#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PostToolUse Hook：提取工具使用中的关键信息并写入知识条目。"""

from __future__ import annotations

import json
import re
import sys
import uuid
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Any, Dict, List

# 修复 Windows 控制台编码问题
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# 上海时区
SHANGHAI_TZ = timezone(timedelta(hours=8))

from session_state import add_modified_file, load_state, update_current_task


def get_project_dir() -> Path:
    return Path(__file__).resolve().parents[2]


def utc_now_iso() -> str:
    """返回上海时区时间戳。"""
    return datetime.now(SHANGHAI_TZ).isoformat()


def read_hook_input() -> Dict[str, Any]:
    raw = sys.stdin.read()
    try:
        return json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        return {}


def ensure_items_file(project_dir: Path) -> Path:
    items_file = project_dir / ".claude" / "memory" / "areas" / "topics" / "items.json"
    items_file.parent.mkdir(parents=True, exist_ok=True)
    if not items_file.exists():
        items_file.write_text("[]\n", encoding="utf-8")
    return items_file


def load_items(items_file: Path) -> List[Dict[str, Any]]:
    try:
        data = json.loads(items_file.read_text(encoding="utf-8"))
        if isinstance(data, list):
            return [x for x in data if isinstance(x, dict)]
    except json.JSONDecodeError:
        pass
    return []


