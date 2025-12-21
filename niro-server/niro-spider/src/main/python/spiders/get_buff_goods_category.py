import jmespath
import random
import sys
import os
import requests
import time

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.postgres_pool import pg_pool
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"
PAGE_NUM = 1
TOTAL_PAGE = 151
MAX_TOTAL_PAGE = 10
CURRENT_PAGE = 1
CATEGORY_GROUP = "knife"

def get_buff_goods_category():
    """
    一级分类
    """

    pass

def get_buff_goods_children_category():
    """
    获取buff饰品二级分类
    """

    url = BUFF_HOST + "/api/market/goods"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0",
        "cookie": settings.BUFF_COOKIE,
    }
    params = {
        "game": "csgo",
        "page_num": PAGE_NUM,
        "category_group": CATEGORY_GROUP,
        "tab": "selling",
    }
    try:
        # logger.info(f"正在爬取第{PAGE_NUM}页饰品分类[{CATEGORY_GROUP}]")
        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()
        json = response.json()
        if not json or json["data"] is None:
            raise Exception("请求失败，无数据")

        data = json["data"]
        # logger.info(f" ✅️ 响应报文: {data}")
        category_types = jmespath.search(
            f"items[*].goods_info.info.tags.category", data
        )
        # logger.info(f"主分类: {category_name},子分类: {category_types}")

        category_list = []
        for category in category_types:
            internal_name = category.get("internal_name")
            save_category = {
                "parent_id": 1,
                "name": category.get("localized_name", ""),
                "internal_name": internal_name,
            }
            # logger.info(f"✅️:{save_category}")
            category_list.append(save_category)
        return category_list
    except Exception as e:
        logger.error(f"❌ 请求失败: {e}", exc_info=True)


def sync_categories(total_categories):
    """
    同步分类保存到数据库
    """
    success_count = 0
    count = 1
    try:
        with pg_pool.get_conn() as conn:
            with conn.cursor() as cur:
                logger.info(f"正在保存分类数据...")
                for catetory in total_categories:
                    sql = """
                        INSERT INTO buff_weapon_categories (name, parent_id, internal_name)
                        VALUES (%s, %s, %s)
                        ON CONFLICT (internal_name)
                        DO UPDATE SET
                            name = EXCLUDED.name,
                            parent_id = EXCLUDED.parent_id,
                            internal_name = EXCLUDED.internal_name
                    """
                    logger.info(f"正在插入第{count}条记录...")
                    cur.execute(
                        sql,
                        (
                            catetory.get("name"),
                            catetory.get("parent_id"),
                            catetory.get("internal_name"),
                        ),
                    )
                    logger.info(f"✅️ 保存成功")
                    count += 1
                    success_count += 1
            conn.commit()
        logger.info(f"✅ 分类保存完成，共处理 {success_count} 条记录")

    except Exception as e:
        logger.error(f"❌ 分类同步失败: {e}")
        raise e


if __name__ == "__main__":
    from utils.logger import setup_logging
    import time, random

    setup_logging()
    total_categories = []
    min_temp_time = 1
    max_temp_time = 4

    for page in range(1, MAX_TOTAL_PAGE + 1):
        categories = get_buff_goods_children_category()
        logger.info(f"第 {PAGE_NUM} 页抓取完成")
        total_categories.extend(categories)
        elapsed = random.uniform(min_temp_time, max_temp_time)
        time.sleep(elapsed)
        PAGE_NUM = page + 1
        logger.info(f"暂停 {elapsed:.2f} 秒,开始抓取第{PAGE_NUM}页")

    dedup_map = {c["internal_name"]: c for c in total_categories}
    total_categories = list(dedup_map.values())

    logger.info(f"最终去重后分类数: {len(total_categories)}")
    logger.info(f"分类列表: {total_categories}")
    sync_categories(total_categories)
