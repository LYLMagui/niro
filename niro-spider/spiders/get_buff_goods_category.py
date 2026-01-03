import jmespath
import random
import sys
import os
import requests
import time
import psycopg2.extras

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

def fetch_buff_goods(params, max_retries=3):
    """
    通用请求 Buff 饰品列表接口 (带重试机制)
    """
    url = f"{BUFF_HOST}/api/market/goods"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0",
        "cookie": settings.BUFF_COOKIE,
        "accept": "application/json, text/javascript, */*; q=0.01",
        "accept-language": "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
        "cache-control": "no-cache",
        "pragma": "no-cache",
        "priority": "u=1, i",
        "referer": "https://buff.163.com/market/csgo",
        "sec-ch-ua": '"Microsoft Edge";v="143", "Chromium";v="143", "Not A(Brand";v="24"',
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": '"Windows"',
        "sec-fetch-dest": "empty",
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin",
        "x-requested-with": "XMLHttpRequest",
    }
    
    for attempt in range(max_retries):
        try:
            # 构造完整URL仅用于日志打印，不影响 requests.get 调用
            full_url = requests.Request('GET', url, params=params).prepare().url
            logger.info(f"🌐 请求 URL: {full_url}")
            
            response = requests.get(url, headers=headers, params=params, timeout=10)
            response.raise_for_status()
            
            json_data = response.json()
            if not json_data or json_data.get("data") is None:
                # 业务级错误也视为失败，但不一定重试（视情况而定），这里简单抛出
                raise Exception(f"请求成功但无数据: {json_data}")
                
            return json_data["data"]
            
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


def get_buff_goods_parent_category(max_pages=15):
    """
    获取一级分类 (Type)
    params: game=csgo, tab=selling, page_num=1..50
    """
    logger.info("开始抓取一级分类...")
    all_categories = []
    
    for page in range(1, max_pages + 1):
        params = {
            "game": "csgo",
            "page_num": page,
            "tab": "selling",
        }
        
        logger.info(f"正在抓取一级分类第 {page} 页...")
        data = fetch_buff_goods(params)
        
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
        time.sleep(random.uniform(2, 4))

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

def get_buff_goods_children_category(parent_id, category_group, parent_full_internal_name, max_pages=20):
    """
    获取二级分类 (Category)
    params: game=csgo, tab=selling, category_group=..., page_num=1..20
    """
    logger.info(f"开始抓取 [{category_group}] 下的二级分类...")
    all_categories = []
    
    for page in range(1, max_pages + 1):
        params = {
            "game": "csgo",
            "page_num": page,
            "category_group": category_group,
            "tab": "selling",
        }
        
        logger.info(f"正在抓取二级分类 [{category_group}] 第 {page} 页...")
        data = fetch_buff_goods(params)
        
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
            
        time.sleep(random.uniform(2, 4)) # 增加间隔避免触发限流

    # 去重
    dedup_map = {c["internal_name"]: c for c in all_categories if c.get("internal_name")}
    result = list(dedup_map.values())
    logger.info(f"二级分类 [{category_group}] 抓取完成，去重后数量: {len(result)}")
    return result

