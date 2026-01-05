from typing import Optional, Dict, Any
from pydantic import BaseModel, Field
from datetime import datetime

class BuffScanTaskDTO(BaseModel):
    id: int
    name: str
    status: int
    cron_expression: Optional[str] = None
    scan_interval: Optional[int] = 5
    duration_minutes: Optional[int] = 0
    task_type: int
    user_id: Optional[int] = None
    config: Optional[Dict[str, Any]] = None
    goods_id: Optional[int] = None
    max_price: Optional[float] = None
    min_paintwear: Optional[float] = None
    max_paintwear: Optional[float] = None
    min_profit: Optional[float] = None
    buy_count: Optional[int] = 0
    success_count: Optional[int] = 0

    class Config:
        from_attributes = True  # 允许从 SQLAlchemy 模型转换
