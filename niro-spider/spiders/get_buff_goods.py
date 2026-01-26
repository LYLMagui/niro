import sys
import os
import json
import time
from typing import List, Dict, Any, Optional
from loguru import logger
from storage.redis_pool import redis_async as global_redis_client

# --- 业务逻辑 ---

async def save_goods_batch(goods_list: List[Dict], category_id: int = 0, redis_async: Any = None, sync_tag: str = None, category_name: str = None):
    """批量保存商品数据 (Redis 上报)
    
    由 ShardedSpiderExecutor 调用，负责将抓取到的商品数据推送到 Redis 队列。
    仅上报商品元数据，不包含价格、数量等变动频繁的字段。
    """
    if not goods_list: return 0
    
    # 优先使用传入的 redis 客户端，否则使用全局
    redis_client = redis_async or global_redis_client
    
    display_name = category_name or str(category_id)
    
    # 分布式状态锁：以 category_id 为键，避免多个账号同时在更新同一个分类下的商品
    lock_key = f"niro:lock:goods_sync:{category_id}"
    lock_acquired = False
    
    try:
        # 尝试获取 Redis 锁，有效期 60 秒
        if redis_client:
            if await redis_client.set(lock_key, "locked", ex=60, nx=True):
                lock_acquired = True
            else:
                logger.warning(f"⏳ 另一个账号正在执行分类 [分类: {display_name}] 商品保存，跳过本次写入")
                return 0
        
        # 1. 过滤和转换数据 (只保留元数据字段)
        filtered_goods_list = []
        seen_ids = set()
        
        for g in goods_list:
            g_id = g.get('id') or g.get('goods_id')
            if not g_id or g_id in seen_ids:
                continue
            seen_ids.add(g_id)
            
            goods_info = g.get('goods_info', {})
            info = goods_info.get('info', {})
            tags = info.get('tags', {})
            
            item = {
                "goods_id": g_id,
                "name": g.get("name"),
                "market_hash_name": g.get("market_hash_name"),
                "short_name": g.get("short_name"),
                "icon_url": goods_info.get("icon_url"),
                "original_icon_url": goods_info.get("original_icon_url"),
                "category_id": category_id,
                "internal_name": tags.get("weapon", {}).get("internal_name") or tags.get("type", {}).get("internal_name"),
                "rarity": tags.get("rarity", {}).get("internal_name"),
                "exterior": tags.get("exterior", {}).get("internal_name"),
                "tags": tags
            }
            filtered_goods_list.append(item)
            
        if not filtered_goods_list:
            return 0

        # 2. 构建上报消息
        message = {
            "type": "GOODS_LIST",
            "timestamp": int(time.time()),
            "data": filtered_goods_list,
            "meta": {
                "categoryId": category_id,
                "categoryName": category_name,
                "syncTag": sync_tag
            }
        }

        # 推送到 Redis List
        if redis_client:
            await redis_client.rpush("niro:data:report", json.dumps(message, ensure_ascii=False))
            logger.info(f"✅ 已上报 {len(filtered_goods_list)} 条商品元数据到 Redis (Category: {display_name})")
        else:
            logger.error("❌ 未提供 Redis 客户端，无法上报商品数据")
            
        return len(filtered_goods_list)

    except Exception as e:
        logger.error(f"❌ 上报商品数据失败: {e}")
        return 0
    finally:
        # 主动释放锁
        if lock_acquired and redis_client:
            await redis_client.delete(lock_key)
