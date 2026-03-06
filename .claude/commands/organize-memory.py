#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""记忆整理命令行工具。"""

from __future__ import annotations

import argparse
import importlib
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

# 添加 hooks 目录到路径


_session_state_module: Optional[Any] = None


def _ensure_session_state_ready() -> None:
    hooks_dir = Path(__file__).resolve().parent.parent / "hooks"
    hooks_path = str(hooks_dir)
    if hooks_path not in sys.path:
        sys.path.insert(0, hooks_path)
    os.chdir(hooks_dir.parent)  # 切换到项目根目录


def _session_state() -> Any:
    global _session_state_module
    _ensure_session_state_ready()
    if _session_state_module is None:
        _session_state_module = importlib.import_module("session_state")
    return _session_state_module


def get_projects_file(project_dir: Path) -> Path:
    return _session_state().get_projects_file(project_dir)


def load_state(project_dir: Path) -> Dict[str, Any]:
    return _session_state().load_state(project_dir)


def mark_project_complete(name: str, lessons: str, project_dir: Path) -> None:
    _session_state().mark_project_complete(name, lessons, project_dir)


def migrate_to_memory(insight: str, project_dir: Path) -> bool:
    return _session_state().migrate_to_memory(insight, project_dir)


def migrate_to_projects(name: str, goal: str, next_step: str, project_dir: Path) -> bool:
    return _session_state().migrate_to_projects(name, goal, next_step, project_dir)


def get_project_dir() -> Path:
    return Path(__file__).resolve().parents[2]


def load_items() -> List[Dict[str, Any]]:
    items_file = get_project_dir() / ".claude" / "memory" / "areas" / "topics" / "items.json"
    if not items_file.exists():
        return []
    try:
        data = json.loads(items_file.read_text(encoding="utf-8"))
        if isinstance(data, list):
            return [x for x in data if isinstance(x, dict)]
    except (json.JSONDecodeError, OSError):
        pass
    return []


def count_tokens_estimate(text: str) -> int:
    """估算 token 数量（中文约 1.5 字符/token，英文约 4 字符/token）"""
    chinese = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
    english = sum(1 for c in text if c.isascii())
    other = len(text) - chinese - english
    return int(chinese / 1.5 + english / 4 + other / 4)


def get_dir_size(path: Path) -> int:
    """计算目录大小（字节）"""
    total = 0
    try:
        for item in path.rglob("*"):
            if item.is_file():
                total += item.stat().st_size
    except OSError:
        pass
    return total


