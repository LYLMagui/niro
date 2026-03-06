#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SessionStart Hook：加载三层记忆并输出到 stdout。"""

from __future__ import annotations

import gzip
import json
import sys
import tarfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, List

# 修复 Windows 控制台编码问题
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass  # 某些环境不支持

# 上海时区
SHANGHAI_TZ = timezone(timedelta(hours=8))


def get_project_dir() -> Path:
    return Path(__file__).resolve().parents[2]


def load_items(items_file: Path) -> Dict[str, List[Dict[str, Any]]]:
    if not items_file.exists():
        items_file.parent.mkdir(parents=True, exist_ok=True)
        items_file.write_text("[]\n", encoding="utf-8")
        return {}
    try:
        payload = json.loads(items_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        payload = []
    if not isinstance(payload, list):
        payload = []
    grouped: Dict[str, List[Dict[str, Any]]] = {}
    for item in payload:
        if not isinstance(item, dict):
            continue
        if item.get("status") != "active":
            continue
        topic = str(item.get("category") or "未分类")
        grouped.setdefault(topic, []).append(item)
    for topic, records in grouped.items():
        records.sort(key=lambda x: str(x.get("timestamp", "")), reverse=True)
        grouped[topic] = records[:10]
    return grouped


def load_recent_daily_notes(notes_dir: Path, days: int = 3) -> List[Dict[str, str]]:
    notes_dir.mkdir(parents=True, exist_ok=True)
    today = datetime.now()
    allowed = {(today - timedelta(days=i)).strftime("%Y-%m-%d") for i in range(days)}
    notes: List[Dict[str, str]] = []
    for file in sorted(notes_dir.glob("*.md"), reverse=True):
        day = file.stem
        if day not in allowed:
            continue
        content = file.read_text(encoding="utf-8").strip()
        notes.append({"date": day, "content": content})
    return notes


def load_memory_head(memory_file: Path, max_lines: int = 200) -> str:
    if not memory_file.exists():
        memory_file.parent.mkdir(parents=True, exist_ok=True)
        memory_file.write_text("# MEMORY\n", encoding="utf-8")
    lines = memory_file.read_text(encoding="utf-8").splitlines()
    return "\n".join(lines[:max_lines]).strip()


def load_projects(projects_file: Path) -> str:
    """加载 PROJECTS.md 项目跟踪文件"""
    if not projects_file.exists():
        projects_file.parent.mkdir(parents=True, exist_ok=True)
        template = """# Active Projects

## 项目模板

**Goal**: [项目目标]
**Status**: 进行中/已完成/阻塞
**Blockers**: [阻碍因素]
**Next Step**: [下一步行动]

---
"""
        projects_file.write_text(template, encoding="utf-8")
    return projects_file.read_text(encoding="utf-8").strip()


def load_archived_logs(notes_dir: Path) -> Dict[str, str]:
    """加载已归档的月度日志（支持 .md 和 .tar.gz）"""
    archive_dir = notes_dir.parent / "archive"
    archived: Dict[str, str] = {}
    if not archive_dir.exists():
        return archived

    # 先处理 .md 文件
    for file in archive_dir.glob("*.md"):
        month = file.stem
        content = file.read_text(encoding="utf-8").strip()
        if content:
            archived[month] = content[:500]

    # 再处理 .tar.gz 压缩包
    for tar_file in archive_dir.glob("*.tar.gz"):
        month = tar_file.stem.replace(".tar.gz", "")
        try:
            with tarfile.open(tar_file, "r:gz") as tar:
                for member in tar.getmembers():
                    if member.name.endswith(".md"):
                        f = tar.extractfile(member)
                        if f:
                            content = f.read().decode("utf-8").strip()
                            archived[month] = content[:500]
                            break
        except Exception:
            pass

    return archived


def render_output(
    topics: Dict[str, List[Dict[str, Any]]],
    daily_notes: List[Dict[str, str]],
    memory_head: str,
    projects_content: str = "",
    archived_logs: Dict[str, str] = None,
) -> str:
    if archived_logs is None:
        archived_logs = {}
    output: List[str] = []
    output.append("=== 三层记忆加载结果 ===")
    output.append("")
    output.append("【Layer 1: 知识图谱（主题活跃条目）】")
    if not topics:
        output.append("- 暂无活跃条目")
    else:
        for topic, records in topics.items():
            output.append(f"- 主题: {topic}（最近 {len(records)} 条）")
            for rec in records:
                fact = str(rec.get("fact", "")).strip()
                timing = str(rec.get("timing", ""))
                ts = str(rec.get("timestamp", ""))
                output.append(f"  - [{ts}] {fact}（{timing}）")
    output.append("")
    output.append("【Layer 2: 最近3天每日笔记】")
    if not daily_notes:
        output.append("- 暂无最近3天笔记")
    else:
        for note in daily_notes:
            preview = note["content"][:220].replace("\n", " ")
            output.append(f"- {note['date']}: {preview}")
    # 显示归档日志预览
    if archived_logs:
        output.append("")
        output.append("【Layer 2: 归档日志（最近3个月）】")
        for month in sorted(archived_logs.keys(), reverse=True)[:3]:
            preview = archived_logs[month][:100].replace("\n", " ")
            output.append(f"- {month}: {preview}...")
    output.append("")
    output.append("【Layer 3: MEMORY.md 前200行】")
    output.append(memory_head or "(空)")
    output.append("")
    output.append("【PROJECTS: 项目跟踪】")
    if projects_content:
        preview = projects_content[:500].replace("\n", " ")
        output.append(preview + "..." if len(projects_content) > 500 else projects_content)
    else:
        output.append("(空)")
    return "\n".join(output).strip() + "\n"


def main() -> None:
    root = get_project_dir()
    memory_root = root / ".claude" / "memory"
    items_file = memory_root / "areas" / "topics" / "items.json"
    notes_dir = memory_root / "memory"
    memory_file = memory_root / "MEMORY.md"
    projects_file = memory_root / "PROJECTS.md"

    topics = load_items(items_file)
    notes = load_recent_daily_notes(notes_dir, days=3)
    memory_head = load_memory_head(memory_file, max_lines=200)
    projects_content = load_projects(projects_file)
    archived_logs = load_archived_logs(notes_dir)

    print(render_output(topics, notes, memory_head, projects_content, archived_logs), end="")


if __name__ == "__main__":
    main()
