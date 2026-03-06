#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PreCompact Hook：压缩前保存会话状态并归档旧日志。"""

from __future__ import annotations

import gzip
import json
import subprocess
import sys
import tarfile
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Any, Dict, List

from session_state import load_state, save_state


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_project_dir() -> Path:
    return Path(__file__).resolve().parents[2]


def read_hook_input() -> Dict[str, Any]:
    raw = sys.stdin.read()
    try:
        return json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        return {}


def get_git_modified_files(project_dir: Path) -> List[str]:
    try:
        result = subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=str(project_dir),
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError:
        return []
    files: List[str] = []
    for line in result.stdout.splitlines():
        if len(line) >= 4:
            files.append(line[3:].strip())
    return files


def archive_old_daily_notes(notes_dir: Path, days: int = 30) -> List[str]:
    """归档超过指定天数的每日笔记到月度文件，并压缩"""
    if not notes_dir.exists():
        return []

    archive_dir = notes_dir.parent / "archive"
    archive_dir.mkdir(parents=True, exist_ok=True)

    cutoff_date = datetime.now() - timedelta(days=days)
    archived: List[str] = []

    # 按月份分组
    monthly_notes: Dict[str, List[Path]] = {}

    for file in notes_dir.glob("*.md"):
        try:
            file_date = datetime.strptime(file.stem, "%Y-%m-%d")
        except ValueError:
            continue

        if file_date < cutoff_date:
            month_key = file.strftime("%Y-%m")
            monthly_notes.setdefault(month_key, []).append(file)

    # 合并到月度归档文件
    for month_key, files in monthly_notes.items():
        archive_file = archive_dir / f"{month_key}.md"
        existing_content = ""
        if archive_file.exists():
            existing_content = archive_file.read_text(encoding="utf-8")

        new_content = existing_content
        for file in sorted(files):
            content = file.read_text(encoding="utf-8")
            new_content += f"\n\n---\n\n{content}"
            archived.append(file.name)
            file.unlink()  # 删除原始文件

        archive_file.write_text(new_content, encoding="utf-8")

        # 创建 tar.gz 压缩包
        try:
            tar_path = archive_dir / f"{month_key}.tar.gz"
            with tarfile.open(tar_path, "w:gz") as tar:
                tar.add(archive_file, arcname=f"{month_key}.md")
            # 压缩成功后删除未压缩文件
            archive_file.unlink()
        except Exception:
            pass  # 压缩失败保留原文

    return archived


def main() -> None:
    payload = read_hook_input()
    project_dir = get_project_dir()
    state = load_state(project_dir)

    tracked_files = set(state.get("modified_files", []))
    tracked_files.update(get_git_modified_files(project_dir))

    state["modified_files"] = sorted(f for f in tracked_files if f)
    state["last_compact"] = {
        "timestamp": utc_now_iso(),
        "trigger": payload.get("trigger", ""),
        "custom_instructions": payload.get("custom_instructions", ""),
    }

    save_state(state, project_dir)

    # 归档超过30天的每日笔记
    memory_root = project_dir / ".claude" / "memory"
    notes_dir = memory_root / "memory"
    archived = archive_old_daily_notes(notes_dir, days=30)

    if archived:
        print(f"pre_compact: 会话状态已保存，已归档 {len(archived)} 个旧日志文件。")
    else:
        print("pre_compact: 会话状态已保存。")


if __name__ == "__main__":
    main()