def format_size(size_bytes: int) -> str:
    """格式化文件大小"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024
    return f"{size_bytes:.1f} TB"


def get_diagnosis(items: List[Dict], memory_file: Path, notes: List, active_projects: int) -> List[str]:
    """生成诊断意见"""
    diagnoses = []

    # Layer 3 长期未更新
    if memory_file.exists():
        content = memory_file.read_text(encoding="utf-8")
        lines = content.splitlines()
        # 查找最后更新日期
        for line in reversed(lines):
            if line.strip().startswith("更新时间"):
                try:
                    last_update = datetime.strptime(line.split("：")[-1].split("（")[0], "%Y-%m-%d %H:%M")
                    days_ago = (datetime.now() - last_update).days
                    if days_ago > 14:
                        diagnoses.append(f"Layer 3 长期未更新，已 {days_ago} 天未更新，建议运行 /memory-review")
                except (ValueError, IndexError):
                    pass
                break
        else:
            diagnoses.append("Layer 3 从未更新，建议添加核心规则")

    # 知识条目过多
    active_count = len([i for i in items if i.get("status") == "active"])
    if active_count > 50:
        diagnoses.append(f"Layer 1 活跃条目过多 ({active_count}条)，建议归档老旧条目")

    # 分类统计
    categories: Dict[str, int] = {}
    for item in items:
        cat = item.get("category", "未分类")
        categories[cat] = categories.get(cat, 0) + 1
    if categories:
        max_cat = max(categories.items(), key=lambda x: x[1])
        if max_cat[1] > 50:
            diagnoses.append(f"分类 '{max_cat[0]}' 条目过多 ({max_cat[1]}条)，建议拆分或归档")

    # 每日笔记频率
    if len(notes) > 0:
        diagnoses.append(f"Layer 2 覆盖良好，共 {len(notes)} 篇笔记")

    # Token 消耗
    total_tokens = 0
    for item in items[:10]:
        total_tokens += count_tokens_estimate(str(item.get("fact", "")))
    if memory_file.exists():
        total_tokens += count_tokens_estimate(memory_file.read_text(encoding="utf-8"))
    if total_tokens < 2000:
        diagnoses.append(f"Token 消耗可控，启动约 ~{total_tokens} tokens")
    else:
        diagnoses.append(f"⚠️ Token 消耗较高 (~{total_tokens} tokens)，建议精简")

    return diagnoses


def safe_print(text: str) -> None:
    """安全打印，处理编码问题"""
    try:
        print(text)
    except UnicodeEncodeError:
        # 移除 emoji 后重试
        import re
        text = re.sub(r'[\U00010000-\U0010ffff]', '', text)
        print(text)


def show_status() -> None:
    """显示记忆系统状态 - 增强版（参考老金图片）"""
    import re
    project_dir = get_project_dir()
    memory_root = project_dir / ".claude" / "memory"

    # 边框
    border = "=" * 60

    safe_print(f"\n{border}")
    safe_print("       [MEMORY] 记忆系统状态面板")
    safe_print(f"{border}\n")

    # ========== Layer 1: 知识图谱 ==========
    items = load_items()
    active_items = [i for i in items if i.get("status") == "active"]
    completed_items = [i for i in items if i.get("status") == "completed"]
    superseded_items = [i for i in items if i.get("status") == "superseded"]

    safe_print("+-----------------------------------------------------------+")
    safe_print("| Layer 1: 知识图谱 (Knowledge Graph)                       |")
    safe_print("+-----------------------------------------------------------+")
    safe_print(f"|   总条目: {len(items):<5}  活跃: {len(active_items):<5}  已废弃: {len(superseded_items):<5}      |")

    # 按分类统计
    categories: Dict[str, int] = {}
    for item in items:
        cat = item.get("category", "未分类")
        categories[cat] = categories.get(cat, 0) + 1

    if categories:
        safe_print("+--- 分类明细 ------------------------------------------------+")
        for cat, count in sorted(categories.items(), key=lambda x: x[1], reverse=True):
            status_info = ""
            active_count = sum(1 for i in items if i.get("category") == cat and i.get("status") == "active")
            superseded_count = sum(1 for i in items if i.get("category") == cat and i.get("status") == "superseded")
            status_info = f" ({active_count}活跃"
            if superseded_count > 0:
                status_info += f", {superseded_count}废弃"
            status_info += ")"
            safe_print(f"|   {cat:<20}: {count:<5}{status_info:<25}     |")
    safe_print("+-----------------------------------------------------------+")

    # 显示最近活跃条目
    if active_items:
        safe_print("\n[最近活跃条目]")
        for i, item in enumerate(sorted(active_items, key=lambda x: x.get("timestamp", ""), reverse=True)[:5]):
            fact = item.get("fact", "")[:40]
            cat = item.get("category", "")
            ts = item.get("timestamp", "")[:10]
            safe_print(f"   {i+1}. [{cat}] {fact}... ({ts})")

    # ========== Layer 2: 每日笔记 ==========
    safe_print("\n+-----------------------------------------------------------+")
    safe_print("| Layer 2: 每日笔记 (Daily Notes)                          |")
    safe_print("+-----------------------------------------------------------+")

    notes_dir = memory_root / "memory"
    notes = list(notes_dir.glob("*.md")) if notes_dir.exists() else []
    safe_print(f"|   笔记文件: {len(notes):<5}                                       |")

    # 最近笔记
    if notes:
        recent_notes = sorted(notes, key=lambda x: x.stat().st_mtime, reverse=True)[:3]
        safe_print("+--- 最近笔记 ----------------------------------------------+")
        for note in recent_notes:
            date = note.stem
            lines = len(note.read_text(encoding="utf-8").splitlines())
            tokens = count_tokens_estimate(note.read_text(encoding="utf-8"))
            safe_print(f"|   {date}: {lines:>3} 行, ~{tokens:>4} tokens                              |")

    # 归档
    archive_dir = memory_root / "archive"
    archives = list(archive_dir.glob("*")) if archive_dir.exists() else []
    safe_print(f"|   归档文件: {len(archives):<5}                                       |")

    safe_print("+-----------------------------------------------------------+")

    # ========== Layer 3: MEMORY.md ==========
    safe_print("\n+-----------------------------------------------------------+")
    safe_print("| Layer 3: MEMORY.md (长期记忆)                           |")
    safe_print("+-----------------------------------------------------------+")

    memory_file = memory_root / "MEMORY.md"
    if memory_file.exists():
        memory_content = memory_file.read_text(encoding="utf-8")
        memory_lines = len(memory_content.splitlines())
        memory_tokens = count_tokens_estimate(memory_content)
        safe_print(f"|   行数: {memory_lines:<5}  tokens: ~{memory_tokens:<5}                         |")

        # 章节统计
        sections = [l for l in memory_content.splitlines() if l.startswith("## ")]
        safe_print(f"|   章节: {len(sections):<5}                                              |")

        # 最后更新时间
        lines = memory_content.splitlines()
        last_update_info = "未知"
        for line in reversed(lines):
            if line.strip().startswith("更新时间"):
                last_update_info = line.split("：")[-1].split("（")[0]
                break
        safe_print(f"|   最后更新: {last_update_info:<45}|")
    else:
        safe_print("|   (文件不存在)                                           |")

    safe_print("+-----------------------------------------------------------+")

    # ========== PROJECTS ==========
    safe_print("\n+-----------------------------------------------------------+")
    safe_print("| PROJECTS.md (项目跟踪)                                   |")
    safe_print("+-----------------------------------------------------------+")

    projects_file = memory_root / "PROJECTS.md"
    active_projects = 0
    if projects_file.exists():
        projects_content = projects_file.read_text(encoding="utf-8")
        active_projects = projects_content.count("**Status**: 进行中")
        completed_projects = projects_content.count("**Status**: 已完成")
        safe_print(f"|   进行中: {active_projects:<5}  已完成: {completed_projects:<5}                        |")

        # 提取当前项目
        project_pattern = r"## (.+?)\n\*\*Goal\*\*: (.+?)\n\*\*Status\*\*: (.+?)\n"
        matches = re.findall(project_pattern, projects_content, re.DOTALL)
        if matches:
            safe_print("+--- 当前项目 ----------------------------------------------+")
            for name, goal, status in matches[:3]:
                goal_preview = goal.strip()[:35]
                safe_print(f"|   - {name.strip()[:18]:<18} [{status.strip():<6}] {goal_preview:<35}|")
    else:
        safe_print("|   (文件不存在)                                           |")

    safe_print("+-----------------------------------------------------------+")

    # ========== 系统指标 ==========
    safe_print("\n+-----------------------------------------------------------+")
    safe_print("| 系统指标 (System Metrics)                                 |")
    safe_print("+-----------------------------------------------------------+")

    # 磁盘占用
    total_size = get_dir_size(memory_root)
    safe_print(f"|   磁盘占用: {format_size(total_size):<47}|")

    # Token 估算
    total_tokens = 0
    for item in active_items[:10]:
        total_tokens += count_tokens_estimate(str(item.get("fact", "")))
    if memory_file.exists():
        total_tokens += count_tokens_estimate(memory_file.read_text(encoding="utf-8"))
    safe_print(f"|   启动Token估算: ~{total_tokens:<5} tokens (加载10条活跃条目)           |")

    # 废弃比例
    superseded_ratio = len(superseded_items) / max(len(items), 1) * 100
    health_status = "健康" if superseded_ratio < 5 else "警告"
    safe_print(f"|   废弃比例: {superseded_ratio:.1f}% (状态: {health_status})                            |")

    safe_print("+-----------------------------------------------------------+")

    # ========== 诊断意见 ==========
    diagnoses = get_diagnosis(items, memory_file, notes, active_projects)
    if diagnoses:
        safe_print("\n+-----------------------------------------------------------+")
        safe_print("| [诊断意见]                                                 |")
        safe_print("+-----------------------------------------------------------+")
        for diag in diagnoses:
            safe_print(f"|   {diag:<57}|")
        safe_print("+-----------------------------------------------------------+")

    # ========== 会话状态 ==========
    safe_print("\n+-----------------------------------------------------------+")
    safe_print("| 当前会话 (Session)                                       |")
    safe_print("+-----------------------------------------------------------+")

    state = load_state(project_dir)
    current_task = state.get("current_task", "")
    modified_files = state.get("modified_files", [])

    if current_task:
        task_preview = current_task[:45]
        safe_print(f"|   任务: {task_preview:<47}|")
    else:
        safe_print("|   任务: (无)                                             |")

    safe_print(f"|   修改文件: {len(modified_files):<5}                                        |")

    if modified_files:
        for f in modified_files[:3]:
            f_preview = str(f)[-40:]
            safe_print(f"|   - ...{f_preview:<44}|")
        if len(modified_files) > 3:
            safe_print(f"|   ... 共 {len(modified_files)} 个文件                                  |")

    safe_print("+-----------------------------------------------------------+")

    safe_print(f"\n{border}")
    safe_print("  提示: /organize-memory -i <洞察> 添加记忆 | /organize-memory -s 刷新")
    safe_print(f"{border}\n")


def do_insight(insight: str = "") -> None:
    """提取洞察到 MEMORY.md"""
    project_dir = get_project_dir()
    if not insight:
        # 尝试从会话状态获取洞察
        state = load_state(project_dir)
        insight = state.get("current_task", "")
        if not insight:
            print("错误: 请提供洞察内容")
            return

    migrate_to_memory(insight, project_dir)
    print(f"已添加洞察到 MEMORY.md: {insight[:50]}...")


def do_project(name: str, goal: str, next_step: str) -> None:
    """添加任务到 PROJECTS.md"""
    project_dir = get_project_dir()
    success = migrate_to_projects(name, goal, next_step, project_dir)
    if success:
        print(f"已添加项目到 PROJECTS.md: {name}")
    else:
        print("错误: 无法添加项目")


def do_complete(name: str, lessons: str = "") -> None:
    """标记任务完成"""
    project_dir = get_project_dir()
    mark_project_complete(name, lessons, project_dir)
    print(f"已标记项目完成: {name}")
    if lessons:
        print(f"已迁移经验到 MEMORY.md")


def load_notes_for_review(notes_dir: Path) -> List[Dict]:
    """加载笔记用于回顾分析"""
    notes = []
    if not notes_dir.exists():
        return notes
    for note_file in notes_dir.glob("*.md"):
        try:
            date = datetime.strptime(note_file.stem, "%Y-%m-%d")
        except ValueError:
            continue
        content = note_file.read_text(encoding="utf-8")
        notes.append({
            "date": date,
            "file": note_file,
            "content": content,
            "lines": len(content.splitlines()),
        })
    return sorted(notes, key=lambda x: x["date"])


def analyze_high_frequency_patterns(items: List[Dict], min_count: int = 3) -> List[Dict]:
    """分析高频模式"""
    # 按 category 统计
    cat_counts: Dict[str, int] = {}
    for item in items:
        cat = item.get("category", "未分类")
        cat_counts[cat] = cat_counts.get(cat, 0) + 1

    # 按 timing 统计
    timing_counts: Dict[str, int] = {}
    for item in items:
        timing = item.get("timing", "未知")
        timing_counts[timing] = timing_counts.get(timing, 0) + 1

    # 提取关键词（简化版：从 fact 中提取）
    keywords: Dict[str, int] = {}
    keywords_to_check = ["Python", "Java", "Bug", "API", "数据库", "测试", "优化", "重构"]
    for item in items:
        fact = item.get("fact", "")
        for kw in keywords_to_check:
            if kw.lower() in fact.lower():
                keywords[kw] = keywords.get(kw, 0) + 1

    results = []
    for cat, count in cat_counts.items():
        if count >= min_count:
            results.append({"type": "category", "key": cat, "count": count})

    for timing, count in timing_counts.items():
        if count >= min_count:
            results.append({"type": "timing", "key": timing, "count": count})

    for kw, count in keywords.items():
        if count >= min_count:
            results.append({"type": "keyword", "key": kw, "count": count})

    return sorted(results, key=lambda x: x["count"], reverse=True)


def analyze_trends(items: List[Dict], days: int = 14) -> Dict:
    """分析趋势（近期 vs 早期）"""
    from datetime import timedelta
    now = datetime.now()
    cutoff = now.replace(hour=0, minute=0, second=0)
    recent_cutoff = cutoff - timedelta(days=days)

    recent_items = []
    older_items = []

    for item in items:
        ts_str = item.get("timestamp", "")
        if not ts_str:
            continue
        try:
            ts = datetime.fromisoformat(ts_str.replace("Z", "+00:00"))
            ts = ts.replace(tzinfo=None)  # 移除时区信息以便比较
            if ts >= recent_cutoff:
                recent_items.append(item)
            else:
                older_items.append(item)
        except (TypeError, ValueError):
            continue

    return {
        "recent_count": len(recent_items),
        "older_count": len(older_items),
        "recent_items": recent_items,
        "older_items": older_items,
    }


def find_decay_patterns(items: List[Dict], threshold: int = 3) -> List[Dict]:
    """找出衰减模式（曾经活跃现在消失的）"""
    # 按月统计
    monthly_counts: Dict[str, int] = {}
    for item in items:
        ts_str = item.get("timestamp", "")
        if not ts_str:
            continue
        try:
            ts = datetime.fromisoformat(ts_str.replace("Z", "+00:00"))
            month_key = ts.strftime("%Y-%m")
            monthly_counts[month_key] = monthly_counts.get(month_key, 0) + 1
        except (TypeError, ValueError):
            continue

    # 找出最近减少的分类
    trends = analyze_trends(items)
    decay_patterns = []

    # 检查 timing 衰减
    recent_timing: Dict[str, int] = {}
    older_timing: Dict[str, int] = {}
    for item in trends["recent_items"]:
        t = item.get("timing", "未知")
        recent_timing[t] = recent_timing.get(t, 0) + 1
    for item in trends["older_items"]:
        t = item.get("timing", "未知")
        older_timing[t] = older_timing.get(t, 0) + 1

    for timing, older_count in older_timing.items():
        recent_count = recent_timing.get(timing, 0)
        if older_count > threshold and recent_count < threshold:
            decay_patterns.append({
                "type": "timing_decay",
                "key": timing,
                "older_count": older_count,
                "recent_count": recent_count,
            })

    return decay_patterns


def find_data_quality_issues(items: List[Dict]) -> List[Dict]:
    """检测数据质量问题"""
    issues = []

    # 检查空值
    for i, item in enumerate(items):
        if not item.get("fact"):
            issues.append({"type": "empty_fact", "index": i, "id": item.get("id", "")})
        if not item.get("category"):
            issues.append({"type": "empty_category", "index": i, "id": item.get("id", "")})

    # 检查重复事实
    fact_counts: Dict[str, int] = {}
    for item in items:
        fact = item.get("fact", "")
        if fact:
            # 简化：只统计完全相同的事实
            fact_counts[fact] = fact_counts.get(fact, 0) + 1

    duplicate_facts = [f for f, c in fact_counts.items() if c > 1]
    if len(duplicate_facts) > 0:
        issues.append({"type": "duplicate_facts", "count": len(duplicate_facts), "examples": duplicate_facts[:3]})

    return issues


def do_review() -> None:
    """Memory Review - 记忆回顾分析"""
    project_dir = get_project_dir()
    memory_root = project_dir / ".claude" / "memory"
    items = load_items()
    notes_dir = memory_root / "memory"
    notes = load_notes_for_review(notes_dir)
    memory_file = memory_root / "MEMORY.md"

    border = "=" * 60
    safe_print(f"\n{border}")
    safe_print("       [MEMORY REVIEW] 记忆回顾报告")
    safe_print(f"{border}\n")

    # 统计概览
    active_items = [i for i in items if i.get("status") == "active"]
    superseded_items = [i for i in items if i.get("status") == "superseded"]

    safe_print("+-----------------------------------------------------------+")
    safe_print("| 统计概览                                                   |")
    safe_print("+-----------------------------------------------------------+")
    safe_print(f"|   Layer 1 记录: {len(items):<5} (活跃: {len(active_items)}, 废弃: {len(superseded_items)})       |")
    safe_print(f"|   Layer 2 笔记: {len(notes):<5}                                       |")
    if memory_file.exists():
        memory_content = memory_file.read_text(encoding="utf-8")
        layer3_count = len([l for l in memory_content.splitlines() if l.startswith("- ")])
        safe_print(f"|   Layer 3 规则: {layer3_count:<5}                                       |")
    safe_print("+-----------------------------------------------------------+")

    # 高频模式
    high_freq = analyze_high_frequency_patterns(active_items, min_count=2)
    if high_freq:
        safe_print("\n+-----------------------------------------------------------+")
        safe_print("| 高频模式 (出现 2 次以上)                                    |")
        safe_print("+-----------------------------------------------------------+")
        for pf in high_freq[:8]:
            safe_print(f"|   [{pf['type']:<8}] {pf['key']:<15}: {pf['count']:<5}                     |")
        safe_print("+-----------------------------------------------------------+")

    # 趋势分析
    trends = analyze_trends(active_items)
    if trends["recent_count"] > 0 or trends["older_count"] > 0:
        safe_print("\n+-----------------------------------------------------------+")
        safe_print("| 趋势分析 (近 14 天 vs 更早)                                |")
        safe_print("+-----------------------------------------------------------+")
        growth = 0
        if trends["older_count"] > 0:
            growth = ((trends["recent_count"] - trends["older_count"]) / trends["older_count"]) * 100
        trend_str = "增长" if growth > 0 else "下降" if growth < 0 else "持平"
        safe_print(f"|   近期: {trends['recent_count']:<5}  早期: {trends['older_count']:<5} ({trend_str}: {abs(growth):.0f}%)              |")
        safe_print("+-----------------------------------------------------------+")

    # 衰减模式
    decay = find_decay_patterns(active_items)
    if decay:
        safe_print("\n+-----------------------------------------------------------+")
        safe_print("| 衰减模式 (曾经活跃，现在减少)                                |")
        safe_print("+-----------------------------------------------------------+")
        for d in decay[:5]:
            safe_print(f"|   {d['key']:<15}: 早期 {d['older_count']} -> 近期 {d['recent_count']}                 |")
        safe_print("+-----------------------------------------------------------+")

    # 数据质量
    issues = find_data_quality_issues(items)
    if issues:
        safe_print("\n+-----------------------------------------------------------+")
        safe_print("| 数据质量问题                                                |")
        safe_print("+-----------------------------------------------------------+")
        empty_fact = len([i for i in issues if i["type"] == "empty_fact"])
        empty_cat = len([i for i in issues if i["type"] == "empty_category"])
        dup_facts = len([i for i in issues if i["type"] == "duplicate_facts"])
        if empty_fact > 0:
            safe_print(f"|   空事实: {empty_fact:<5}                                           |")
        if empty_cat > 0:
            safe_print(f"|   空分类: {empty_cat:<5}                                           |")
        if dup_facts > 0:
            safe_print(f"|   重复事实: {dup_facts:<5}                                         |")
        safe_print("+-----------------------------------------------------------+")

    # 建议规则
    suggestions = []

    # 基于高频模式建议
    if high_freq:
        top_cat = high_freq[0]
        if top_cat["type"] == "category":
            suggestions.append(f"- 建议将 '{top_cat['key']}' 作为核心分类，持续关注")

    # 基于趋势建议
    if trends["recent_count"] > trends["older_count"] * 1.5:
        suggestions.append("- 近期活跃度明显提升，建议更新产出预期")

    # 基于衰减建议
    if decay:
        suggestions.append(f"- 发现 {len(decay)} 个衰减模式，建议标记为备选")

    # 基于数据质量建议
    if issues:
        suggestions.append("- 建议清理数据质量问题，提升知识库质量")

    if suggestions:
        safe_print("\n+-----------------------------------------------------------+")
        safe_print("| 建议规则 (可写入 MEMORY.md)                                |")
        safe_print("+-----------------------------------------------------------+")
        for s in suggestions:
            safe_print(f"| {s:<61}|")
        safe_print("+-----------------------------------------------------------+")

    safe_print(f"\n{border}")
    safe_print("  提示: 使用 /organize-memory -i <规则> 将建议写入长期记忆")
    safe_print(f"{border}\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="记忆整理命令")
    parser.add_argument("-s", "--status", action="store_true", help="查看记忆状态")
    parser.add_argument("-r", "--review", action="store_true", help="记忆回顾分析")
    parser.add_argument("-i", "--insight", nargs="?", const="", help="提取洞察到 MEMORY.md")
    parser.add_argument("-p", "--project", nargs=3, metavar=("NAME", "GOAL", "NEXT"),
                       help="添加任务到 PROJECTS.md")
    parser.add_argument("-c", "--complete", nargs="+", metavar=("NAME", "LESSONS"),
                       help="标记任务完成")
    parser.add_argument("-a", "--all", action="store_true", help="完整整理")

    args = parser.parse_args()

    if args.status:
        show_status()
    elif args.review:
        do_review()
    elif args.insight is not None:
        do_insight(args.insight if args.insight else "")
    elif args.project:
        name, goal, next_step = args.project
        do_project(name, goal, next_step)
    elif args.complete:
        name = args.complete[0]
        lessons = " ".join(args.complete[1:]) if len(args.complete) > 1 else ""
        do_complete(name, lessons)
    elif args.all:
        show_status()
        print("\n建议使用其他选项完成整理")
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
