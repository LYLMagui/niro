#!/usr/bin/env python3
import json
import subprocess
import sys

payload = json.load(sys.stdin)
prompt = payload.get("prompt") or payload.get("user_prompt") or payload.get("message") or ""

has_submit_push = "提交推送" in prompt or "提交并推送" in prompt
has_build = "构建" in prompt

if not (has_submit_push and has_build):
    sys.exit(0)

try:
    branch = subprocess.check_output(
        ["git", "branch", "--show-current"],
        text=True,
        stderr=subprocess.DEVNULL,
    ).strip()
except Exception:
    branch = ""

if branch == "test":
    message = (
        "检测到用户要求“提交推送并构建”，且当前分支是 test。"
        "完成提交和推送后，必须继续执行 /connect-niro-server 对远程 /home/app/niro 拉取并构建启动 Niro 测试环境。"
        "如果用户只是要求“提交并推送”且没有“并构建”，不要执行远程构建。"
    )
else:
    message = (
        f"检测到用户要求“提交推送并构建”，但当前分支是 {branch or '未知'}，不是 test。"
        "不要执行远程构建；先告知用户该规则只在 test 分支触发。"
    )

print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "UserPromptSubmit",
        "additionalContext": message,
    }
}, ensure_ascii=False))
