import jmespath
import random
import sys
import os
import requests
import time
import psycopg2.extras
import hashlib
import json

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.postgres_pool import pg_pool
from storage.redis_pool import redis_client
from config import settings
from utils.logger import get_logger
from utils.exception_handler import LoginRequiredError
from utils.cookie_util import get_latest_cookie, verify_cookie
from utils.proxy_helper import get_proxies

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

def fetch_buff_goods(params, max_retries=3, user_id=None):
    """
    通用请求 Buff 饰品列表接口 (带重试机制)
    """
    url = f"{BUFF_HOST}/api/market/goods"
    
    # 动态获取最新的 Cookie
    current_cookie = get_latest_cookie(user_id)
    if not current_cookie:
        logger.error(f"❌ 无法获取有效 Cookie (user_id: {user_id})")
        return {}
    
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
    
    # 获取代理
    proxies = get_proxies()
    if proxies:
        logger.debug(f"🛰️ 使用代理: {proxies.get('http')}")

    for attempt in range(max_retries):
        try:
            # 构造完整URL仅用于日志打印，不影响 requests.get 调用
            full_url = requests.Request('GET', url, params=params).prepare().url
            logger.info(f"🌐 请求 URL: {full_url}")
            
            response = requests.get(url, headers=headers, params=params, proxies=proxies, timeout=10)
            
            if response.status_code == 403:
                logger.error("🔑 HTTP 403 Forbidden: Cookie 已失效或 IP 被封禁")
                raise LoginRequiredError("Buff Login Required (403)")

            response.raise_for_status()
            
            json_data = response.json()
            if json_data.get("code") == "Login Required":
                logger.error("🔑 API Code: Login Required. Cookie 已失效")
                raise LoginRequiredError("Buff Login Required")
                
            if not json_data or json_data.get("data") is None:
                # 业务级错误也视为失败，但不一定重试（视情况而定），这里简单抛出
                raise Exception(f"请求成功但无数据: {json_data}")
                
            return json_data["data"]
            
        except LoginRequiredError:
            raise
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
            break
            
    return {}


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

def get_buff_goods_parent_category(task_id=None, user_id=None):
    """
    获取一级分类 (Type)
    params: game=csgo, tab=selling, page_num=1..50
    """
    logger.info("开始抓取一级分类...")
    all_categories = []
    
    page = 1
    total_pages = 1 # 初始设为 1，第一次请求后更新
    
    while page <= total_pages:
        # 每一页抓取前都检查一下任务是否被手动停止
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已被手动停止，退出一级分类抓取")
            return "STOPPED"

        params = {
            "game": "csgo",
            "page_num": page,
            "tab": "selling",
        }
        
        logger.info(f"正在抓取一级分类第 {page}/{total_pages} 页...")
        data = fetch_buff_goods(params, user_id=user_id)
        
        if not data:
            logger.warning(f"⚠️ 第 {page} 页获取失败，跳过")
            page += 1
            continue

        # 更新总页数
        if page == 1:
            total_pages = data.get("total_page", 1)
            logger.info(f"📊 检测到一级分类共 {total_pages} 页")

        # 提取一级分类: items[*].goods_info.info.tags.type
        # 结果可能包含 None，需过滤
        types = jmespath.search("items[*].goods_info.info.tags.type", data) or []
        
        for item in types:
            if not item:
                continue
            
            full_internal_name = item.get("internal_name", "")
            # 截取最后一部分作为 short name (例如 csgo_type_rifle -> rifle)
            internal_name = full_internal_name.split('_')[-1] if full_internal_name else ""
            
            cat = {
                "parent_id": 0,
                "name": item.get("localized_name", ""),
                "internal_name": internal_name,
                "full_internal_name": full_internal_name,
            }
            all_categories.append(cat)
            
        # 随机延时
        time.sleep(random.uniform(2, 6))
        page += 1

    # 去重
    dedup_map = {c["internal_name"]: c for c in all_categories if c.get("internal_name")}
    result = list(dedup_map.values())
    logger.info(f"一级分类抓取完成，去重后数量: {len(result)}")
    return result


def get_parent_categories_from_db():
    """
    从数据库获取所有一级分类 (parent_id=0)
    返回: [{"id": 1, "internal_name": "knife", ...}, ...]
    """
    logger.info("正在从数据库获取一级分类...")
    try:
        with pg_pool.get_cursor() as cur:
            sql = "SELECT id, internal_name, name, full_internal_name FROM buff_goods_categories WHERE parent_id = 0"
            cur.execute(sql)
            rows = cur.fetchall()
            logger.info(f"✅ 获取到 {len(rows)} 个一级分类")
            return rows
    except Exception as e:
        logger.error(f"❌ 获取一级分类失败: {e}")
        return []

