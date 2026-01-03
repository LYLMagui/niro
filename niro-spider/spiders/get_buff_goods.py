
import random
import sys
import os
import requests
import time
import psycopg2.extras
import json

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

def fetch_goods_data(category_internal_name, page_num=1, max_retries=3):
    """
    请求 Buff 商品列表接口
    params: game=csgo, category=..., page_num=..., tab=selling
    """
    url = f"{BUFF_HOST}/api/market/goods"
    params = {
        "game": "csgo",
        "category": category_internal_name,
        "page_num": page_num,
        "tab": "selling"
    }
    
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "cookie": settings.BUFF_COOKIE,
        "accept": "application/json, text/javascript, */*; q=0.01",
        "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
        "referer": f"https://buff.163.com/market/csgo?tab=selling&category={category_internal_name}",
        "x-requested-with": "XMLHttpRequest",
    }
    
    for attempt in range(max_retries):
        try:
            full_url = requests.Request('GET', url, params=params).prepare().url
            logger.debug(f"🌐 请求 URL: {full_url}")
            
            response = requests.get(url, headers=headers, params=params, timeout=15)
            
            # 处理 429 限流
            if response.status_code == 429:
                # 指数退避: 15s, 30s, 60s... 加上随机抖动
                wait_time = (2 ** attempt) * 15 + random.uniform(5, 15)
                logger.warning(f"⚠️ 触发限流 (429)，暂停 {wait_time:.2f} 秒后进行第 {attempt + 1} 次重试...")
                time.sleep(wait_time)
                continue

            response.raise_for_status()
            
            json_data = response.json()
            if json_data.get("code") != "OK":
                err_msg = json_data.get("error", "Unknown Error")
                logger.error(f"❌ API 返回错误: {json_data.get('code')} - {err_msg}")
                
                if json_data.get("code") == "Login Required":
                     logger.critical("🔑 Cookie 已失效，请在 settings.py 中更新 BUFF_COOKIE")
                     return None
                
                # 其他错误可能需要重试
                time.sleep(5)
                continue

            data = json_data.get("data")
            if data is None:
                raise Exception(f"请求成功但 data 字段为空: {json_data}")
                
            return data
            
        except requests.exceptions.RequestException as e:
            logger.error(f"❌ 网络请求异常 (尝试 {attempt + 1}/{max_retries}): {e}")
            time.sleep(random.uniform(5, 10))
            
    return None

def get_db_goods_count(category_id):
    """
    获取数据库中某个分类的商品数量
    """
    try:
        # 使用默认 cursor_factory=None 获取元组格式，方便 index 访问
        with pg_pool.get_cursor(cursor_factory=None) as cur:
            sql = "SELECT COUNT(*) FROM buff_goods WHERE category_id = %s"
            cur.execute(sql, (category_id,))
            res = cur.fetchone()
            return res[0] if res else 0
    except Exception as e:
        logger.error(f"❌ 获取数据库商品计数失败: {e}")
        return 0

def get_db_existing_ids(goods_ids):
    """
    批量检查哪些商品 ID 已经存在于数据库中
    """
    if not goods_ids:
        return set()
    try:
        # 使用默认 cursor_factory=None 获取元组格式
        with pg_pool.get_cursor(cursor_factory=None) as cur:
            sql = "SELECT goods_id FROM buff_goods WHERE goods_id = ANY(%s)"
            cur.execute(sql, (list(goods_ids),))
            return {row[0] for row in cur.fetchall()}
    except Exception as e:
        logger.error(f"❌ 检查已存在 ID 失败: {e}")
        return set()

def get_secondary_categories():
    """
    从数据库获取所有二级分类 (parent_id > 0)
    """
    logger.info("正在获取待抓取的二级分类...")
    try:
        with pg_pool.get_cursor() as cur:
            # 只获取二级分类 (假设 parent_id > 0)
            sql = "SELECT id, internal_name, name FROM buff_goods_categories WHERE parent_id > 0 ORDER BY id"
            cur.execute(sql)
            rows = cur.fetchall()
            logger.info(f"✅ 获取到 {len(rows)} 个二级分类")
            return rows
    except Exception as e:
        logger.error(f"❌ 获取分类失败: {e}")
        return []

def save_goods_batch(goods_list):
    """
    批量保存商品数据 (UPSERT)
    """
    if not goods_list:
        return 0
        
    try:
        with pg_pool.get_cursor() as cur:
            sql = """
                INSERT INTO buff_goods (
                    goods_id, name, short_name, internal_name, category_id,
                    rarity, exterior, market_hash_name, icon_url, original_icon_url, tags
                ) VALUES %s
                ON CONFLICT (goods_id) DO UPDATE SET
                    name = EXCLUDED.name,
                    short_name = EXCLUDED.short_name,
                    internal_name = EXCLUDED.internal_name,
                    category_id = EXCLUDED.category_id,
                    rarity = EXCLUDED.rarity,
                    exterior = EXCLUDED.exterior,
                    market_hash_name = EXCLUDED.market_hash_name,
                    icon_url = EXCLUDED.icon_url,
                    original_icon_url = EXCLUDED.original_icon_url,
                    tags = EXCLUDED.tags,
                    update_time = CURRENT_TIMESTAMP
            """
            
            values = []
            for g in goods_list:
                values.append((
                    g['goods_id'],
                    g['name'],
                    g['short_name'],
                    g['internal_name'],
                    g['category_id'],
                    g['rarity'],
                    g['exterior'],
                    g['market_hash_name'],
                    g['icon_url'],
                    g['original_icon_url'],
                    json.dumps(g['tags'], ensure_ascii=False)
                ))
            
            psycopg2.extras.execute_values(cur, sql, values)
            return len(values)
            
    except Exception as e:
        logger.error(f"❌ 批量保存失败: {e}")
        return 0

