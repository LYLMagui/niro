import sys
import os
import json
import time
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field, AliasPath

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.models import BuffGoods
from storage.database import async_session_factory
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy import func
from utils.logger import get_logger

logger = get_logger(__name__)

# --- Pydantic 模型定义 (仅保留必要的) ---

class BuffGoodsItem(BaseModel):
    goods_id: int = Field(alias="id")
    name: str
    market_hash_name: Optional[str] = None
    short_name: Optional[str] = None
    
    # 使用 AliasPath 提取深度嵌套字段
    icon_url: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "icon_url"))
    original_icon_url: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "original_icon_url"))
    rarity: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "rarity", "internal_name"))
    exterior: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "exterior", "internal_name"))
    type: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "type", "internal_name"))
    tags_dict: Optional[Dict[str, Any]] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags"))

# --- 业务逻辑 ---

async def save_goods_batch(goods_list: List[Dict], category_id: int = 0, redis_async: Any = None):
    """批量保存商品数据 (UPSERT)
    
    由 ShardedSpiderExecutor 调用，负责将抓取到的商品数据写入数据库。
    """
    if not goods_list: return 0
    
    # 分布式状态锁：以 category_id 为键，避免多个账号同时在更新同一个分类下的商品
    lock_key = f"niro:lock:goods_sync:{category_id}"
    
    # 尝试获取 Redis 锁，有效期 60 秒
    if redis_async:
        if not await redis_async.set(lock_key, "locked", ex=60, nx=True):
            logger.warning(f"⏳ 另一个账号正在执行分类 [ID:{category_id}] 商品保存，跳过本次写入")
            return 0
    
    # 1. 对 goods_list 进行去重处理 (基于 goods_id)
    unique_goods = {}
    for g in goods_list:
        g_id = g.get('goods_id') or g.get('id')
        if g_id:
            unique_goods[g_id] = g
    
    final_goods_list = []
    for g_id, g in unique_goods.items():
        # 统一字段名
        item = {
            "goods_id": g_id,
            "name": g.get("name"),
            "market_hash_name": g.get("market_hash_name") or "",
            "short_name": g.get("short_name") or "",
            "icon_url": g.get("icon_url") or "",
            "original_icon_url": g.get("original_icon_url") or "",
            "rarity": g.get("rarity") or "",
            "exterior": g.get("exterior") or "",
            "type": g.get("type") or "",
            "category_id": category_id,
            "tags": json.dumps(g.get("tags_dict"), ensure_ascii=False) if g.get("tags_dict") else g.get("tags")
        }
        final_goods_list.append(item)
    
    if not final_goods_list:
        return 0

    async with async_session_factory() as session:
        try:
            stmt = insert(BuffGoods).values(final_goods_list)
            upsert_stmt = stmt.on_conflict_do_update(
                index_elements=['goods_id'],
                set_={
                    "name": stmt.excluded.name,
                    "short_name": stmt.excluded.short_name,
                    "market_hash_name": stmt.excluded.market_hash_name,
                    "icon_url": stmt.excluded.icon_url,
                    "original_icon_url": stmt.excluded.original_icon_url,
                    "rarity": stmt.excluded.rarity,
                    "exterior": stmt.excluded.exterior,
                    "type": stmt.excluded.type,
                    "category_id": stmt.excluded.category_id,
                    "tags": stmt.excluded.tags,
                    "update_time": func.now()
                }
            )
            result = await session.execute(upsert_stmt)
            await session.commit()
            return result.rowcount
        except Exception as e:
            await session.rollback()
            logger.error(f"❌ 批量保存商品失败: {e}")
            return 0
        finally:
            if redis_async:
                await redis_async.delete(lock_key)
