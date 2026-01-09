import random
import sys
import os
import json
import requests
import time
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field, AliasPath
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
from utils.browser_helper import BrowserHelper
from utils.proxy_helper import get_proxies
from utils.network_util import log_request_ip

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

# --- Pydantic 模型定义 ---

class BuffGoodsItem(BaseModel):
    goods_id: int = Field(alias="id")
    name: str
    market_hash_name: Optional[str] = None
    short_name: Optional[str] = None
    
    # 使用 AliasPath 提取深度嵌套字段
    icon_url: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "icon_url"))
    original_icon_url: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "original_icon_url"))
    rarity: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "rarity", "internal_name"))
    exterior: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "exterior", "internal_name"))
    type: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "type", "internal_name"))
    tags_dict: Optional[Dict[str, Any]] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags"))

class BuffGoodsData(BaseModel):
    items: List[BuffGoodsItem]
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
def fetch_goods_api(category_internal_name: str, page_num: int = 1, profile: Any = None) -> BuffGoodsData:
    """使用 Tenacity 重试的商品列表 API 请求"""
    url = f"{BUFF_HOST}/api/market/goods"
    params = {
        "game": "csgo",
        "category": category_internal_name,
        "page_num": page_num,
        "tab": "selling"
    }
    
    if not profile or not profile.cookie:
        raise Exception(f"无法获取有效 Profile 或 Cookie")

    headers = profile.get_headers()
    
    proxies = get_proxies()
    log_request_ip(proxies, prefix="[GoodsSync] ")
    response = requests.get(url, headers=headers, params=params, proxies=proxies, timeout=15)
    
    # 强制设置编码，防止中文乱码
    response.encoding = 'utf-8'
    
    if response.status_code == 403:
        raise LoginRequiredError("Buff Login Required (403)")
    
    if response.status_code == 429:
        raise requests.exceptions.RequestException(f"Rate limited (429)", response=response)

    response.raise_for_status()
    
    # 使用 json() 解析更安全，或者确保 model_validate_json 接收的是正确编码的字符串
    try:
        resp_json = response.json()
        resp = BuffGoodsResponse.model_validate(resp_json)
    except Exception as e:
        logger.error(f"解析 API 响应失败: {e}")
        # 如果 json 解析失败，尝试原始文本
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
                "tags": stmt.excluded.tags,
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

def process_category(category, force=False, task_id=None, profile=None):
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
            data = fetch_goods_api(cat_internal, page, profile=profile)
            if page == 1:
                total_pages = data.total_page
                total_count = data.total_count
                logger.info(f"📊 Buff 共 {total_count} 个商品, {total_pages} 页. 库中已存 {db_count} 个")
                if not force and db_count >= total_count:
                    logger.info(f"✨ 分类 {cat_name} 已同步，跳过")
                    break

            items = data.items
            if items:
                logger.debug(f"🔍 第一个商品校验: {items[0].name}, icon_url: {items[0].icon_url}")
            
            goods_to_save = []
            for item in items:
                goods_to_save.append({
                    "goods_id": item.goods_id,
                    "name": item.name,
                    "market_hash_name": item.market_hash_name or "",
                    "original_icon_url": item.original_icon_url or "",
                    "icon_url": item.icon_url or "",
                    "short_name": item.short_name or "",
                    "internal_name": item.market_hash_name or "", # 暂时用 hash name
                    "category_id": cat_id or 0,
                    "rarity": item.rarity or "",
                    "exterior": item.exterior or "",
                    "tags": json.dumps(item.tags_dict, ensure_ascii=False) if item.tags_dict else None
                })
            
            saved = save_goods_batch(goods_to_save)
            logger.info(f"📦 第 {page}/{total_pages} 页: 同步 {len(items)} 个，生效 {saved} 个")
            
            wait_time = random.uniform(settings.CRAWL_INTERVAL_MIN, settings.CRAWL_INTERVAL_MAX)
            logger.info(f"💤 暂停 {wait_time:.2f} 秒后继续...")
            time.sleep(wait_time)
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
    # 在任务启动时随机生成一个浏览器指纹并绑定 Cookie
    profile = BrowserHelper.create_profile(user_id)
    logger.info(f"🎭 已为商品同步任务分配指纹: {profile.user_agent}")
    
    categories = get_secondary_categories()
    
    for cat in categories:
        res = process_category(cat, force=force, task_id=task_id, profile=profile)
        if res == "STOPPED": break
        
    logger.info(f"🏁 所有分类商品同步完成")

if __name__ == "__main__":
    run_goods_sync()
