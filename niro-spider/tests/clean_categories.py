import os
import sys
import psycopg2
from loguru import logger

# Add project root to sys.path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import settings

# 经过测试确认返回全量数据的无效分类 internal_name 列表
# 这些通常是聚合大类，不应作为二级分类存在
INVALID_INTERNAL_NAMES = [
    # 用户反馈的名称
    'csgo_type_shotgun', 'csgo_type_machinegun', 'csgo_type_knife',
    'weapon_knife_kukri', 'csgo_tool_sticker', 'type_hands',
    'csgo_type_rifle', 'csgo_type_sniperrifle', 'csgo_type_pistol',
    'weapon_taser', 'csgo_type_smg',
    # 数据库中可能存在的简称版本 (防止误删，将在SQL中限制 parent_id != 0)
    'shotgun', 'machinegun', 'knife', 'sticker', 'hands', 'rifle',
    'sniperrifle', 'pistol', 'smg'
]

def clean_database():
    """清理数据库中的无效二级分类"""
    logger.info("🔌 连接数据库...")
    try:
        conn = psycopg2.connect(
            host=settings.DB_HOST,
            port=settings.DB_PORT,
            dbname=settings.DB_NAME,
            user=settings.DB_USERNAME,
            password=settings.DB_PASSWORD
        )
    except Exception as e:
        logger.error(f"无法连接数据库: {e}")
        return

    try:
        with conn.cursor() as cur:
            # 1. 查询确认 (仅查询 parent_id != 0 的，避免误删正确的一级分类)
            placeholders = ','.join(['%s'] * len(INVALID_INTERNAL_NAMES))
            query_check = f"""
                SELECT id, name, internal_name, parent_id 
                FROM buff_goods_categories 
                WHERE internal_name IN ({placeholders}) AND parent_id != 0
            """
            cur.execute(query_check, tuple(INVALID_INTERNAL_NAMES))
            rows = cur.fetchall()
            
            if not rows:
                logger.info("✅ 数据库中未发现这些无效的二级分类，无需清理。")
                logger.info("💡 提示：这些分类可能已经是正确的一级分类(parent_id=0)，或者已被删除。")
                return

            logger.warning(f"🚨 发现 {len(rows)} 条无效分类记录 (将被删除):")
            for row in rows:
                logger.info(f"   - ID: {row[0]} | Name: {row[1]} | Internal: {row[2]} | ParentID: {row[3]}")
            
            # 2. 执行删除
            query_del = f"""
                DELETE FROM buff_goods_categories 
                WHERE internal_name IN ({placeholders}) AND parent_id != 0
            """
            cur.execute(query_del, tuple(INVALID_INTERNAL_NAMES))
            deleted_count = cur.rowcount
            conn.commit()
            
            logger.success(f"🗑️ 已成功删除 {deleted_count} 条记录！")
            
    except Exception as e:
        logger.error(f"❌ 清理失败: {e}")
        conn.rollback()
    finally:
        conn.close()



if __name__ == "__main__":
    clean_database()
