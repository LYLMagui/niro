#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""会话状态管理模块。"""

from __future__ import annotations

import json
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Any, Dict, List

# 上海时区
SHANGHAI_TZ = timezone(timedelta(hours=8))


def utc_now_iso() -> str:
    """返回上海时区 ISO8601 时间戳。"""
    return datetime.now(SHANGHAI_TZ).isoformat()


def _default_state() -> Dict[str, Any]:
    return {
        "current_task": "",
        "task_history": [],
        "modified_files": [],
        "last_updated": utc_now_iso(),
        "last_compact": {},
    }


def get_project_dir() -> Path:
    """推断项目根目录。"""
    return Path(__file__).resolve().parents[2]


def get_state_file(project_dir: Path | None = None) -> Path:
    """返回状态文件路径。"""
    root = project_dir or get_project_dir()
    return root / ".claude" / "hooks" / "session_state.json"


def ensure_state_file(project_dir: Path | None = None) -> Path:
    """确保状态文件存在。"""
    state_file = get_state_file(project_dir)
    state_file.parent.mkdir(parents=True, exist_ok=True)
    if not state_file.exists():
        save_state(_default_state(), project_dir)
    return state_file


def load_state(project_dir: Path | None = None) -> Dict[str, Any]:
    """读取状态。"""
    state_file = ensure_state_file(project_dir)
    try:
        data = json.loads(state_file.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            return _default_state()
        return {**_default_state(), **data}
    except (OSError, json.JSONDecodeError):
        return _default_state()


def save_state(state: Dict[str, Any], project_dir: Path | None = None) -> None:
    """保存状态。"""
    state_file = get_state_file(project_dir)
    state_file.parent.mkdir(parents=True, exist_ok=True)
    snapshot = {**_default_state(), **state}
    snapshot["last_updated"] = utc_now_iso()
    state_file.write_text(
        json.dumps(snapshot, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def add_modified_file(file_path: str, project_dir: Path | None = None) -> None:
    """记录修改文件。"""
    if not file_path:
        return
    state = load_state(project_dir)
    files: List[str] = list(state.get("modified_files", []))
    normalized = str(Path(file_path))
    if normalized not in files:
        files.append(normalized)
        state["modified_files"] = files
        save_state(state, project_dir)


def update_current_task(task: str, project_dir: Path | None = None) -> None:
    """更新当前任务，并保留历史。"""
    if not task:
        return
    state = load_state(project_dir)
    previous = state.get("current_task", "")
    if previous and previous != task:
        history = list(state.get("task_history", []))
        history.append(
            {
                "task": previous,
                "closed_at": utc_now_iso(),
            }
        )
        state["task_history"] = history[-50:]
    state["current_task"] = task
    save_state(state, project_dir)


# ========== 项目管理功能 ==========

def get_projects_file(project_dir: Path | None = None) -> Path:
    """返回 PROJECTS.md 文件路径。"""
    root = project_dir or get_project_dir()
    return root / ".claude" / "memory" / "PROJECTS.md"


def migrate_to_memory(insight: str, project_dir: Path | None = None) -> None:
    """将洞察迁移到 MEMORY.md。"""
    memory_file = project_dir / ".claude" / "memory" / "MEMORY.md"
    if not memory_file.exists():
        return

    content = memory_file.read_text(encoding="utf-8")
    new_section = f"\n\n## {datetime.now().strftime('%Y-%m-%d')} 洞察\n\n- {insight}"
    content += new_section

    memory_file.write_text(content, encoding="utf-8")


def migrate_to_projects(project_name: str, goal: str, next_step: str, project_dir: Path | None = None) -> bool:
    """将任务迁移到 PROJECTS.md 作为新项目。"""
    projects_file = get_projects_file(project_dir)
    if not projects_file.exists():
        return False

    content = projects_file.read_text(encoding="utf-8")

    new_project = f"""

## {project_name}

**Goal**: {goal}
**Status**: 进行中
**Blockers**: 无
**Next Step**: {next_step}
**Last Updated**: {datetime.now().strftime('%Y-%m-%d %H:%M')}
"""

    content += new_project
    projects_file.write_text(content, encoding="utf-8")
    return True


def mark_project_complete(project_name: str, lessons_learned: str, project_dir: Path | None = None) -> None:
    """标记项目完成，并迁移经验到 MEMORY.md。"""
    projects_file = get_projects_file(project_dir)
    if not projects_file.exists():
        return

    content = projects_file.read_text(encoding="utf-8")

    # 更新项目状态
    content = content.replace(
        f"## {project_name}\n\n**Status**: 进行中",
        f"## {project_name}\n\n**Status**: 已完成"
    )

    projects_file.write_text(content, encoding="utf-8")

    # 迁移经验到 MEMORY.md
    if lessons_learned:
        migrate_to_memory(lessons_learned, project_dir)
