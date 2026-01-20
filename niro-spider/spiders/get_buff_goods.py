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

async def save_goods_batch(goods_list: List[Dict], category_id: int = 0, redis_async: Any = None, sync_tag: str = None, category_name: str = None):
    """批量保存商品数据 (UPSERT)
    
    由 ShardedSpiderExecutor 调用，负责将抓取到的商品数据写入数据库。
    """
    if not goods_list: return 0
    
    # 获取分类名称用于日志
    if not category_name and category_id:
        async with async_session_factory() as session:
            from sqlalchemy import select
            from storage.models import BuffGoodsCategory
            cat_stmt = select(BuffGoodsCategory.name).where(BuffGoodsCategory.id == category_id)
            cat_result = await session.execute(cat_stmt)
            category_name = cat_result.scalar()
    
    display_name = category_name or str(category_id)
    
    # 分布式状态锁：以 category_id 为键，避免多个账号同时在更新同一个分类下的商品
    lock_key = f"niro:lock:goods_sync:{category_id}"
    
    # 尝试获取 Redis 锁，有效期 60 秒
    if redis_async:
        if not await redis_async.set(lock_key, "locked", ex=60, nx=True):
            logger.warning(f"⏳ 另一个账号正在执行分类 [分类: {display_name}] 商品保存，跳过本次写入")
            return 0
    
    # 1. 对 goods_list 进行去重处理 (基于 goods_id)
    unique_goods = {}
    for g in goods_list:
        g_id = g.get('goods_id') or g.get('id')
        if g_id:
            unique_goods[g_id] = g
    
    final_goods_list = []
    for g_id, g in unique_goods.items():
        try:
            # 使用 Pydantic 模型进行字段提取与校验
            validated_item = BuffGoodsItem.model_validate(g)
            
            item = {
                "goods_id": validated_item.goods_id,
                "name": validated_item.name,
                "market_hash_name": validated_item.market_hash_name or "",
                "short_name": validated_item.short_name or "",
                "icon_url": validated_item.icon_url or "",
                "original_icon_url": validated_item.original_icon_url or "",
                "rarity": validated_item.rarity or "",
                "exterior": validated_item.exterior or "",
                "category_id": category_id,
                "tags": validated_item.tags_dict,
                "last_sync_tag": sync_tag
            }
            final_goods_list.append(item)
        except Exception as ve:
            logger.warning(f"⚠️ 商品 {g_id} 数据校验失败，尝试手动提取: {ve}")
            # 备选方案：手动提取
            item = {
                "goods_id": g_id,
                "name": g.get("name"),
                "market_hash_name": g.get("market_hash_name") or "",
                "short_name": g.get("short_name") or "",
                "icon_url": g.get("icon_url") or "",
                "original_icon_url": g.get("original_icon_url") or "",
                "rarity": g.get("rarity") or "",
                "exterior": g.get("exterior") or "",
                "category_id": category_id,
                "tags": g.get("tags_dict") or g.get("tags"),
                "last_sync_tag": sync_tag
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
                    "category_id": stmt.excluded.category_id,
                    "tags": stmt.excluded.tags,
                    "last_sync_tag": stmt.excluded.last_sync_tag,
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

async def delete_stale_goods(category_id: int, current_tag: str, category_name: str = None):
    """清理分类下过期的商品数据 (异步删除)"""
    if not category_id or not current_tag:
        return 0
    
    async with async_session_factory() as session:
        try:
            from sqlalchemy import delete, select
            from storage.models import BuffGoodsCategory
            
            # 获取分类名称
            if not category_name:
                cat_stmt = select(BuffGoodsCategory.name).where(BuffGoodsCategory.id == category_id)
                cat_result = await session.execute(cat_stmt)
                category_name = cat_result.scalar() or str(category_id)

            # 查找该分类下，但版本标识不是当前版本的商品（说明在本次全量同步中未出现，已下架）
            stmt = delete(BuffGoods).where(
                BuffGoods.category_id == category_id,
                BuffGoods.last_sync_tag != current_tag
            )
            result = await session.execute(stmt)
            await session.commit()
            count = result.rowcount
            if count > 0:
                logger.info(f"🧹 [分类: {category_name}] 清理已下架商品: {count} 条")
            return count
        except Exception as e:
            await session.rollback()
            logger.error(f"❌ 清理分类 {category_id} 过期数据失败: {e}")
            return 0
