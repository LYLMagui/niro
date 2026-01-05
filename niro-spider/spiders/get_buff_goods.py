import random
import sys
import os
import requests
import time
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type, before_sleep_log

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.models import BuffGoods, BuffGoodsCategory, BuffScanTask
from storage.database import Session
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy import func
from config import settings
from utils.logger import get_logger
from utils.exception_handler import LoginRequiredError
from utils.cookie_util import get_latest_cookie

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

# --- Pydantic 模型定义 ---

class BuffGoodsInfo(BaseModel):
    goods_id: int = Field(alias="id")
    name: str
    short_name: Optional[str] = None
    market_hash_name: Optional[str] = None
    icon_url: Optional[str] = None
    original_icon_url: Optional[str] = None
    internal_name: Optional[str] = None

class BuffGoodsItem(BaseModel):
    id: int
    name: str
    market_hash_name: Optional[str] = None
    original_icon_url: Optional[str] = None
    icon_url: Optional[str] = None
    short_name: Optional[str] = None
    goods_id: int = Field(alias="id")

class BuffGoodsData(BaseModel):
    items: List[Dict[str, Any]]
    total_page: int
    total_count: int

class BuffGoodsResponse(BaseModel):
    code: str
    data: Optional[BuffGoodsData] = None
    msg: Optional[str] = None

# --- 业务逻辑 ---

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    retry=retry_if_exception_type(requests.exceptions.RequestException),
    before_sleep=before_sleep_log(logger, "WARNING"),
    reraise=True
)
def fetch_goods_api(category_internal_name: str, page_num: int = 1, user_id: Optional[int] = None) -> BuffGoodsData:
    """使用 Tenacity 重试的商品列表 API 请求"""
    url = f"{BUFF_HOST}/api/market/goods"
    params = {
        "game": "csgo",
        "category": category_internal_name,
        "page_num": page_num,
        "tab": "selling"
    }
    
    current_cookie = get_latest_cookie(user_id)
    if not current_cookie:
        raise Exception(f"无法获取有效 Cookie (user_id: {user_id})")

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "cookie": current_cookie,
        "accept": "application/json, text/javascript, */*; q=0.01",
        "referer": "https://buff.163.com/market/csgo",
        "x-requested-with": "XMLHttpRequest",
    }
    
    response = requests.get(url, headers=headers, params=params, timeout=15)
    
    if response.status_code == 403:
        raise LoginRequiredError("Buff Login Required (403)")
    
    if response.status_code == 429:
        raise requests.exceptions.RequestException(f"Rate limited (429)", response=response)

    response.raise_for_status()
    resp = BuffGoodsResponse.model_validate_json(response.text)
    
    if resp.code == "Login Required":
        raise LoginRequiredError("Buff Login Required")
        
    if resp.code != "OK" or not resp.data:
        raise Exception(f"API 业务错误: {resp.msg}")
        
    return resp.data

def get_db_goods_count(category_id):
    """获取数据库中某个分类的商品数量"""
    session = Session()
    try:
        return session.query(func.count(BuffGoods.id)).filter(BuffGoods.category_id == category_id).scalar() or 0
    finally:
        Session.remove()

def get_secondary_categories():
    """获取所有二级分类"""
    session = Session()
    try:
        query = session.query(BuffGoodsCategory).filter(BuffGoodsCategory.parent_id > 0).order_by(BuffGoodsCategory.id)
        return [{"id": c.id, "name": c.name, "internal_name": c.internal_name} for c in query.all()]
    finally:
        Session.remove()

def save_goods_batch(goods_list):
    """批量保存商品数据 (UPSERT)"""
    if not goods_list: return 0
    session = Session()
    try:
        stmt = insert(BuffGoods).values(goods_list)
        upsert_stmt = stmt.on_conflict_do_update(
            index_elements=['goods_id'],
            set_={
                "name": stmt.excluded.name,
                "short_name": stmt.excluded.short_name,
                "internal_name": stmt.excluded.internal_name,
                "category_id": stmt.excluded.category_id,
                "rarity": stmt.excluded.rarity,
                "exterior": stmt.excluded.exterior,
                "market_hash_name": stmt.excluded.market_hash_name,
                "icon_url": stmt.excluded.icon_url,
                "original_icon_url": stmt.excluded.original_icon_url,
                "update_time": func.now()
            }
        )
        result = session.execute(upsert_stmt)
        session.commit()
        return result.rowcount
    except Exception as e:
        session.rollback()
        logger.error(f"❌ 批量保存失败: {e}")
        return 0
    finally:
        Session.remove()

def is_task_running(task_id):
    """检查任务是否仍处于活跃状态"""
    if not task_id: return True
    session = Session()
    try:
        task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()
        return task.status in (1, 4) if task else False
    finally:
        Session.remove()

def get_task_user_id(task_id):
    """获取任务所属的用户ID"""
    if not task_id: return None
    session = Session()
    try:
        task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()
        return task.user_id if task else None
    finally:
        Session.remove()

def process_category(category, force=False, task_id=None, user_id=None):
    """处理单个分类"""
    cat_id = category['id']
    cat_internal = category['internal_name']
    cat_name = category['name']
    
    logger.info(f"🚀 开始处理分类: {cat_name} ({cat_internal})")
    
    db_count = get_db_goods_count(cat_id)
    page, total_pages = 1, 1
    
    while page <= total_pages:
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已停止")
            return "STOPPED"

        try:
            data = fetch_goods_api(cat_internal, page, user_id)
            if page == 1:
                total_pages = data.total_page
                total_count = data.total_count
                logger.info(f"📊 Buff 共 {total_count} 个商品, {total_pages} 页. 库中已存 {db_count} 个")
                if not force and db_count >= total_count:
                    logger.info(f"✨ 分类 {cat_name} 已同步，跳过")
                    break

            items = data.items
            goods_to_save = []
            for item in items:
                # 提取 tags
                tags = item.get("goods_info", {}).get("info", {}).get("tags", {})
                
                goods_to_save.append({
                    "goods_id": item.get("id"),
                    "name": item.get("name"),
                    "market_hash_name": item.get("market_hash_name"),
                    "original_icon_url": item.get("original_icon_url"),
                    "icon_url": item.get("icon_url"),
                    "short_name": item.get("short_name"),
                    "internal_name": item.get("market_hash_name"), # 暂时用 hash name
                    "category_id": cat_id,
                    "rarity": tags.get("rarity", {}).get("internal_name"),
                    "exterior": tags.get("exterior", {}).get("internal_name"),
                })
            
            saved = save_goods_batch(goods_to_save)
            logger.info(f"📦 第 {page}/{total_pages} 页: 同步 {len(items)} 个，生效 {saved} 个")
            
            time.sleep(random.uniform(settings.CRAWL_INTERVAL_MIN, settings.CRAWL_INTERVAL_MAX))
            page += 1
            
        except LoginRequiredError:
            raise
        except Exception as e:
            logger.error(f"❌ 抓取第 {page} 页失败: {e}")
            page += 1
            continue
            
    return "DONE"

def run_goods_sync(force=False, task_id=None):
    """运行商品同步任务入口"""
    user_id = get_task_user_id(task_id)
    categories = get_secondary_categories()
    
    for cat in categories:
        res = process_category(cat, force=force, task_id=task_id, user_id=user_id)
        if res == "STOPPED": break
        
    logger.info("🏁 所有分类商品同步完成")

if __name__ == "__main__":
    run_goods_sync()
