#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
三层记忆系统 - SessionStart Hook
功能：对话启动时加载 L1+L2+L3 到上下文
"""
import json
import os
import sys
import io
from datetime import datetime, timedelta

# 设置 UTF-8 输出
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 路径配置（相对于项目根目录）
MEMORY_DIR = ".claude/memory"
L1_FILE = f"{MEMORY_DIR}/items.json"
L2_DIR = f"{MEMORY_DIR}/daily_logs"
L3_FILE = f"{MEMORY_DIR}/MEMORY.md"
CLAUDE_MD = ".claude/CLAUDE.md"  # 同时加载项目原有配置


def load_layer1():
    """加载 L1: 结构化知识（最近10条活跃）"""
    if not os.path.exists(L1_FILE):
        return []

    try:
        with open(L1_FILE, 'r', encoding='utf-8') as f:
            items = json.load(f)

        # 只加载 active 状态且不超过 10 条
        active_items = [
            item for item in items
            if item.get('status') == 'active'
        ][-10:]
        return active_items
    except (json.JSONDecodeError, IOError):
        return []


def load_layer2():
    """加载 L2: 每日日志（最近3天）"""
    logs = []
    today = datetime.now()

    for i in range(3):
        date = (today - timedelta(days=i)).strftime("%Y-%m-%d")
        log_file = f"{L2_DIR}/{date}.md"
        if os.path.exists(log_file):
            try:
                with open(log_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    # 只取前 50 行，防止 Token 爆炸
                    lines = content.split('\n')[:50]
                    logs.append(f"### {date}\n" + '\n'.join(lines))
            except IOError:
                continue

    return logs


def load_layer3():
    """加载 L3: 手动规则"""
    layers = []

    # 优先加载 MEMORY.md
    if os.path.exists(L3_FILE):
        try:
            with open(L3_FILE, 'r', encoding='utf-8') as f:
                content = f.read()
                if content.strip():
                    layers.append(("L3 核心规则", content))
        except IOError:
            pass

    # 同时加载 CLAUDE.md（项目级配置）
    if os.path.exists(CLAUDE_MD):
        try:
            with open(CLAUDE_MD, 'r', encoding='utf-8') as f:
                content = f.read()
                # 取前 100 行作为摘要
                lines = content.split('\n')[:100]
                if lines:
                    layers.append(("项目配置摘要", '\n'.join(lines)))
        except IOError:
            pass

    return layers


def format_output():
    """格式化输出给 Claude Code"""
    output_parts = []

    # L3 优先级最高
    l3_layers = load_layer3()
    for title, content in l3_layers:
        output_parts.append(f"## {title}\n{content}\n")

    # L1 知识图谱
    l1_items = load_layer1()
    if l1_items:
        output_parts.append("## 项目知识库 (L1)")
        for item in l1_items:
            content = item.get('content', '')
            item_type = item.get('type', 'info')
            file_ref = f" [{item.get('file', '')}]" if item.get('file') else ""
            output_parts.append(f"- [{item_type}] {content}{file_ref}")
        output_parts.append("")

    # L2 近期上下文
    l2_logs = load_layer2()
    if l2_logs:
        output_parts.append("## 最近工作记录 (L2)\n")
        output_parts.append('\n\n'.join(l2_logs))

    return '\n\n'.join(output_parts)


def main():
    result = format_output()
    print(result)

    # 写入临时文件供调试
    debug_file = f"{MEMORY_DIR}/loaded_memory.txt"
    try:
        with open(debug_file, 'w', encoding='utf-8') as f:
            f.write(result)
    except IOError:
        pass


if __name__ == "__main__":
    main()
