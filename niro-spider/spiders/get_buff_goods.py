import random
import sys
import os
import json
import requests
import time
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field, AliasPath
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from storage.models import BuffGoods, BuffGoodsCategory, BuffScanTask
from storage.database import Session
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy import func
from utils.logger import get_logger, setup_logging, get_current_ip_cached
from utils.exception_handler import LoginRequiredError
from utils.browser_helper import BrowserHelper
from utils.proxy_helper import get_proxies, refresh_proxies
from utils.network_util import log_request_ip
from storage.redis_pool import redis_client
from utils.notifier import Notifier

logger = get_logger(__name__)
notifier = Notifier()

BUFF_HOST = "https://buff.163.com"

REDIS_TEMP_GOODS_KEY = "niro:temp:goods:sync"
# 存储每个分类的状态：{cat_id: {"total_count": x, "total_page": y}}
REDIS_CATEGORY_STATE_PREFIX = "niro:spider:cat_state:"

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

def before_retry_callback(retry_state):
    """重试前的回调：处理代理失效与节点切换"""
    attempt = retry_state.attempt_number
    exception = retry_state.outcome.exception()
    
    # 记录错误原因
    error_msg = str(exception)
    if "Read timed out" in error_msg:
        logger.warning(f"⏳ [超时重试] 请求 Buff 超时 (Read timed out)，正在尝试切换代理节点... ({attempt}/3)")
    elif "429" in error_msg:
        logger.warning(f"🚫 [限流重试] 触发 Buff 频率限制 (429)，正在更换 IP 规避... ({attempt}/3)")
    else:
        logger.warning(f"🔄 [异常重试] 请求出现异常: {error_msg}，准备重试... ({attempt}/3)")

    try:
        # 调用统一的代理刷新逻辑
        new_ip = refresh_proxies()
        # logger.info(f"✨ 重试准备就绪，当前出口 IP: {new_ip}")
    except Exception as e:
        logger.error(f"❌ 尝试切换代理节点失败: {e}")

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    retry=retry_if_exception_type(requests.exceptions.RequestException),
    before_sleep=before_retry_callback,
    reraise=True
)
def fetch_goods_api(category_internal_name: str, category_type: str = "category", page_num: int = 1, profile: Any = None) -> BuffGoodsData:
    """使用 Tenacity 重试的商品列表 API 请求"""
    url = f"{BUFF_HOST}/api/market/goods"
    params = {
        "game": "csgo",
        "page_num": page_num,
        "tab": "selling"
    }
    # 根据分类类型动态设置参数名 (category 或 category_group)
    params[category_type] = category_internal_name
    
    if not profile or not profile.cookie:
        raise Exception("无法获取有效 Profile 或 Cookie")

    headers = profile.get_headers()
    
    proxies = get_proxies()
    log_request_ip(proxies, prefix="[GoodsSync] ")
    response = requests.get(url, headers=headers, params=params, proxies=proxies, timeout=15)
    
    # 强制设置编码，防止中文乱码
    response.encoding = 'utf-8'
    
    if response.status_code == 403:
        raise LoginRequiredError("Buff Login Required (403)")
    
    if response.status_code == 429:
        raise requests.exceptions.RequestException("Rate limited (429)", response=response)

    response.raise_for_status()
    
    try:
        resp_json = response.json()
        resp = BuffGoodsResponse.model_validate(resp_json)
    except Exception as e:
        logger.error(f"解析 API 响应失败: {e}，响应内容: {response.text}")
        resp = BuffGoodsResponse.model_validate_json(response.text)
    
    if resp.code == "Login Required":
        logger.error(f"响应报错: {response.text}")
        raise LoginRequiredError("Buff Login Required")
        
    if resp.code != "OK" or not resp.data:
        logger.error(f"响应报错: {response.text}")
        raise Exception(f"API 业务错误: {resp.msg}")
        
    return resp.data

def get_db_goods_count(category_id):
    """获取数据库中某个分类的商品数量"""
    session = Session()
    try:
        return session.query(func.count(BuffGoods.id)).filter(BuffGoods.category_id == category_id).scalar() or 0
    finally:
        Session.remove()

def delete_category_goods(category_id):
    """删除某个分类下的所有商品"""
    session = Session()
    try:
        count = session.query(BuffGoods).filter(BuffGoods.category_id == category_id).delete()
        session.commit()
        logger.info(f"🗑️ 已清空分类 [ID:{category_id}] 的旧数据，共删除 {count} 条记录")
        return count
    except Exception as e:
        session.rollback()
        logger.error(f"❌ 清空分类数据失败: {e}")
        return 0
    finally:
        Session.remove()

