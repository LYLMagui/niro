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
from utils.browser_helper import BrowserHelper
from utils.proxy_helper import get_proxies
from utils.network_util import log_request_ip

logger = get_logger(__name__)

BUFF_HOST = "https://buff.163.com"

# --- Pydantic 模型定义 ---

class BuffGoodsItem(BaseModel):
    # 使用 AliasPath 提取深度嵌套字段
    localized_name: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "type", "localized_name"))
    internal_name: Optional[str] = Field(None, validation_alias=AliasPath("goods_info", "info", "tags", "type", "internal_name"))
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
    before_sleep=before_sleep_log(logger, "WARNING"),
    reraise=True
)
def fetch_buff_goods_api(params: Dict[str, Any], profile: Any = None) -> BuffGoodsData:
    """
    使用 Tenacity 声明式重试的 Buff 饰品列表接口
    """
    url = f"{BUFF_HOST}/api/market/goods"
    
    if not profile or not profile.cookie:
        raise Exception(f"无法获取有效 Profile 或 Cookie")

    headers = profile.get_headers()
    
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

def reset_category_hierarchy():
    """重置所有分类的层级关系，防止旧数据干扰"""
    session = Session()
    try:
        session.query(BuffGoodsCategory).update({"parent_id": 0})
        session.commit()
        logger.info("🧹 已重置所有分类的层级关系")
    except Exception as e:
        session.rollback()
        logger.error(f"❌ 重置分类层级失败: {e}")
    finally:
        Session.remove()

def save_categories(categories):
    """保存分类到数据库 (支持层级 Upsert)"""
    if not categories: return
    session = Session()
    try:
        # 1. 第一步：获取所有涉及到的分类名，提前查询已存在的 ID 映射
        # 这样可以减少数据库往返次数
        all_internals = set()
        for cat in categories:
            all_internals.add(cat["internal_name"])
            if cat.get("parent_internal_name"):
                all_internals.add(cat["parent_internal_name"])
        
        # 2. 第二步：批量插入/更新基本信息 (此时 parent_id 暂时保持原样或设为 0)
        db_items = []
        for cat in categories:
            db_items.append({
                "name": cat["name"],
                "internal_name": cat["internal_name"],
                "category_type": cat.get("category_type", "type"),
                "full_internal_name": cat["full_internal_name"],
                "parent_id": 0 # 初始值，稍后更新
            })
            
        stmt = insert(BuffGoodsCategory).values(db_items)
        # 注意：这里需要数据库有唯一索引 idx_category_internal_name
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

        # 3. 第三步：重新查询 ID 映射，处理 parent_id
        existing_cats = session.query(BuffGoodsCategory.id, BuffGoodsCategory.internal_name).filter(
            BuffGoodsCategory.internal_name.in_(list(all_internals))
        ).all()
        name_to_id = {c.internal_name: c.id for c in existing_cats}

        # 4. 第四步：构建更新列表，一次性更新 parent_id
        update_count = 0
        for cat in categories:
            child_internal = cat["internal_name"]
            parent_internal = cat.get("parent_internal_name")
            
            if parent_internal and parent_internal in name_to_id:
                new_parent_id = name_to_id[parent_internal]
                child_id = name_to_id.get(child_internal)
                
                if child_id:
                    session.query(BuffGoodsCategory).filter(
                        BuffGoodsCategory.id == child_id
                    ).update({"parent_id": new_parent_id})
                    update_count += 1
        
        session.commit()
        return update_count
    except Exception as e:
        session.rollback()
        logger.error(f"❌ 保存分类失败: {e}")
        return 0
    finally:
        Session.remove()

def get_buff_goods_parent_category(task_id=None, profile=None):
    """从商品列表抓取所有层级的分类 (通过 tags 提取层级)"""
    logger.info("🚀 开始从商品列表抓取多级分类...")
    
    page, total_pages = 1, 1
    # 限制页数，因为大部分分类在前面几十页就能全部覆盖
    max_pages = 50  # 默认最多同步 50 页
    
    # 记录本次任务已发现的分类，避免重复保存
    processed_internals = set()
    total_saved = 0
    total_hierarchy = 0
    
    while page <= total_pages and page <= max_pages:
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已被手动停止")
            return "STOPPED"
            
        logger.info(f"正在抓取商品列表第 {page}/{min(total_pages, max_pages)} 页以提取分类...")
        
        params = {"game": "csgo", "page_num": page, "tab": "selling"}
        
        try:
            data = fetch_buff_goods_api(params, profile=profile)
            if page == 1:
                total_pages = data.total_page
                logger.info(f"📊 检测到共 {total_pages} 页，本次最多同步 {max_pages} 页")

            items = data.items
            page_categories_map = {} # 本页发现的新分类
            
            for item in items:
                tags = item.tags or {}
                
                # 定义层级顺序: type (一级，如步枪) -> weapon (二级，如AK-47)
                hierarchy = ["type", "weapon"]
                prev_internal = None
                
                for key in hierarchy:
                    tag = tags.get(key)
                    if not tag: continue
                    
                    internal = tag.get("internal_name")
                    if not internal: continue
                    
                    name = tag.get("localized_name") or tag.get("name")
                    if not name: continue
                    
                    # 构造分类对象
                    full_internal = internal if internal.startswith("csgo_") else f"csgo_{internal}"
                    cat_obj = {
                        "name": name,
                        "internal_name": internal,
                        "category_type": key, # 记录参数类型 (type 或 weapon)
                        "full_internal_name": full_internal,
                        "parent_internal_name": prev_internal
                    }
                    
                    # 如果是新发现的分类，或者建立了新的父子关系，则加入保存列表
                    if internal not in page_categories_map:
                        page_categories_map[internal] = cat_obj
                    
                    prev_internal = internal
            
            # 每抓完一页立即保存
            if page_categories_map:
                h_count = save_categories(list(page_categories_map.values()))
                new_count = len([k for k in page_categories_map.keys() if k not in processed_internals])
                total_saved += new_count
                total_hierarchy += h_count
                processed_internals.update(page_categories_map.keys())
                logger.info(f"📄 第 {page} 页处理完成: 新增/更新 {len(page_categories_map)} 条，当前累计发现 {len(processed_internals)} 条")

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
            
    logger.info(f"✅ 分类同步完成: 累计发现 {len(processed_internals)} 条分类，已建立 {total_hierarchy} 条层级关系")
    return "SUCCESS"

def run_category_sync(task_id=None):
    """运行分类同步任务入口"""
    user_id = get_task_user_id(task_id)
    profile = BrowserHelper.create_profile(user_id)
    logger.info(f"🎭 已为分类同步任务分配指纹: {profile.user_agent}")
    
    try:
        # 移除全局重置逻辑，改为 save_categories 中的原地原子更新，避免同步期间分类树断裂影响业务
        # 从商品列表页抓取并提取多级分类 (内部会自动调用 save_categories)
        result = get_buff_goods_parent_category(task_id, profile=profile)
        if result == "STOPPED": return
    except Exception as e:
        logger.error(f"❌ 分类同步任务异常: {e}")
        raise

if __name__ == "__main__":
    run_category_sync()