def process_category(category, force=False):
    """
    处理单个分类：分页抓取所有商品
    force: 是否强制抓取所有页，即使数量匹配或已存在
    """
    cat_id = category['id']
    cat_internal = category['internal_name']
    cat_name = category['name']
    
    logger.info(f"🚀 开始处理分类: {cat_name} ({cat_internal}) [ID={cat_id}]")
    
    # 预检查：如果非强制模式，先看一眼数据库里有多少
    db_count = get_db_goods_count(cat_id)
    
    page = 1
    total_pages = 1 
    total_saved = 0
    seen_goods_ids = set() 
    
    while page <= total_pages:
        logger.info(f"   正在抓取第 {page}/{total_pages} 页...")
        
        data = fetch_goods_data(cat_internal, page_num=page)
        if not data:
            logger.warning(f"   第 {page} 页获取失败，跳过")
            page += 1
            continue
            
        if page == 1:
            total_pages = data.get("total_page", 1)
            total_count = data.get("total_count", 0)
            logger.info(f"   📊 Buff 共有 {total_count} 个商品，数据库已有 {db_count} 个")
            
            # 优化点 1：数量完全匹配且不强制更新，直接跳过整个分类
            if not force and db_count >= total_count and total_count > 0:
                logger.info(f"   ✅ 数量已匹配 ({db_count}/{total_count})，跳过该分类")
                return

        items = data.get("items", [])
        if not items:
            break
            
        # 提取当前页的所有 ID
        current_page_ids = {item.get("id") for item in items if item.get("id")}
        
        # 优化点 2：智能跳过 (Early Exit)
        # 如果当前页的所有商品 ID 都在数据库里了，说明后面可能也没新东西了
        if not force:
            existing_ids = get_db_existing_ids(current_page_ids)
            if len(existing_ids) == len(current_page_ids) and len(current_page_ids) > 0:
                logger.info(f"   ⏩ 第 {page} 页商品全部已存在，触发智能停机，跳过后续页码")
                break

        # 解析数据
        parsed_goods = []
        for item in items:
            try:
                goods_id = item.get("id")
                if goods_id in seen_goods_ids:
                    continue
                seen_goods_ids.add(goods_id)

                goods_info = item.get("goods_info", {})
                info_tags = goods_info.get("info", {}).get("tags", {})
                
                # 提取内部名称
                weapon_tag = info_tags.get("weapon", {})
                internal_name = weapon_tag.get("internal_name", "") or info_tags.get("category", {}).get("internal_name", "")
                
                parsed_goods.append({
                    "goods_id": goods_id,
                    "name": item.get("name", ""),
                    "short_name": item.get("short_name", ""),
                    "internal_name": internal_name,
                    "category_id": cat_id,
                    "rarity": info_tags.get("rarity", {}).get("internal_name", ""),
                    "exterior": info_tags.get("exterior", {}).get("internal_name", ""),
                    "market_hash_name": item.get("market_hash_name", ""),
                    "icon_url": goods_info.get("icon_url", ""),
                    "original_icon_url": goods_info.get("original_icon_url", ""),
                    "tags": info_tags 
                })
            except Exception as e:
                logger.error(f"解析商品出错: {item.get('id')} - {e}")
        
        if parsed_goods:
            count = save_goods_batch(parsed_goods)
            total_saved += count
            logger.info(f"   ✅ 本页新增/更新 {count} 条数据")
        
        page += 1
        time.sleep(random.uniform(2, 5)) # 基础延时
        
    logger.info(f"✨ 分类 {cat_name} 处理完成，本次任务影响 {total_saved} 个商品")

def run_goods_sync(force=False):
    """
    暴露给外部调用的商品同步主入口
    """
    logger.info(f"=== 开始抓取 Buff 商品数据 (Force Mode: {force}) ===")
    
    categories = get_secondary_categories()
    if not categories:
        logger.warning("未找到分类数据")
        return
        
    for i, cat in enumerate(categories):
        process_category(cat, force=force)
        
        if i < len(categories) - 1:
            sleep_time = random.uniform(5, 12)
            logger.info(f"😴 分类间歇休息 {sleep_time:.2f} 秒...")
            time.sleep(sleep_time)
            
    logger.info("=== 任务结束 ===")

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Buff 商品全量抓取脚本")
    parser.add_argument("--force", action="store_true", help="强制抓取所有分类和页码，忽略增量跳过逻辑")
    args = parser.parse_args()

    run_goods_sync(force=args.force)

if __name__ == "__main__":
    from utils.logger import setup_logging
    setup_logging()
    main()
