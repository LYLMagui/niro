import random
import sys
import os
import requests
import time
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field, AliasPath, ConfigDict
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type, before_sleep_log

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.models import BuffSticker
from storage.database import Session
from sqlalchemy import func
from sqlalchemy.dialects.postgresql import insert
from config.constants import GAME_CSGO, CATEGORY_STICKER, TAB_SELLING
from config.settings import CRAWL_INTERVAL_MIN, CRAWL_INTERVAL_MAX
from utils.logger import get_logger
from utils.exception_handler import LoginRequiredError
from utils.browser_helper import BrowserHelper
from utils.proxy_helper import get_proxies

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

# --- Pydantic 模型定义 ---

class BuffStickerItem(BaseModel):
    """BUFF印花项模型"""
    model_config = ConfigDict(populate_by_name=True)

    sticker_id: int = Field(alias="id")
    name: str
    image_url: str = Field(validation_alias=AliasPath("goods_info", "icon_url"))
    price: float = Field(alias="sell_min_price")
    # 用于过滤的类型
    item_type: str = Field(validation_alias=AliasPath("goods_info", "info", "tags", "type", "internal_name"))

class BuffStickerData(BaseModel):
    """BUFF印花分页数据模型"""
    items: List[BuffStickerItem]
    total_page: int
    total_count: int

class BuffStickerResponse(BaseModel):
    """BUFF印花响应模型"""
    code: str
    data: Optional[BuffStickerData] = None
    msg: Optional[str] = None

# --- 业务逻辑 ---

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    retry=retry_if_exception_type(requests.exceptions.RequestException),
    before_sleep=before_sleep_log(logger, "WARNING"),
    reraise=True
)
def fetch_stickers_api(page_num: int = 1, profile: Any = None) -> BuffStickerData:
    """分页抓取印花数据"""
    url = f"{BUFF_HOST}/api/market/goods"
    params = {
        "game": GAME_CSGO,
        "category_group": CATEGORY_STICKER,  # 关键：指定分类组为印花
        "page_num": page_num,
        "tab": TAB_SELLING,
        "use_suggestion": 0
    }
    
    if not profile or not profile.cookie:
        raise Exception("无法获取有效 Profile 或 Cookie")

    headers = profile.get_headers()
    proxies = get_proxies()
    
    response = requests.get(url, headers=headers, params=params, proxies=proxies, timeout=15)
    response.encoding = 'utf-8'
    
    if response.status_code == 403:
        raise LoginRequiredError("Buff Login Required (403)")
    
    if response.status_code == 429:
        logger.warning(f"触发频率限制 (429)，准备重试... URL: {url} Page: {page_num}")
        raise requests.exceptions.RequestException(f"Rate limited (429)", response=response)

    response.raise_for_status()
    
    resp_json = response.json()
    resp = BuffStickerResponse.model_validate(resp_json)
    
    if resp.code != "OK":
        raise Exception(f"BUFF API Error: {resp.msg}")
        
    return resp.data

def upsert_stickers(stickers: List[BuffStickerItem]):
    """使用 PostgreSQL 的 upsert 逻辑更新印花价格"""
    if not stickers:
        return

    # 过滤掉非印花物品（如挂件等）
    valid_stickers = [s for s in stickers if s.item_type == "csgo_tool_sticker"]
    if not valid_stickers:
        return

    with Session() as session:
        for item in valid_stickers:
            stmt = insert(BuffSticker).values(
                sticker_id=item.sticker_id,
                name=item.name,
                image_url=item.image_url,
                price=item.price
            )
            # 如果冲突（sticker_id已存在），则更新价格、名称、图片和更新时间
            stmt = stmt.on_conflict_do_update(
                index_elements=['sticker_id'],
                set_={
                    'price': stmt.excluded.price,
                    'name': stmt.excluded.name,
                    'image_url': stmt.excluded.image_url,
                    'update_time': func.now()
                }
            )
            session.execute(stmt)
        session.commit()

def run_sticker_sync(user_id: int = 1):
    """执行同步印花主逻辑"""
    logger.info(f"开始执行印花价值同步任务, 用户ID: {user_id}")
    
    try:
        # 1. 获取用户 Profile (包含 Cookie 和浏览器指纹)
        profile = BrowserHelper.create_profile(user_id)
        logger.info(f"🎭 已为印花同步任务分配指纹: {profile.user_agent}")
        
        if not profile:
            logger.error(f"未找到用户 {user_id} 的配置信息")
            return

        # 2. 抓取第一页获取总页数
        first_page_data = fetch_stickers_api(page_num=1, profile=profile)
        total_page = first_page_data.total_page
        logger.info(f"成功获取印花数据，共 {total_page} 页, {first_page_data.total_count} 个印花")
        
        # 处理第一页
        upsert_stickers(first_page_data.items)
        
        # 3. 循环处理后续页面
        for page in range(2, total_page + 1):
            logger.info(f"正在同步第 {page}/{total_page} 页...")
            
            # 随机休眠，避免风控
            time.sleep(random.uniform(CRAWL_INTERVAL_MIN, CRAWL_INTERVAL_MAX))
            
            page_data = fetch_stickers_api(page_num=page, profile=profile)
            if page_data and page_data.items:
                upsert_stickers(page_data.items)
            
        logger.info("印花价值同步任务执行完成！")
        
    except Exception as e:
        logger.error("印花价值同步任务失败: {}", e, exc_info=True)
        raise e

if __name__ == "__main__":
    # 默认使用管理员账户执行
    run_sticker_sync(user_id=1)
