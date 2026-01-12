import random
import sys
import os
import requests
import time
import json
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field, AliasPath
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type, before_sleep_log

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.models import BuffGoodsCategory, BuffScanTask
from storage.database import Session
from storage.redis_pool import redis_client
from sqlalchemy.dialects.postgresql import insert
from config import settings
from utils.logger import get_logger, setup_logging
from utils.exception_handler import LoginRequiredError
from utils.browser_helper import BrowserHelper
from utils.proxy_helper import get_proxies
from utils.network_util import log_request_ip

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

# --- 常量定义 ---
# Redis 暂存 Key
REDIS_TEMP_CATEGORY_KEY = "niro:spider:temp_categories"

# BUFF 核心一级分类 (Type)
BUFF_PRIMARY_TYPES = [
    {"internal_name": "knife", "name": "匕首"},
    {"internal_name": "pistol", "name": "手枪"},
    {"internal_name": "rifle", "name": "步枪"},
    {"internal_name": "smg", "name": "微型冲锋枪"},
    {"internal_name": "shotgun", "name": "重型武器"},
    {"internal_name": "machinegun", "name": "机枪"},
    {"internal_name": "hands", "name": "手套"},
    {"internal_name": "sticker", "name": "印花"},
    {"internal_name": "graffiti", "name": "涂鸦"},
    {"internal_name": "collectible", "name": "收藏品"},
    {"internal_name": "container", "name": "礼物/箱子"},
    {"internal_name": "asset_tag", "name": "工具"},
    {"internal_name": "type_custom_player", "name": "探员"},
    {"internal_name": "music_kit", "name": "音乐盒"},
    {"internal_name": "pass_ticket", "name": "通行证"},
    {"internal_name": "flair_sticker", "name": "布章"},
    {"internal_name": "unusual_equipment", "name": "装备"},
    {"internal_name": "other", "name": "其它"},
]

# --- Pydantic 模型定义 ---

class BuffGoodsItem(BaseModel):
    tags: Optional[Dict[str, Any]] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags"))

class BuffGoodsData(BaseModel):
    items: List[BuffGoodsItem]
    total_page: int

class BuffGoodsResponse(BaseModel):
    code: str
    data: Optional[BuffGoodsData] = None
    msg: Optional[str] = None

# --- 业务逻辑 ---

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    retry=retry_if_exception_type(requests.exceptions.RequestException),
    reraise=True
)
def fetch_buff_goods_api(params: Dict[str, Any], profile: Any = None) -> BuffGoodsData:
    url = f"{BUFF_HOST}/api/market/goods"
    if not profile or not profile.cookie:
        raise Exception(f"无法获取有效 Profile 或 Cookie")
    headers = profile.get_headers()
    proxies = get_proxies()
    log_request_ip(proxies, prefix="[CategorySync] ")
    response = requests.get(url, headers=headers, params=params, proxies=proxies, timeout=10)
    response.encoding = 'utf-8'
    if response.status_code == 403: raise LoginRequiredError("Buff Login Required (403)")
    response.raise_for_status()
    resp = BuffGoodsResponse.model_validate(response.json())
    if resp.code == "Login Required": raise LoginRequiredError("Buff Login Required")
    if resp.code != "OK" or not resp.data: raise Exception(f"API 业务错误: {resp.msg}")
    return resp.data

def is_task_running(task_id):
    if not task_id: return True
    session = Session()
    try:
        task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()
        return task.status in (1, 4) if task else False
    finally: Session.remove()

def get_task_user_id(task_id):
    if not task_id: return None
    session = Session()
    try:
        task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()
        return task.user_id if task else None
    finally: Session.remove()

def save_categories(categories: List[Dict]):
    if not categories: return
    session = Session()
    try:
        db_items = []
        for cat in categories:
            db_items.append({
                "name": cat["name"],
                "internal_name": cat["internal_name"],
                "category_type": cat.get("category_type", "type"),
                "full_internal_name": cat["full_internal_name"],
                "parent_id": 0
            })
        stmt = insert(BuffGoodsCategory).values(db_items)
        stmt = stmt.on_conflict_do_update(
            index_elements=['internal_name'],
            set_={
                'name': stmt.excluded.name,
                'category_type': stmt.excluded.category_type,
                'full_internal_name': stmt.excluded.full_internal_name
            }
        )
        session.execute(stmt)
        session.commit()

        # 处理 parent_id 关联
        existing_cats = session.query(BuffGoodsCategory.id, BuffGoodsCategory.internal_name).all()
        name_to_id = {c.internal_name: c.id for c in existing_cats}
        for cat in categories:
            if cat.get("parent_internal_name") and cat["parent_internal_name"] in name_to_id:
                p_id = name_to_id[cat["parent_internal_name"]]
                c_id = name_to_id.get(cat["internal_name"])
                if c_id:
                    session.query(BuffGoodsCategory).filter(BuffGoodsCategory.id == c_id).update({"parent_id": p_id})
        session.commit()
    except Exception as e:
        session.rollback()
        logger.error(f"❌ 保存分类失败: {e}")
    finally: Session.remove()