def get_sync_categories():
    """获取所有待同步的二级分类"""
    session = Session()
    try:
        # 仅获取二级分类 (parent_id > 0)
        query = session.query(BuffGoodsCategory).filter(BuffGoodsCategory.parent_id > 0).order_by(BuffGoodsCategory.id)
        return [{
            "id": c.id, 
            "name": c.name, 
            "internal_name": c.internal_name,
            "category_type": c.category_type or "category"
        } for c in query.all()]
    finally:
        Session.remove()

def save_goods_batch(goods_list):
    """批量保存商品数据 (UPSERT)"""
    if not goods_list: return 0
    
    # 1. 对 goods_list 进行去重处理 (基于 goods_id)
    # PostgreSQL 的 ON CONFLICT DO UPDATE 不允许在同一次批量操作中对同一行进行多次更新
    unique_goods = {}
    for g in goods_list:
        # 如果有重复的 goods_id，保留最后一次出现的记录 (通常是较新的)
        unique_goods[g['goods_id']] = g
    
    final_goods_list = list(unique_goods.values())
    
    session = Session()
    try:
        stmt = insert(BuffGoods).values(final_goods_list)
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
    """处理单个分类：抓取全量页数后一次性入库"""
    cat_id = category['id']
    cat_internal = category['internal_name']
    cat_name = category['name']
    cat_type = category['category_type']
    
    logger.info(f"🚀 开始处理分类: {cat_name} ({cat_internal}, type: {cat_type})")
    
    # 1. 获取 Redis 中记录的上次同步状态
    state_key = f"{REDIS_CATEGORY_STATE_PREFIX}{cat_id}"
    stored_state_raw = redis_client.get(state_key)
    stored_state = json.loads(stored_state_raw) if stored_state_raw else None
    
    page, total_pages, total_count = 1, 1, 0
    
    # 确保开始前清理 Redis 缓存
    redis_client.delete(REDIS_TEMP_GOODS_KEY)
    
    while page <= total_pages:
        if task_id and not is_task_running(task_id):
            logger.warning(f"🛑 任务 [ID:{task_id}] 已停止")
            return "STOPPED"

        try:
            data = fetch_goods_api(cat_internal, category_type=cat_type, page_num=page, profile=profile)
            if page == 1:
                total_pages = data.total_page
                total_count = data.total_count
                logger.info(f"📊 Buff 当前: {total_count} 个商品, {total_pages} 页")
                
                # 状态对比逻辑
                if stored_state:
                    prev_count = stored_state.get("total_count", 0)
                    prev_page = stored_state.get("total_page", 0)
                    
                    if not force and total_count == prev_count and total_pages == prev_page:
                        logger.info(f"✨ 分类 {cat_name} 商品数量与上次同步一致 ({total_count}个)，跳过同步")
                        return 0
                    elif total_count < prev_count:
                        logger.warning(f"📉 分类 {cat_name} 商品减少 ({prev_count} -> {total_count})，将清空旧数据并全量重刷")
                        delete_category_goods(cat_id)
                    else:
                        logger.info(f"📈 分类 {cat_name} 商品有新增 ({prev_count} -> {total_count})，继续增量同步")
                else:
                    logger.info(f"🆕 分类 {cat_name} 首次同步或状态已失效，开始全量拉取")

            items = data.items
            if not items:
                break
                
            page_goods_list = []
            for item in items:
                # 提取更加丰富的标签信息，方便后续扩展
                tags_json = json.dumps(item.tags_dict, ensure_ascii=False) if item.tags_dict else None
                
                page_goods_list.append(json.dumps({
                    "goods_id": item.goods_id,
                    "name": item.name,
                    "market_hash_name": item.market_hash_name or "",
                    "original_icon_url": item.original_icon_url or "",
                    "icon_url": item.icon_url or "",
                    "short_name": item.short_name or "",
                    "internal_name": item.market_hash_name or "", 
                    "category_id": cat_id,
                    "rarity": item.rarity or "",
                    "exterior": item.exterior or "",
                    "tags": tags_json
                }, ensure_ascii=False))
            
            # 每抓完一页，立即存入 Redis
            if page_goods_list:
                redis_client.rpush(REDIS_TEMP_GOODS_KEY, *page_goods_list)
                logger.info(f"📦 第 {page}/{total_pages} 页: 采集到 {len(items)} 个商品并暂存至 Redis (当前分类累计: {redis_client.llen(REDIS_TEMP_GOODS_KEY)})")
            
            # 分页抓取间隔：每抓完一页暂停 7-12 秒
            wait_time = random.uniform(13, 19)
            logger.info(f"💤 暂停 {wait_time:.2f} 秒后继续抓取下一页...")
            time.sleep(wait_time)
            page += 1
            
        except LoginRequiredError:
            logger.error(f"🔑 登录失效，停止抓取")
            raise
        except Exception as e:
            logger.error(f"❌ 抓取第 {page} 页失败: {e}")
            page += 1
            time.sleep(5) 
            continue
            
    # 从 Redis 中取出所有暂存的商品数据
    temp_count = redis_client.llen(REDIS_TEMP_GOODS_KEY)
    if temp_count > 0:
        logger.info(f"💾 正在从 Redis 读取分类 [{cat_name}] 的 {temp_count} 个商品并保存到数据库...")
        all_temp_data = redis_client.lrange(REDIS_TEMP_GOODS_KEY, 0, -1)
        category_goods_list = [json.loads(d) for d in all_temp_data]
        
        saved_count = save_goods_batch(category_goods_list)
        logger.info(f"✅ 分类 {cat_name} 同步完成，库中生效 {saved_count} 条记录")
        
        # 保存完成后清理 Redis
        redis_client.delete(REDIS_TEMP_GOODS_KEY)
        
        # 4. 更新分类同步状态到 Redis (永久存储，直到下次更新)
        new_state = {"total_count": total_count, "total_page": total_pages}
        redis_client.set(state_key, json.dumps(new_state))
        logger.info(f"📝 已更新分类 [{cat_name}] 的同步状态: {new_state}")
        
        # 每抓完一个分类，保存完后随机暂停 12-16 秒，并观察 IP
        cat_wait_time = random.uniform(20, 25)
        logger.info(f"😴 分类 [{cat_name}] 处理完毕，休息 {cat_wait_time:.2f} 秒后处理下一个分类...")
        time.sleep(cat_wait_time)
        
        # 刷新出口 IP 显示
        try:
            new_ip = get_current_ip_cached(force_refresh=True)
            logger.info(f"✨ 当前出口 IP: {new_ip}")
        except Exception as e:
            logger.warning(f"⚠️ 获取出口 IP 失败: {e}，将继续尝试同步")
        
        return saved_count
    else:
        logger.info(f"💡 分类 {cat_name} 未发现新数据或已跳过")
        return 0