def get_buff_goods_children_category(parent_id, category_group, parent_full_internal_name, task_id=None, user_id=None):
    """
    获取二级分类 (Category)
    params: game=csgo, tab=selling, category_group=..., page_num=1..20
    """
    logger.info(f"开始抓取 [{category_group}] 下的二级分类...")
    all_categories = []
    
    page = 1
    total_pages = 1
    
    while page <= total_pages:
        # 每一页抓取前都检查一下任务是否被手动停止
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已被手动停止，退出二级分类抓取")
            return "STOPPED"

        params = {
            "game": "csgo",
            "page_num": page,
            "category_group": category_group,
            "tab": "selling",
        }
        
        logger.info(f"正在抓取二级分类 [{category_group}] 第 {page}/{total_pages} 页...")
        data = fetch_buff_goods(params, user_id=user_id)
        
        if not data:
            logger.warning(f"⚠️ 第 {page} 页获取失败，跳过")
            page += 1
            continue

        # 更新总页数
        if page == 1:
            total_pages = data.get("total_page", 1)
            logger.info(f"📊 检测到二级分类 [{category_group}] 共 {total_pages} 页")

        # 提取商品列表
        items = data.get("items", [])
        if not items:
            logger.info(f"⚠️ [{category_group}] 第 {page} 页无商品数据，停止该组抓取")
            break

        for item in items:
            goods_info = item.get("goods_info", {})
            info = goods_info.get("info", {})
            tags = info.get("tags", {})
            
            # 1. 校验一级分类是否匹配 (防止混入其他分类的商品)
            # 使用完整的 internal_name (如 csgo_type_rifle) 进行精确匹配
            
            type_tag = tags.get("type", {})
            type_internal = type_tag.get("internal_name", "")
            
            if type_internal != parent_full_internal_name:
                # logger.debug(f"跳过不匹配商品: {goods_info.get('original_icon_url')} (Type: {type_internal} != {parent_full_internal_name})")
                continue

            # 2. 提取二级分类
            cat_tag = tags.get("category", {})
            if not cat_tag:
                continue
            
            internal_name = cat_tag.get("internal_name")
            cat = {
                "parent_id": parent_id, 
                "name": cat_tag.get("localized_name", ""),
                "internal_name": internal_name,
                "full_internal_name": internal_name, # 二级分类两者相同
            }
            all_categories.append(cat)
            
        time.sleep(random.uniform(2, 6)) # 增加间隔避免触发限流
        page += 1

    # 去重
    dedup_map = {c["internal_name"]: c for c in all_categories if c.get("internal_name")}
    result = list(dedup_map.values())
    logger.info(f"二级分类 [{category_group}] 抓取完成，去重后数量: {len(result)}")
    return result

def get_categories_fingerprint(categories):
    """
    计算分类数据的指纹 (MD5)
    确保排序以保证结果唯一性
    """
    if not categories:
        return ""
    # 按照 internal_name 排序并转换为稳定的 JSON 字符串
    sorted_cats = sorted(categories, key=lambda x: x.get("internal_name", ""))
    cat_str = json.dumps(sorted_cats, sort_keys=True, ensure_ascii=False)
    return hashlib.md5(cat_str.encode('utf-8')).hexdigest()

