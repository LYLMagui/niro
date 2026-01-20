import random
import sys
import os
import httpx
import asyncio
import time
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field, AliasPath, ConfigDict

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.models import BuffSticker
from storage.database import async_session_factory
from sqlalchemy import func
from sqlalchemy.dialects.postgresql import insert
from config.constants import GAME_CSGO, CATEGORY_STICKER, TAB_SELLING
from config.settings import CRAWL_INTERVAL_MIN, CRAWL_INTERVAL_MAX
from utils.logger import get_logger, setup_logging, account_name_var, account_id_var, task_id_var
from utils.browser_helper import BrowserHelper, BrowserProfile
from utils.exception_handler import LoginRequiredError

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
    sell_num: int = Field(alias="sell_num")
    # 用于过滤的类型
    item_type: str = Field(validation_alias=AliasPath("goods_info", "info", "tags", "type", "internal_name"))

class BuffStickerData(BaseModel):
    """BUFF印花分页数据模型"""
    items: List[BuffStickerItem]
    total_page: int
    total_count: int

# --- 业务逻辑 ---

async def fetch_stickers_api(client: httpx.AsyncClient, page_num: int = 1, profile: BrowserProfile = None) -> Optional[BuffStickerData]:
    """分页抓取印花数据 (异步版)"""
    url = f"{BUFF_HOST}/api/market/goods"
    params = {
        "game": GAME_CSGO,
        "category_group": CATEGORY_STICKER,
        "page_num": page_num,
        "tab": TAB_SELLING,
        "use_suggestion": 0
    }
    
    if not profile or not profile.cookie:
        logger.error("无法获取有效 Profile 或 Cookie")
        return None

    headers = profile.get_headers()
    
    try:
        response = await client.get(url, headers=headers, params=params)
        response.raise_for_status()
        
        # 预检：如果返回的是 HTML，说明 Cookie 已失效
        resp_text = response.text
        if resp_text.strip().startswith("<!DOCTYPE") or resp_text.strip().startswith("<html"):
            logger.error("🔑 Cookie 已失效 (收到 HTML 登录重定向响应)")
            raise LoginRequiredError("Buff Login Required (HTML Redirect)")

        try:
            data = response.json()
        except Exception as e:
            logger.error(f"解析 JSON 失败: {e}, 响应内容: {resp_text[:100]}...")
            raise LoginRequiredError(f"Invalid JSON Response: {str(e)}")
        
        if data.get("code") == "OK":
            return BuffStickerData(**data.get("data"))
        else:
            msg = data.get('msg', 'Unknown API Error')
            logger.error(f"API 错误: {msg}")
            # 根据用户要求：只要不是成功，则视为失效
            raise LoginRequiredError(f"Buff API Error: {msg}")
    except LoginRequiredError as le:
        raise le
    except Exception as e:
        logger.error(f"抓取印花第 {page_num} 页失败: {e}")
        return None

async def upsert_stickers(stickers: List[BuffStickerItem]):
    """批量更新印花数据 (异步 UPSERT)"""
    if not stickers:
        return
        
    # 转换为字典列表
    values = []
    for item in stickers:
        values.append({
            "sticker_id": item.sticker_id,
            "name": item.name,
            "image_url": item.image_url,
            "price": item.price,
            "sell_num": item.sell_num
        })
        
    async with async_session_factory() as session:
        try:
            stmt = insert(BuffSticker).values(values)
            stmt = stmt.on_conflict_do_update(
                index_elements=['sticker_id'],
                set_={
                    'price': stmt.excluded.price,
                    'name': stmt.excluded.name,
                    'image_url': stmt.excluded.image_url,
                    'sell_num': stmt.excluded.sell_num,
                    'update_time': func.now()
                }
            )
            await session.execute(stmt)
            await session.commit()
        except Exception as e:
            await session.rollback()
            logger.error(f"❌ 更新印花数据失败: {e}")

async def run_sticker_sync(user_id: int = 1, task_id: int = None):
    """执行同步印花主逻辑 (Async Entry)"""
    if task_id:
        task_id_var.set(task_id)
        
    # 设置账号级上下文 (默认管理员账号)
    account_id_var.set(1)
    account_name_var.set("Admin")

    logger.info("🚀 开始执行印花价值同步任务")
    
    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            # 1. 获取用户 Profile
            profile = BrowserHelper.create_profile(user_id)
            if not profile:
                logger.error(f"未找到用户 {user_id} 的配置信息")
                return

            # 2. 抓取第一页
            first_page_data = await fetch_stickers_api(client, page_num=1, profile=profile)
            if not first_page_data:
                return
                
            total_page = first_page_data.total_page
            logger.info(f"📊 成功获取印花数据，共 {total_page} 页, {first_page_data.total_count} 个印花")
            
            await upsert_stickers(first_page_data.items)
            
            # 3. 循环处理
            for page in range(2, total_page + 1):
                logger.info(f"正在同步第 {page}/{total_page} 页...")
                await asyncio.sleep(random.uniform(CRAWL_INTERVAL_MIN, CRAWL_INTERVAL_MAX))
                
                page_data = await fetch_stickers_api(client, page_num=page, profile=profile)
                if page_data and page_data.items:
                    await upsert_stickers(page_data.items)
                
            logger.info("✅ 印花价值同步任务执行完成！")
            
        except Exception as e:
            logger.error(f"印花价值同步任务失败: {e}")

if __name__ == "__main__":
    setup_logging()
    asyncio.run(run_sticker_sync())
