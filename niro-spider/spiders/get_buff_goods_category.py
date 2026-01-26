import json
import time
from typing import List, Dict, Any
from loguru import logger
from storage.redis_pool import redis_async as global_redis_client

# --- 业务逻辑 ---

async def save_categories(categories: List[Dict], redis_async: Any = None, parent_id: int = 0) -> bool:
    """保存抓取到的分类数据 (Redis 上报)
    
    由 ShardedSpiderExecutor 调用，负责将抓取到的分类树数据推送到 Redis 队列。
    """
    if not categories:
        return True

    redis_client = redis_async or global_redis_client

    db_items = []
    for item in categories:
        internal_name = item.get('internal_name')
        if not internal_name: continue
        
        db_items.append({
            'name': item.get('name'),
            'internal_name': internal_name,
            'category_type': item.get('category_type', 'type'),
            'full_internal_name': internal_name,
            'parent_id': parent_id
        })

    if not db_items:
        return True

    # 构建上报消息
    message = {
        "type": "CATEGORY_LIST",
        "timestamp": int(time.time()),
        "data": db_items
    }

    try:
        # 推送到 Redis List
        if redis_client:
            await redis_client.rpush("niro:data:report", json.dumps(message, ensure_ascii=False))
            logger.info(f"✅ 已上报 {len(db_items)} 条分类数据到 Redis")
            return True
        else:
            logger.error("❌ 未提供 Redis 客户端，无法上报分类数据")
            return False
    except Exception as e:
        logger.error(f"❌ 上报分类数据失败: {e}")
        return False

