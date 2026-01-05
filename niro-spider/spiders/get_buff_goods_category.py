import random
import sys
import os
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

from storage.models import BuffGoodsCategory, BuffScanTask
from storage.database import Session
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy import func
from config import settings
from utils.logger import get_logger
from utils.exception_handler import LoginRequiredError
from utils.cookie_util import get_latest_cookie
from utils.proxy_helper import get_proxies
from utils.network_util import log_request_ip

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

# --- Pydantic 模型定义 ---

class BuffGoodsItem(BaseModel):
    # 使用 AliasPath 提取深度嵌套字段
    localized_name: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "type", "localized_name"))
    internal_name: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "type", "internal_name"))

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
    before_sleep=before_sleep_log(logger, "WARNING"),
    reraise=True
)
def fetch_buff_goods_api(params: Dict[str, Any], user_id: Optional[int] = None) -> BuffGoodsData:
    """
    使用 Tenacity 声明式重试的 Buff 饰品列表接口
    """
    url = f"{BUFF_HOST}/api/market/goods"
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
    
    proxies = get_proxies()
    log_request_ip(proxies, prefix="[Category] ")
    response = requests.get(url, headers=headers, params=params, proxies=proxies, timeout=10)
    
    # 强制设置编码，防止中文乱码
    response.encoding = 'utf-8'
    
    if response.status_code == 403:
        raise LoginRequiredError("Buff Login Required (403)")
    
    if response.status_code == 429:
        # 429 交给 Tenacity 重试，这里抛出异常触发重试
        raise requests.exceptions.RequestException(f"Rate limited (429)", response=response)

    response.raise_for_status()
    
    # 使用 json() 解析更安全
    try:
        resp_json = response.json()
        resp = BuffGoodsResponse.model_validate(resp_json)
    except Exception as e:
        logger.error(f"解析 API 响应失败: {e}")
        resp = BuffGoodsResponse.model_validate_json(response.text)
    
    if resp.code == "Login Required":
        raise LoginRequiredError("Buff Login Required")
        
    if resp.code != "OK" or not resp.data:
        raise Exception(f"API 业务错误: {resp.msg}")
        
    return resp.data

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

def get_buff_goods_parent_category(task_id=None, user_id=None):
    """获取一级分类 (Type)"""
    logger.info("🚀 开始抓取一级分类...")
    all_categories = []
    page, total_pages = 1, 1
    
    while page <= total_pages and page <= 20:
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已被手动停止")
            return "STOPPED"

        params = {"game": "csgo", "page_num": page, "tab": "selling"}
        logger.info(f"正在抓取一级分类第 {page}/{min(total_pages, 20)} 页...")
        
        try:
            data = fetch_buff_goods_api(params, user_id=user_id)
            if page == 1:
                total_pages = data.total_page
                logger.info(f"📊 检测到共 {total_pages} 页，本次最多同步 20 页")

            # 直接从 item 中获取已经解析好的分类信息
            items = data.items
            
            for item in items:
                if not item.internal_name: continue
                full_name = item.internal_name
                cat = {
                    "parent_id": 0,
                    "name": item.localized_name,
                    "internal_name": full_name.split('_')[-1] if full_name else "",
                    "full_internal_name": full_name,
                }
                all_categories.append(cat)
                
            wait_time = random.uniform(settings.CRAWL_INTERVAL_MIN, settings.CRAWL_INTERVAL_MAX)
            logger.info(f"💤 暂停 {wait_time:.2f} 秒后继续...")
            time.sleep(wait_time)
            page += 1
        except LoginRequiredError as e:
            logger.error(f"🔑 登录失效: {e}")
            raise
        except Exception as e:
            logger.error(f"❌ 抓取第 {page} 页失败: {e}")
            page += 1
            continue

    # 去重
    seen = set()
    dedup_list = []
    for cat in reversed(all_categories):
        key = cat["full_internal_name"]
        if key not in seen:
            dedup_list.append(cat)
            seen.add(key)
    
    return dedup_list

def save_categories(categories):
    """保存分类到数据库 (Upsert)"""
    if not categories: return
    session = Session()
    try:
        stmt = insert(BuffGoodsCategory).values(categories)
        # 如果 internal_name 冲突，则更新 name 和 full_internal_name
        stmt = stmt.on_conflict_do_update(
            index_elements=['internal_name', 'parent_id'],
            set_={
                'name': stmt.excluded.name,
                'full_internal_name': stmt.excluded.full_internal_name
            }
        )
        session.execute(stmt)
        session.commit()
        logger.info(f"✅ 成功同步 {len(categories)} 条分类数据")
    except Exception as e:
        session.rollback()
        logger.error(f"❌ 保存分类失败: {e}")
    finally:
        Session.remove()

def run_category_sync(task_id=None):
    """运行分类同步任务入口"""
    user_id = get_task_user_id(task_id)
    try:
        categories = get_buff_goods_parent_category(task_id, user_id)
        if categories == "STOPPED": return
        save_categories(categories)
    except Exception as e:
        logger.error(f"❌ 分类同步任务异常: {e}")
        raise

if __name__ == "__main__":
    run_category_sync()
