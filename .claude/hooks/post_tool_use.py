#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
三层记忆系统 - PostToolUse Hook
功能：工具使用后自动提取知识写入 L1，记录对话到 L2
"""
import json
import os
import sys
import re
import io
from datetime import datetime

# 设置 UTF-8 输出
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stdin = io.TextIOWrapper(sys.stdin.buffer, encoding='utf-8')

MEMORY_DIR = ".claude/memory"
L1_FILE = f"{MEMORY_DIR}/items.json"
L2_DIR = f"{MEMORY_DIR}/daily_logs"


def extract_knowledge(text):
    """从对话中提取结构化知识"""
    if not text or len(text) < 10:
        return []

    items = []

    # 技术决策模式
    tech_patterns = [
        r'(?:决定|选择|使用|采用|配置)\s*(\w+)\s*(?:作为|用于|版本|方案)',
        r'(?:安装|引入|依赖)\s*([^\s]+[@\d\.]*)',
        r'(?:修复|解决)\s*(.+?)(?:\s*问题|\s*bug|\s*错误)',
    ]

    for pattern in tech_patterns:
        matches = re.finditer(pattern, text, re.IGNORECASE)
        for match in matches:
            items.append({
                "type": "decision",
                "content": match.group(0),
                "timestamp": datetime.now().isoformat(),
                "status": "active"
            })

    # 踩坑记录模式
    pitfall_keywords = ['注意', '避免', '坑', 'issue', '错误', '失败', 'warning', 'error']
    if any(kw in text.lower() for kw in pitfall_keywords):
        items.append({
            "type": "pitfall",
            "content": text[:200],  # 截取前200字符
            "timestamp": datetime.now().isoformat(),
            "status": "active"
        })

    return items


def append_to_layer1(items):
    """追加到 L1（items.json）"""
    os.makedirs(os.path.dirname(L1_FILE), exist_ok=True)

    existing = []
    if os.path.exists(L1_FILE):
        try:
            with open(L1_FILE, 'r', encoding='utf-8') as f:
                existing = json.load(f)
        except (json.JSONDecodeError, IOError):
            existing = []

    # 去重：相同内容不重复添加
    existing_contents = {item['content'] for item in existing}
    new_items = [item for item in items if item['content'] not in existing_contents]

    existing.extend(new_items)

    with open(L1_FILE, 'w', encoding='utf-8') as f:
        json.dump(existing, f, ensure_ascii=False, indent=2)

    return len(new_items)


def append_to_layer2(text):
    """追加到 L2（按日期）"""
    today = datetime.now().strftime("%Y-%m-%d")
    log_file = f"{L2_DIR}/{today}.md"

    os.makedirs(L2_DIR, exist_ok=True)

    timestamp = datetime.now().strftime("%H:%M:%S")
    entry = f"\n## [{timestamp}]\n{text}\n"

    with open(log_file, 'a', encoding='utf-8') as f:
        f.write(entry)


def main():
    # 从标准输入读取对话内容
    input_text = sys.stdin.read()

    if not input_text or len(input_text) < 10:
        return

    # 提取知识
    items = extract_knowledge(input_text)

    # 写入 L1
    if items:
        added = append_to_layer1(items)
        print(f"[记忆系统] 提取 {added} 条知识到 L1")

    # 全文写入 L2
    if len(input_text) > 50:
        append_to_layer2(input_text)
        print(f"[记忆系统] 已记录到今日日志")


if __name__ == "__main__":
    main()
