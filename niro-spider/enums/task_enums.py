from enum import IntEnum

class BuffTaskType(IntEnum):
    """BUFF 任务类型枚举"""
    
    # 普通扫货任务
    SNIPING = 0
    FLIPPING = 1
    
    # 系统同步任务
    SYNC_CATEGORY = 2
    SYNC_GOODS = 3
    SYNC_STICKER = 4
    SYNC_CATEGORY_GOODS = 5

    @classmethod
    def is_system_task(cls, task_type: int) -> bool:
        """判断是否为系统同步任务"""
        return task_type >= cls.SYNC_CATEGORY
