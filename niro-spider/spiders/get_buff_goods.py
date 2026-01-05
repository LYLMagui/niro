
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
from utils.exception_handler import LoginRequiredError
from utils.cookie_util import get_latest_cookie, verify_cookie

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

def fetch_goods_data(category_internal_name, page_num=1, max_retries=3, user_id=None):
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
    
    # 动态获取最新的 Cookie
    current_cookie = get_latest_cookie(user_id)
    if not current_cookie:
        logger.error(f"❌ 无法获取有效 Cookie (user_id: {user_id})")
        return None

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "cookie": current_cookie,
        "accept": "application/json, text/javascript, */*; q=0.01",
        "accept-language": "zh-CN,zh;q=0.9,en;q=0.8",
        "cache-control": "no-cache",
        "pragma": "no-cache",
        "priority": "u=1, i",
        "referer": "https://buff.163.com/market/csgo",
        "sec-ch-ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": '"Windows"',
        "sec-fetch-dest": "empty",
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin",
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

            if response.status_code == 403:
                logger.error("🔑 HTTP 403 Forbidden: Cookie 已失效或 IP 被封禁")
                raise LoginRequiredError("Buff Login Required (403)")

            response.raise_for_status()
            
            json_data = response.json()
            if json_data.get("code") != "OK":
                err_msg = json_data.get("error", "Unknown Error")
                
                if json_data.get("code") == "Login Required":
                     logger.error("🔑 API Code: Login Required. Cookie 已失效")
                     raise LoginRequiredError("Buff Login Required")
                
                logger.error(f"❌ API 返回错误: {json_data.get('code')} - {err_msg}")
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

def is_task_running(task_id):
    """检查任务是否仍处于活跃状态"""
    if not task_id:
        return True
    try:
        # 使用默认 cursor_factory=None 获取元组格式
        with pg_pool.get_cursor(cursor_factory=None) as cur:
            sql = "SELECT status FROM buff_scan_task WHERE id = %s"
            cur.execute(sql, (task_id,))
            res = cur.fetchone()
            # 1: 运行中(Active), 4: 执行中(Running)
            return res[0] in (1, 4) if res else False
    except Exception as e:
        logger.error(f"❌ 检查任务状态失败 [ID:{task_id}]: {e}")
        return True # 失败时默认继续

def get_task_user_id(task_id):
    """获取任务所属的用户ID"""
    if not task_id:
        return None
    try:
        with pg_pool.get_cursor() as cur:
            sql = "SELECT user_id FROM buff_scan_task WHERE id = %s"
            cur.execute(sql, (task_id,))
            res = cur.fetchone()
            return res.get('user_id') if res else None
    except Exception as e:
        logger.error(f"❌ 获取任务用户ID失败 [ID:{task_id}]: {e}")
        return None

def process_category(category, force=False, task_id=None, user_id=None):
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
    
    # 获取第一页看总数
    first_page_data = fetch_goods_data(cat_internal, 1, user_id=user_id)
    if not first_page_data:
        return
        
    total_count = first_page_data.get("total_count", 0)
    total_pages = first_page_data.get("total_page", 0)
    
    logger.info(f"   Buff 总数: {total_count}, 数据库总数: {db_count}")
    logger.info(f"   📊 该分类共 {total_pages} 页，本次最多同步 20 页")
    
    # 增量跳过逻辑
    if not force and db_count >= total_count and total_count > 0:
        logger.info(f"   ⏩ 分类 {cat_name} 数量匹配，跳过")
        return

    page = 1
    total_saved = 0
    seen_goods_ids = set() # 本次同步中已见过的 ID，防止同分类重复
    
    while page <= total_pages and page <= 20:
        # 每一页抓取前都检查一下任务是否被手动停止
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已被手动停止，退出分类处理")
            return "STOPPED"

        logger.info(f"   正在抓取第 {page}/{total_pages} 页...")
        
        data = fetch_goods_data(cat_internal, page, user_id=user_id) if page > 1 else first_page_data
        if not data:
            logger.warning(f"   第 {page} 页获取失败，跳过")
            page += 1
            continue
            
        items = data.get("items", [])
        if not items:
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
        wait_time = random.uniform(10, 15)
        logger.info(f"   😴 页面间歇休息 {wait_time:.2f} 秒...")
        time.sleep(wait_time)
        
    logger.info(f"✨ 分类 {cat_name} 处理完成，本次任务影响 {total_saved} 个商品")

def run_goods_sync(force=False, task_id=None, user_id=None):
    """
    暴露给外部调用的商品同步主入口
    """
    logger.info(f"=== 开始抓取 Buff 商品数据 (Force Mode: {force}, TaskID: {task_id}, UserID: {user_id}) ===")
    
    # 如果没传 user_id 但传了 task_id，则从任务中获取
    if not user_id and task_id:
        user_id = get_task_user_id(task_id)
        logger.info(f"👤 从任务 [ID:{task_id}] 中获取到所属用户 ID: {user_id}")
    
    # 如果还是没有 user_id，get_latest_cookie(None) 会尝试获取数据库中最新的一条
    if not user_id:
        logger.info("ℹ️ 未指定用户 ID，将尝试从数据库加载最新更新的 Cookie")
    
    # 运行前预检查 Cookie 有效性
    test_cookie = get_latest_cookie(user_id)
    is_valid, msg = verify_cookie(test_cookie)
    if not is_valid:
        logger.error(f"❌ Cookie 预检查失败: {msg}")
        if "403" in msg:
            raise LoginRequiredError(f"Cookie 预检查失败 (403): {msg}")
        raise LoginRequiredError(f"Cookie 预检查失败: {msg}")
    
    logger.info("✅ Cookie 预检查通过，开始同步...")

    categories = get_secondary_categories()
    if not categories:
        logger.warning("未找到分类数据")
        return
        
    for i, cat in enumerate(categories):
        # 检查任务状态
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已被手动停止，终止同步流程")
            break

        res = process_category(cat, force=force, task_id=task_id, user_id=user_id)
        if res == "STOPPED":
            break
        
        if i < len(categories) - 1:
            sleep_time = random.uniform(5, 12)
            logger.info(f"😴 分类间歇休息 {sleep_time:.2f} 秒...")
            time.sleep(sleep_time)
            
    logger.info("=== 任务结束 ===")

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Buff 商品全量抓取脚本")
    parser.add_argument("--force", action="store_true", help="强制抓取所有分类和页码，忽略增量跳过逻辑")
    parser.add_argument("--user_id", type=int, help="指定使用的用户 ID 加载 Cookie")
    parser.add_argument("--task_id", type=int, help="关联的任务 ID")
    args = parser.parse_args()

    run_goods_sync(force=args.force, task_id=args.task_id, user_id=args.user_id)

if __name__ == "__main__":
    from utils.logger import setup_logging
    setup_logging()
    main()
