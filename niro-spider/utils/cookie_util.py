import os
import sys

# 确保能导入 storage
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(current_dir)
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from storage.postgres_pool import pg_pool
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

def get_latest_cookie(user_id=None):
    """
    从数据库获取指定用户或最新的 Buff Cookie
    :param user_id: 用户 ID，如果不指定则获取最新的一条
    :return: Cookie 字符串，如果数据库没有则返回 settings 中的默认值
    """
    try:
        with pg_pool.get_cursor() as cur:
            if user_id:
                sql = "SELECT buff_cookie FROM user_buff_settings WHERE user_id = %s"
                params = (user_id,)
            else:
                sql = "SELECT buff_cookie FROM user_buff_settings ORDER BY update_time DESC LIMIT 1"
                params = ()

            cur.execute(sql, params)
            row = cur.fetchone()
            if row and row.get('buff_cookie'):
                return row['buff_cookie']
    except Exception as e:
        logger.error(f"❌ 获取数据库 Cookie 失败: {e}")
    
    # 兜底使用配置文件的 Cookie
    return settings.BUFF_COOKIE