def sync_categories(total_categories, parent_id_filter=None):
    """
    同步分类保存到数据库 (Redis 指纹过滤 + 内存比对版)
    :param total_categories: 抓取到的分类列表
    :param parent_id_filter: 如果提供，则只对比数据库中该父分类下的数据
    """
    if not total_categories:
        return

    # 1. Redis 指纹校验 (全量跳过逻辑)
    fingerprint_key = f"niro:spider:category:fingerprint:{parent_id_filter if parent_id_filter is not None else 'all'}"
    new_fingerprint = get_categories_fingerprint(total_categories)
    
    if redis_client:
        try:
            old_fingerprint = redis_client.get(fingerprint_key)
            if old_fingerprint == new_fingerprint:
                logger.info(f"✨ [Redis] 分类指纹未变化，跳过数据库同步 (ParentID: {parent_id_filter})")
                return
        except Exception as e:
            logger.warning(f"⚠️ Redis 指纹校验失败: {e}")

    success_count = 0
    try:
        # 2. 内存比对 & 增量同步
        logger.info("正在进行内存比对以执行增量更新...")
        
        # 2.1 获取数据库中现有的分类
        existing_db_map = {} # internal_name -> {name, parent_id, full_internal_name}
        with pg_pool.get_cursor() as cur:
            if parent_id_filter is not None:
                cur.execute(
                    "SELECT internal_name, name, parent_id, full_internal_name FROM buff_goods_categories WHERE parent_id = %s",
                    (parent_id_filter,)
                )
            else:
                cur.execute("SELECT internal_name, name, parent_id, full_internal_name FROM buff_goods_categories")
            
            rows = cur.fetchall()
            for row in rows:
                existing_db_map[row['internal_name']] = {
                    "name": row['name'],
                    "parent_id": row['parent_id'],
                    "full_internal_name": row['full_internal_name']
                }

        insert_data = []      # 真正需要插入/更新的数据
        
        for category in total_categories:
            internal_name = category.get("internal_name")
            name = category.get("name", "")
            full_internal_name = category.get("full_internal_name", "")
            parent_id = category.get("parent_id")
            
            if not internal_name:
                continue

            # 比对字段是否发生变化
            is_changed = True
            if internal_name in existing_db_map:
                old = existing_db_map[internal_name]
                if (old['name'] == name and 
                    old['parent_id'] == parent_id and 
                    old['full_internal_name'] == full_internal_name):
                    is_changed = False
            
            if is_changed:
                insert_data.append((name, parent_id, internal_name, full_internal_name))

        if not insert_data:
            logger.info(f"✅ 所有分类数据已是最新，无需更新数据库 (ParentID: {parent_id_filter})")
            # 即使没写 DB，也更新 Redis 指纹
            if redis_client:
                redis_client.set(fingerprint_key, new_fingerprint, ex=86400*7)
            return

        # 3. 执行数据库操作
        with pg_pool.get_cursor() as cur:
            logger.info(f"正在批量更新 {len(insert_data)} 条变动记录...")
            sql = """
                INSERT INTO buff_goods_categories (name, parent_id, internal_name, full_internal_name)
                VALUES %s
                ON CONFLICT (internal_name)
                DO UPDATE SET
                    name = EXCLUDED.name,
                    parent_id = EXCLUDED.parent_id,
                    internal_name = EXCLUDED.internal_name,
                    full_internal_name = EXCLUDED.full_internal_name
            """
            psycopg2.extras.execute_values(cur, sql, insert_data)
            success_count = len(insert_data)

        # 4. 同步成功后更新 Redis 指纹
        if redis_client:
            try:
                redis_client.set(fingerprint_key, new_fingerprint, ex=86400*7) # 缓存一周
                logger.debug(f"📝 已更新 Redis 分类指纹: {new_fingerprint}")
            except Exception as e:
                logger.error(f"❌ 更新 Redis 指纹失败: {e}")

        logger.info(f"✅ 分类增量同步完成，共处理 {success_count} 条变动记录")

    except Exception as e:
        logger.error(f"❌ 分类同步失败: {e}")
        raise e

def sync_all_children_categories(task_id=None, user_id=None):
    """
    主流程：获取所有一级分类 -> 循环抓取对应的二级分类 -> 入库
    """
    parents = get_parent_categories_from_db()
    if not parents:
        logger.warning("未找到一级分类，请先执行一级分类抓取")
        return

    for p in parents:
        # 检查任务状态
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已被手动停止，终止二级分类抓取流程")
            break

        parent_id = p['id']
        internal_name = p['internal_name']
        name = p['name']
        full_internal_name = p['full_internal_name']
        
        logger.info(f"🚀 开始处理一级分类: {name} ({internal_name}) [ID={parent_id}]")
        
        # 抓取该组下的二级分类
        children = get_buff_goods_children_category(parent_id, internal_name, full_internal_name, task_id=task_id, user_id=user_id)
        
        if children == "STOPPED":
            break

        if children:
            # 入库 (复用 sync_categories)
            sync_categories(children, parent_id_filter=parent_id)
        
        # 组间延时
        sleep_time = random.uniform(5, 8)
        logger.info(f" 组间休息 {sleep_time:.2f} 秒...")
        time.sleep(sleep_time)

def run_category_sync(task_id=None, user_id=None):
    """
    暴露给外部调用的分类同步主入口
    """
    logger.info(f"=== 开始同步 Buff 分类数据 (TaskID: {task_id}, UserID: {user_id}) ===")
    
    # 如果没传 user_id 但传了 task_id，则从任务中获取
    if not user_id and task_id:
        user_id = get_task_user_id(task_id)
        logger.info(f"👤 从任务 [ID:{task_id}] 中获取到所属用户 ID: {user_id}")

    # 运行前预检查 Cookie 有效性
    test_cookie = get_latest_cookie(user_id)
    is_valid, msg = verify_cookie(test_cookie)
    if not is_valid:
        logger.error(f"❌ Cookie 预检查失败: {msg}")
        raise LoginRequiredError(f"Cookie 预检查失败: {msg}")
    
    logger.info("✅ Cookie 预检查通过，开始同步...")

    # 1. 抓取一级分类 (Parent)
    logger.info("=== 阶段1: 抓取一级分类 ===")
    parent_cats = get_buff_goods_parent_category(task_id=task_id, user_id=user_id)
    if parent_cats == "STOPPED":
        return
        
    sync_categories(parent_cats, parent_id_filter=0)

    # 2. 抓取二级分类 (Children)
    logger.info("=== 阶段2: 抓取二级分类 ===")
    sync_all_children_categories(task_id=task_id, user_id=user_id)
    logger.info("🎉 全量分类同步任务完成！")

if __name__ == "__main__":
    from utils.logger import setup_logging
    
    setup_logging()
    run_category_sync()