def run_category_sync(task_id=None):
    from utils.logger import get_current_ip_cached
    get_current_ip_cached(force_refresh=True)
    
    user_id = get_task_user_id(task_id)
    profile = BrowserHelper.create_profile(user_id)
    logger.info(f"🎭 已启动精准分类同步，使用指纹: {profile.user_agent}")

    # 清理上次可能残留的 Redis 数据
    redis_client.delete(REDIS_TEMP_CATEGORY_KEY)

    # 1. 首先确保一级分类在数据库中 (作为二级分类的 Parent)
    primary_cats = []
    for p_type in BUFF_PRIMARY_TYPES:
        primary_cats.append({
            "name": p_type["name"],
            "internal_name": p_type["internal_name"],
            "category_type": "type",
            "full_internal_name": f"csgo_{p_type['internal_name']}",
            "parent_internal_name": None
        })
    save_categories(primary_cats)
    logger.info(f"✅ 已同步 {len(BUFF_PRIMARY_TYPES)} 个核心一级分类到数据库")

    # 2. 循环遍历一级分类，深度抓取二级分类并暂存到 Redis
    total_new_categories = 0
    for p_type in BUFF_PRIMARY_TYPES:
        if task_id and not is_task_running(task_id):
            logger.warning("🛑 任务被手动停止")
            break

        type_internal = p_type["internal_name"]
        type_name = p_type["name"]
        logger.info(f"📂 正在抓取一级分类 [{type_name}] 的二级细分...")

        processed_in_type = set()
        for page in range(1, 21): # 每种分类抓取 20 页
            if task_id and not is_task_running(task_id): break
            
            logger.info(f"  -> 正在处理 [{type_name}] 第 {page}/20 页...")
            params = {"game": "csgo", "page_num": page, "tab": "selling", "category": type_internal}
            
            try:
                data = fetch_buff_goods_api(params, profile=profile)
                if not data.items: break
                
                page_categories = []
                for item in data.items:
                    tags = item.tags or {}
                    sub_tag = tags.get("category") or tags.get("weapon")
                    if not sub_tag: continue
                    
                    sub_internal = sub_tag.get("internal_name")
                    if not sub_internal or sub_internal in processed_in_type: continue
                    
                    sub_name = sub_tag.get("localized_name") or sub_tag.get("name")
                    page_categories.append({
                        "name": sub_name,
                        "internal_name": sub_internal,
                        "category_type": sub_tag.get("category", "category"),
                        "full_internal_name": sub_internal if sub_internal.startswith("csgo_") else f"csgo_{sub_internal}",
                        "parent_internal_name": type_internal
                    })
                    processed_in_type.add(sub_internal)

                if page_categories:
                    # 暂存到 Redis
                    for cat in page_categories:
                        redis_client.rpush(REDIS_TEMP_CATEGORY_KEY, json.dumps(cat, ensure_ascii=False))
                    total_new_categories += len(page_categories)
                    logger.info(f"  📥 本页发现 {len(page_categories)} 个新二级分类，已暂存至 Redis (当前累计: {total_new_categories})")

                if data.total_page < page: break
                time.sleep(random.uniform(8, 12))
            except Exception as e:
                logger.error(f"  ❌ 抓取出错: {e}")
                break
        
        logger.info(f"✅ 一级分类 [{type_name}] 扫描完毕")
        time.sleep(random.uniform(10, 15))

    # 3. 全部抓取完成后，从 Redis 读取并统一保存到 PostgreSQL
    if total_new_categories > 0:
        logger.info(f"🚀 抓取任务完成，正在从 Redis 读取 {total_new_categories} 条数据并保存到数据库...")
        all_temp_data = redis_client.lrange(REDIS_TEMP_CATEGORY_KEY, 0, -1)
        all_categories = [json.loads(d) for d in all_temp_data]
        
        save_categories(all_categories)
        logger.info(f"✅ 成功将 {len(all_categories)} 条二级分类数据保存到数据库")
        
        # 保存完成后清理 Redis
        redis_client.delete(REDIS_TEMP_CATEGORY_KEY)
    else:
        logger.info("ℹ️ 未发现新的二级分类，无需更新数据库")

    logger.info("🎉 全量精准分类同步任务圆满完成！")

if __name__ == "__main__":
    setup_logging()
    run_category_sync()
