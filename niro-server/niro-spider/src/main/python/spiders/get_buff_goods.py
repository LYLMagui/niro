
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
            response.raise_for_status()
            
            json_data = response.json()
            if json_data.get("code") != "OK":
                 # 可能是登录过期或参数错误
                logger.error(f"❌ API 返回错误: {json_data.get('code')} - {json_data.get('error')}")
                if json_data.get("code") == "Login Required":
                     raise Exception("Cookie 失效，需要重新登录")
                return None

            data = json_data.get("data")
            if data is None:
                raise Exception(f"请求成功但无数据: {json_data}")
                
            return data
            
        except requests.exceptions.HTTPError as e:
            if response.status_code == 429:
                wait_time = random.uniform(5, 10) * (attempt + 1)
                logger.warning(f"⚠️ 触发限流 (429)，暂停 {wait_time:.2f} 秒后重试 ({attempt + 1}/{max_retries})...")
                time.sleep(wait_time)
                continue
            logger.error(f"❌ HTTP 请求失败: {e}")
            break
        except Exception as e:
            logger.error(f"❌ 请求异常: {e}")
            time.sleep(random.uniform(2, 5))
            
    return None

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

def process_category(category):
    """
    处理单个分类：分页抓取所有商品
    """
    cat_id = category['id']
    cat_internal = category['internal_name']
    cat_name = category['name']
    
    logger.info(f"🚀 开始抓取分类: {cat_name} ({cat_internal}) [ID={cat_id}]")
    
    page = 1
    total_pages = 1 # 初始值，第一次请求后更新
    total_saved = 0
    seen_goods_ids = set() # 本次任务内存去重，防止翻页数据漂移
    
    while page <= total_pages:
        logger.info(f"   正在抓取第 {page}/{total_pages} 页...")
        
        data = fetch_goods_data(cat_internal, page_num=page)
        if not data:
            logger.warning(f"   第 {page} 页获取失败或无数据，跳过本页")
            page += 1
            continue
            
        # 更新总页数
        if page == 1:
            total_pages = data.get("total_page", 1)
            total_count = data.get("total_count", 0)
            logger.info(f"   📊 该分类共有 {total_count} 个商品，共 {total_pages} 页")
            
        items = data.get("items", [])
        if not items:
            logger.info("本页无商品数据")
            break
            
        # 解析数据
        parsed_goods = []
        for item in items:
            try:
                goods_info = item.get("goods_info", {})
                info_tags = goods_info.get("info", {}).get("tags", {})
                
                # 提取字段
                goods_id = item.get("id")
                
                # 内存去重：如果本次任务已经处理过该ID，直接跳过
                if goods_id in seen_goods_ids:
                    continue
                seen_goods_ids.add(goods_id)

                name = item.get("name", "")
                short_name = item.get("short_name", "")
                market_hash_name = item.get("market_hash_name", "")
                
                # 提取内部名称 (优先取 weapon tag 的 internal_name)
                weapon_tag = info_tags.get("weapon", {})
                internal_name = weapon_tag.get("internal_name", "")
                if not internal_name:
                    # 尝试从 category tag 获取
                    internal_name = info_tags.get("category", {}).get("internal_name", "")
                
                # 提取稀有度 (存 internal_name)
                rarity_tag = info_tags.get("rarity", {})
                rarity = rarity_tag.get("internal_name", "")
                
                # 提取外观 (存 internal_name，如 "wearcategory2")
                exterior_tag = info_tags.get("exterior", {})
                exterior = exterior_tag.get("internal_name", "") # 改存 internal_name
                if not exterior:
                    # 如果没有磨损（如印花、箱子），存空字符串或 None
                    exterior = ""
                
                icon_url = goods_info.get("icon_url", "")
                original_icon_url = goods_info.get("original_icon_url", "")
                
                parsed_goods.append({
                    "goods_id": goods_id,
                    "name": name,
                    "short_name": short_name,
                    "internal_name": internal_name,
                    "category_id": cat_id,
                    "rarity": rarity,
                    "exterior": exterior,
                    "market_hash_name": market_hash_name,
                    "icon_url": icon_url,
                    "original_icon_url": original_icon_url,
                    "tags": info_tags  # 保存完整 tags 数据
                })
            except Exception as e:
                logger.error(f"解析商品出错: {item.get('id')} - {e}")
                continue
        
        # 批量入库
        count = save_goods_batch(parsed_goods)
        total_saved += count
        logger.info(f"   ✅ 已保存 {count} 条商品数据")
        
        # 翻页延时
        page += 1
        time.sleep(random.uniform(2, 4))
        
    logger.info(f"✨ 分类 {cat_name} 抓取完成，累计新增/更新 {total_saved} 个商品")

def main():
    logger.info("=== 开始全量抓取 Buff 商品数据 ===")
    
    # 1. 获取所有二级分类
    categories = get_secondary_categories()
    if not categories:
        logger.warning("未找到任何二级分类，请先运行 get_buff_goods_category.py 同步分类表")
        return
        
    # 2. 循环处理每个分类
    for i, cat in enumerate(categories):
        process_category(cat)
        
        # 组间长延时，避免封 IP
        if i < len(categories) - 1:
            sleep_time = random.uniform(5, 10)
            logger.info(f"😴 分类间歇休息 {sleep_time:.2f} 秒...")
            time.sleep(sleep_time)
            
    logger.info("=== 全量抓取任务结束 ===")

if __name__ == "__main__":
    from utils.logger import setup_logging
    setup_logging()
    main()