def sync_categories(total_categories):
    """
    同步分类保存到数据库 (批量插入版)
    """
    if not total_categories:
        return

    success_count = 0
    try:
        # 1. 预处理数据 & 冲突检测
        logger.info("正在预处理数据...")
        
        # 提取所有 name 用于批量查询冲突
        names_to_check = [c.get("name", "") for c in total_categories if c.get("name")]
        
        existing_map = {} # name -> {id, internal_name}
        if names_to_check:
            with pg_pool.get_cursor() as cur:
                # 批量查询已存在的 name
                cur.execute(
                    "SELECT id, internal_name, name FROM buff_goods_categories WHERE name = ANY(%s)",
                    (names_to_check,)
                )
                rows = cur.fetchall()
                for row in rows:
                    existing_map[row['name']] = row

        insert_data = []      # 待批量插入的数据
        conflict_updates = [] # 待更新 ID 的冲突数据 (name 相同但 internal_name 不同)

        for category in total_categories:
            internal_name = category.get("internal_name")
            name = category.get("name", "")
            full_internal_name = category.get("full_internal_name", "")
            parent_id = category.get("parent_id")
            
            if not internal_name:
                continue

            # 检查名称冲突
            if name in existing_map:
                existing_record = existing_map[name]
                if existing_record['internal_name'] != internal_name:
                    # 发生冲突：旧记录占用了这个名字，且 internal_name 不同
                    # 策略：强制更新旧记录的 ID
                    logger.warning(f"⚠️ 名称冲突: '{name}' 已被 ID={existing_record['id']} 占用，加入更新队列")
                    conflict_updates.append({
                        "id": existing_record['id'],
                        "name": name,
                        "internal_name": internal_name,
                        "full_internal_name": full_internal_name,
                        "parent_id": parent_id
                    })
                    continue # 跳过插入列表，走更新流程

            # 加入待插入列表
            insert_data.append((
                name,
                parent_id,
                internal_name,
                full_internal_name
            ))

        # 2. 执行数据库操作
        with pg_pool.get_cursor() as cur:
            # 2.1 批量插入 (UPSERT)
            if insert_data:
                logger.info(f"正在批量插入/更新 {len(insert_data)} 条记录...")
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
                success_count += len(insert_data)

            # 2.2 处理冲突更新 (UPDATE by ID)
            if conflict_updates:
                logger.info(f"正在处理 {len(conflict_updates)} 条ID冲突记录...")
                update_sql = """
                    UPDATE buff_goods_categories 
                    SET name = %s, internal_name = %s, full_internal_name = %s, parent_id = %s
                    WHERE id = %s
                """
                # 使用 execute_batch 优化 UPDATE
                update_params = [
                    (item['name'], item['internal_name'], item['full_internal_name'], item['parent_id'], item['id']) 
                    for item in conflict_updates
                ]
                psycopg2.extras.execute_batch(cur, update_sql, update_params)
                success_count += len(conflict_updates)

        logger.info(f"✅ 分类保存完成，共处理 {success_count} 条记录")

    except Exception as e:
        logger.error(f"❌ 分类同步失败: {e}")
        raise e

def sync_all_children_categories():
    """
    主流程：获取所有一级分类 -> 循环抓取对应的二级分类 -> 入库
    """
    parents = get_parent_categories_from_db()
    if not parents:
        logger.warning("未找到一级分类，请先执行一级分类抓取")
        return

    for p in parents:
        parent_id = p['id']
        internal_name = p['internal_name']
        name = p['name']
        full_internal_name = p['full_internal_name']
        
        logger.info(f"🚀 开始处理一级分类: {name} ({internal_name}) [ID={parent_id}]")
        
        # 抓取该组下的二级分类
        children = get_buff_goods_children_category(parent_id, internal_name, full_internal_name, max_pages=20)
        
        if children:
            # 入库 (复用 sync_categories)
            sync_categories(children)
        
        # 组间延时
        sleep_time = random.uniform(5, 8)
        logger.info(f"� 组间休息 {sleep_time:.2f} 秒...")
        time.sleep(sleep_time)

def run_category_sync():
    """
    暴露给外部调用的分类同步主入口
    """
    logger.info("开始执行全量分类同步任务...")
    # 1. 抓取一级分类 (Parent)
    logger.info("=== 阶段1: 抓取一级分类 ===")
    parent_cats = get_buff_goods_parent_category(max_pages=15)
    sync_categories(parent_cats)

    # 2. 抓取二级分类 (Children)
    logger.info("=== 阶段2: 抓取二级分类 ===")
    sync_all_children_categories()
    logger.info("全量分类同步任务完成")

if __name__ == "__main__":
    from utils.logger import setup_logging
    
    setup_logging()
    run_category_sync()
