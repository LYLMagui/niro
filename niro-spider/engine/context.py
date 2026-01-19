from contextvars import ContextVar
from typing import Optional, Dict, Any

# 链路追踪 ID
trace_id_var: ContextVar[str] = ContextVar("trace_id", default="")

# 任务 ID
task_id_var: ContextVar[Optional[int]] = ContextVar("task_id", default=None)

# 用户 ID (后端管理账号的用户)
user_id_var: ContextVar[Optional[int]] = ContextVar("user_id", default=None)

# 账号 ID
account_id_var: ContextVar[Optional[int]] = ContextVar("account_id", default=None)

# 账号名称
account_name_var: ContextVar[str] = ContextVar("account_name", default="System")

def set_context(trace_id: str, task_id: Optional[int] = None, user_id: Optional[int] = None, account_id: Optional[int] = None, account_name: str = "System"):
    trace_id_var.set(trace_id)
    task_id_var.set(task_id)
    user_id_var.set(user_id)
    account_id_var.set(account_id)
    account_name_var.set(account_name)

def get_context_dict() -> Dict[str, Any]:
    return {
        "traceId": trace_id_var.get(),
        "taskId": task_id_var.get(),
        "userId": user_id_var.get(),
        "accountId": account_id_var.get(),
        "accountName": account_name_var.get()
    }
