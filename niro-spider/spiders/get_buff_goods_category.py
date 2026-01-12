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
# 注意：由于数据库中 internal_name 与官方 API 传参不一致，此处采用硬编码确保请求成功
BUFF_PRIMARY_TYPES = [
    {"internal_name": "knife", "name": "匕首", "param": "category_group"},
    {"internal_name": "pistol", "name": "手枪", "param": "category_group"},
    {"internal_name": "rifle", "name": "步枪", "param": "category_group"},
    {"internal_name": "smg", "name": "微型冲锋枪", "param": "category_group"},
    {"internal_name": "shotgun", "name": "重型武器", "param": "category_group"},
    {"internal_name": "machinegun", "name": "机枪", "param": "category_group"},
    {"internal_name": "hands", "name": "手套", "param": "category_group"},
    {"internal_name": "sticker", "name": "印花", "param": "category_group"},
    {"internal_name": "graffiti", "name": "涂鸦", "param": "category_group"},
    {"internal_name": "collectible", "name": "收藏品", "param": "category_group"},
    {"internal_name": "csgo_type_weaponcase", "name": "武器箱", "param": "category"},
    {"internal_name": "asset_tag", "name": "工具", "param": "category_group"},
    {"internal_name": "type_custom_player", "name": "探员", "param": "category_group"},
    {"internal_name": "csgo_type_musickit", "name": "音乐盒", "param": "category"},
    {"internal_name": "pass_ticket", "name": "通行证", "param": "category_group"},
    {"internal_name": "flair_sticker", "name": "布章", "param": "category_group"},
    {"internal_name": "unusual_equipment", "name": "装备", "param": "category_group"},
    {"internal_name": "other", "name": "其它", "param": "category_group"},
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
            param_key = p_type.get("param", "category_group")
            params = {"game": "csgo", "page_num": page, "tab": "selling", param_key: type_internal}
            
            try:
                data = fetch_buff_goods_api(params, profile=profile)
                if not data.items: break
                
                page_categories = []
                for item in data.items:
                    tags = item.tags or {}
                    
                    # 1. 确定真实的父级分类 (Type)
                    parent_tag = tags.get("type")
                    if not parent_tag: continue
                    
                    real_parent_internal = parent_tag.get("internal_name")
                    
                    # 关键修复：支持多种前缀的匹配 (如 knife 匹配 csgo_type_knife)
                    # 这里的逻辑是：去掉 csgo_type_ 或 type_ 前缀，再去掉下划线后进行匹配
                    def normalize(name):
                        return name.replace("csgo_type_", "").replace("type_", "").replace("csgo_", "").replace("_", "")

                    if normalize(real_parent_internal) != normalize(type_internal):
                        continue
                    
                    # 确定二级分类 (优先取 category，若无则取 weapon)
                    # 关键修复：如果当前一级分类本身就是通过 category 参数请求的（如音乐盒），
                    # 那么二级分类不能再取 category 标签，否则会和父类冲突
                    # 此时应该尝试取更细分的标签，或者直接跳过（因为音乐盒本身没有细分二级分类）
                    sub_tag = None
                    if p_type.get("param") == "category":
                        # 对于武器箱、音乐盒，其本身就是最细分类，通常没有二级分类
                        # 此时 processed_in_type 会记录 sub_internal == real_parent_internal 并跳过
                        sub_tag = tags.get("weapon") or tags.get("category")
                    else:
                        sub_tag = tags.get("category") or tags.get("weapon")
                    
                    if not sub_tag: continue
                    
                    sub_internal = sub_tag.get("internal_name")
                    # 如果二级分类和父级分类一样（魔法值或数据异常），或者已经处理过，则跳过
                    if not sub_internal or sub_internal == real_parent_internal or sub_internal in processed_in_type: 
                        continue
                    
                    sub_name = sub_tag.get("localized_name") or sub_tag.get("name")
                    page_categories.append({
                        "name": sub_name,
                        "internal_name": sub_internal,
                        "category_type": sub_tag.get("category", "category"),
                        "full_internal_name": sub_internal if sub_internal.startswith("csgo_") else f"csgo_{sub_internal}",
                        "parent_internal_name": real_parent_internal
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
        
        # 2.1 抓取完一个一级分类后，立即从 Redis 读取并保存到数据库
        type_temp_count = redis_client.llen(REDIS_TEMP_CATEGORY_KEY)
        if type_temp_count > 0:
            logger.info(f"🚀 一级分类 [{type_name}] 抓取完成，正在从 Redis 读取 {type_temp_count} 条数据并保存到数据库...")
            all_temp_data = redis_client.lrange(REDIS_TEMP_CATEGORY_KEY, 0, -1)
            all_categories = [json.loads(d) for d in all_temp_data]
            
            save_categories(all_categories)
            logger.info(f"✅ 成功将 [{type_name}] 的 {len(all_categories)} 条二级分类数据保存到数据库")
            
            # 保存完成后清理 Redis，为下一个一级分类腾出空间
            redis_client.delete(REDIS_TEMP_CATEGORY_KEY)
        
        logger.info(f"✅ 一级分类 [{type_name}] 同步完毕")
        time.sleep(random.uniform(10, 15))

    logger.info("🎉 全量精准分类同步任务圆满完成！")

if __name__ == "__main__":
    setup_logging()
    run_category_sync()