def run_goods_sync(force=False, task_id=None):
    """运行商品同步任务入口"""
    start_time = time.time()
    # 强制刷新出口IP缓存，确保日志显示准确
    get_current_ip_cached(force_refresh=True)
    
    user_id = get_task_user_id(task_id)
    # 在任务启动时随机生成一个浏览器指纹并绑定 Cookie
    profile = BrowserHelper.create_profile(user_id)
    logger.info(f"🎭 已为商品同步任务分配指纹: {profile.user_agent}")
    
    categories = get_sync_categories()
    total_categories = len(categories)
    processed_count = 0
    total_saved_goods = 0
    
    for cat in categories:
        try:
            res = process_category(cat, force=force, task_id=task_id, profile=profile)
            if res == "STOPPED":
                logger.warning(f"🛑 任务被手动停止，已同步 {processed_count}/{total_categories} 个分类")
                break
            
            if isinstance(res, int):
                total_saved_goods += res
            
            processed_count += 1
        except Exception as e:
            logger.error(f"❌ 处理分类 {cat.get('name', 'Unknown')} 时出现严重错误: {e}")
            continue
        
    # 计算耗时
    duration = time.time() - start_time
    hours, rem = divmod(duration, 3600)
    minutes, seconds = divmod(rem, 60)
    duration_str = f"{int(hours)}h {int(minutes)}m {int(seconds)}s" if hours > 0 else f"{int(minutes)}m {int(seconds)}s"

    # 发送通知
    msg = (
        f"✅ 【商品全量同步任务完成】\n"
        f"━━━━━━━━━━━━━━━\n"
        f"⏱️ 任务耗时：{duration_str}\n"
        f"📂 处理分类：{processed_count} / {total_categories}\n"
        f"📦 生效商品：{total_saved_goods} 条\n"
        f"👤 操作用户：{user_id if user_id else '系统控制'}\n"
        f"━━━━━━━━━━━━━━━\n"
        f"所有分类商品数据已同步至数据库。"
    )
    notifier.send_text(msg, user_id=user_id)

    logger.info(f"🏁 所有分类商品同步完成，总耗时: {duration_str}, 共更新 {total_saved_goods} 条数据")

if __name__ == "__main__":
    # 初始化日志配置
    setup_logging()
    
    # 手动指定分类同步（可选参数 force=True 会清除旧数据重新抓取）
    # run_goods_sync(force=True)
    run_goods_sync()
