import sys
import os
from typing import List, Dict, Any
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy import func

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.models import BuffGoodsCategory
from storage.database import async_session_factory
from utils.logger import get_logger

logger = get_logger(__name__)

async def save_categories(categories: List[Dict]):
    """保存抓取到的分类数据 (UPSERT)
    
    由 ShardedSpiderExecutor 调用，负责将抓取到的分类树数据写入数据库。
    """
    if not categories:
        return

    db_items = []
    for item in categories:
        internal_name = item.get('internal_name')
        if not internal_name: continue
        
        db_items.append({
            'name': item.get('name'),
            'internal_name': internal_name,
            'category_type': item.get('category_type', 'type'),
            'full_internal_name': internal_name,
            'parent_id': item.get('parent_id', 0)
        })

    if not db_items:
        return

    async with async_session_factory() as session:
        try:
            stmt = insert(BuffGoodsCategory).values(db_items)
            stmt = stmt.on_conflict_do_update(
                index_elements=['internal_name'],
                set_={
                    'name': stmt.excluded.name,
                    'category_type': stmt.excluded.category_type,
                    'full_internal_name': stmt.excluded.full_internal_name,
                    'parent_id': stmt.excluded.parent_id,
                    'update_time': func.now()
                }
            )
            await session.execute(stmt)
            await session.commit()
            logger.info(f"✅ 成功保存 {len(db_items)} 个分类数据")
        except Exception as e:
            await session.rollback()
            logger.error(f"❌ 保存分类失败: {e}")