def save_items(items_file: Path, items: List[Dict[str, Any]]) -> None:
    items_file.write_text(
        json.dumps(items, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def get_latest_user_request(transcript_path: str) -> str:
    if not transcript_path:
        return ""
    path = Path(transcript_path)
    if not path.exists():
        return ""
    latest = ""
    try:
        raw = path.read_bytes()
    except OSError:
        return ""

    content = ""
    for enc in ("utf-8-sig", "utf-8", "utf-16"):
        try:
            content = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    if not content:
        return ""

    try:
        for line in content.splitlines():
            if not line.strip():
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event.get("type") != "user":
                continue
            message = event.get("message", {})
            content = message.get("content")
            text = flatten_content(content)
            if text:
                latest = text.strip()
    except OSError:
        return ""
    return latest


def flatten_content(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: List[str] = []
        for block in content:
            if isinstance(block, dict):
                text = block.get("text")
                if isinstance(text, str):
                    parts.append(text)
        return "\n".join(parts)
    if isinstance(content, dict):
        text = content.get("text")
        if isinstance(text, str):
            return text
    return ""


def is_new_development_task(user_text: str, current_task: str) -> bool:
    if not user_text:
        return False
    text = user_text.lower()
    patterns = [
        "创建",
        "新增",
        "实现",
        "开发",
        "修复",
        "重构",
        "build",
        "implement",
        "add",
        "fix",
        "refactor",
        "feature",
    ]
    hit = any(keyword in text for keyword in patterns)
    if not hit:
        return False
    return user_text.strip() != (current_task or "").strip()


def extract_fact(tool_name: str, tool_input: Dict[str, Any], tool_response: Dict[str, Any]) -> str:
    if tool_name in {"Write", "Edit", "MultiEdit"}:
        file_path = tool_input.get("file_path", "")
        return f"对文件进行了写入/编辑：{file_path}"
    if tool_name == "Bash":
        cmd = str(tool_input.get("command", "")).strip()
        return f"执行了命令：{cmd[:180]}"
    if tool_name == "Read":
        file_path = tool_input.get("file_path", "")
        return f"读取了文件：{file_path}"
    summary = tool_response.get("summary") if isinstance(tool_response, dict) else ""
    if summary:
        return f"{tool_name} 执行摘要：{summary}"
    return f"执行了工具：{tool_name}"


def classify_category(tool_name: str, tool_input: Dict[str, Any]) -> str:
    if tool_name in {"Write", "Edit", "MultiEdit"}:
        file_path = str(tool_input.get("file_path", "")).lower()
        if file_path.endswith(".py"):
            return "Python开发"
        if file_path.endswith(".json"):
            return "配置管理"
        if file_path.endswith(".md"):
            return "文档维护"
        return "代码变更"
    if tool_name == "Bash":
        cmd = str(tool_input.get("command", "")).lower()
        if "test" in cmd or "pytest" in cmd or "mvn test" in cmd:
            return "测试验证"
        return "命令执行"
    return "通用操作"


def extract_timing(user_text: str) -> str:
    if not user_text:
        return "当前会话"
    match = re.search(r"(今天|明天|本周|本月|today|tomorrow|this week|this month)", user_text, re.I)
    return match.group(1) if match else "当前会话"


def determine_status(tool_name: str, tool_input: Dict[str, Any], tool_response: Dict[str, Any]) -> str:
    if tool_name == "Bash":
        cmd = str(tool_input.get("command", "")).lower()
        if "test" in cmd and isinstance(tool_response, dict) and tool_response.get("success") is True:
            return "completed"
    return "active"


def find_duplicate_item(items: List[Dict[str, Any]], fact: str, category: str, hours: int = 24) -> int | None:
    """查找重复条目，如果存在则返回索引。"""
    if not items:
        return None

    now = datetime.now(SHANGHAI_TZ)
    cutoff = now - timedelta(hours=hours)

    for i, item in enumerate(items):
        if item.get("status") != "active":
            continue
        # 相同分类和相似事实
        if item.get("category") == category:
            item_fact = str(item.get("fact", ""))
            # 简化匹配：检查事实是否包含对方
            if fact in item_fact or item_fact in fact:
                # 检查时间是否在24小时内
                ts = item.get("timestamp", "")
                if ts:
                    try:
                        # 兼容处理：支持 +00:00 和 +08:00
                        ts_fixed = ts.replace("Z", "+08:00")
                        item_time = datetime.fromisoformat(ts_fixed)
                        if item_time > cutoff:
                            return i
                    except (ValueError, TypeError):
                        pass
    return None


def deduplicate_and_save(
    items_file: Path,
    items: List[Dict[str, Any]],
    new_record: Dict[str, Any],
) -> str:
    """去重后保存，返回操作类型：'updated' 或 'added'"""
    fact = new_record.get("fact", "")
    category = new_record.get("category", "")

    dup_idx = find_duplicate_item(items, fact, category, hours=24)
    if dup_idx is not None:
        # 更新现有记录的时间戳
        items[dup_idx]["timestamp"] = new_record["timestamp"]
        save_items(items_file, items)
        return "updated"

    # 无重复，添加新记录
    items.append(new_record)
    save_items(items_file, items)
    return "added"


def maybe_track_modified_file(tool_name: str, tool_input: Dict[str, Any], project_dir: Path) -> None:
    if tool_name not in {"Write", "Edit", "MultiEdit"}:
        return
    path = str(tool_input.get("file_path", "")).strip()
    if path:
        add_modified_file(path, project_dir)


def main() -> None:
    payload = read_hook_input()
    project_dir = get_project_dir()
    state = load_state(project_dir)

    tool_name = str(payload.get("tool_name", ""))
    tool_input = payload.get("tool_input", {}) if isinstance(payload.get("tool_input"), dict) else {}
    tool_response = payload.get("tool_response", {}) if isinstance(payload.get("tool_response"), dict) else {}
    transcript_path = str(payload.get("transcript_path", ""))

    latest_user = get_latest_user_request(transcript_path)
    if is_new_development_task(latest_user, str(state.get("current_task", ""))):
        update_current_task(latest_user, project_dir)

    state = load_state(project_dir)
    if not state.get("current_task"):
        print("memory_extractor: 未识别到开发任务，跳过提取。")
        return

    items_file = ensure_items_file(project_dir)
    items = load_items(items_file)

    record = {
        "id": str(uuid.uuid4()),
        "fact": extract_fact(tool_name, tool_input, tool_response),
        "category": classify_category(tool_name, tool_input),
        "timing": extract_timing(latest_user),
        "timestamp": utc_now_iso(),
        "status": determine_status(tool_name, tool_input, tool_response),
    }

    # 使用去重策略保存
    action = deduplicate_and_save(items_file, items, record)
    maybe_track_modified_file(tool_name, tool_input, project_dir)

    if action == "updated":
        print(f"memory_extractor: 已更新已有记忆条目（去重）")
    else:
        print(f"memory_extractor: 已记录新记忆条目 {record['id']}")


if __name__ == "__main__":
    main()
