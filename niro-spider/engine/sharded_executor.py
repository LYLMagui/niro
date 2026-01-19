import asyncio
import time
import random
import json
import os
from typing import List, Dict, Any, Optional, Set
from loguru import logger
from contextvars import ContextVar
import httpx
from aiolimiter import AsyncLimiter
from collections import deque

from engine.context import trace_id_var, task_id_var, account_id_var, account_name_var
from utils.browser_helper import BrowserProfile
from storage.redis_pool import redis_async as redis_client_async
from config.constants import REDIS_TASK_STOP_SIGNAL_PREFIX
from config.settings import BACKEND_URL

# 动态导入蜘蛛逻辑中的保存方法 (避免循环依赖)
def get_save_category_func():
    from spiders.get_buff_goods_category import save_categories
    return save_categories

def get_save_goods_func():
    from spiders.get_buff_goods import save_goods_batch
    return save_goods_batch

def get_save_sticker_func():
    from spiders.buff_sticker_spider import upsert_stickers
    return upsert_stickers

class AccountInvalidException(Exception):
    """账号失效异常 (401/429)"""
    def __init__(self, account_id: int, reason: str, category_ids: List[int]):
        self.account_id = account_id
        self.reason = reason
        self.category_ids = category_ids
        super().__init__(f"Account {account_id} invalid: {reason}")

class TaskStoppedException(Exception):
    """任务停止异常"""
    pass

class ShardedSpiderExecutor:
    def __init__(self, spider_client: httpx.AsyncClient, redis_async: Optional[Any] = None):
        self.client = spider_client
        self.redis_async = redis_async
        self.active_accounts: Dict[int, Dict[str, Any]] = {}
        self.account_limiters: Dict[int, AsyncLimiter] = {}
        self.pending_categories: Set[int] = set()
        self.finished_categories: Set[int] = set()
        self.task_id: Optional[int] = None
        self.user_id: Optional[int] = None
        self.task_type: Optional[int] = None
        
        # TPS 计算相关
        self._processed_history = deque(maxlen=100) # 记录 (timestamp, count)
        self._start_time = time.time()

    def _extract_value(self, data: Any) -> Any:
        """处理 Jackson 序列化可能带有的类型信息 (e.g. ['java.math.BigDecimal', 100.0])"""
        if isinstance(data, list) and len(data) == 2 and isinstance(data[0], str) and ("java." in data[0] or "com.niro" in data[0]):
            return self._extract_value(data[1])
        # 增加防御性：如果还是 list 且长度为 1，递归解包
        if isinstance(data, list) and len(data) == 1:
            return self._extract_value(data[0])
        return data

    def _extract_list(self, data: Any) -> List[Any]:
        """处理 Jackson 序列化可能带有的类型信息或嵌套 (e.g. ['java.util.ArrayList', [...]])"""
        raw_data = self._extract_value(data)
        if not raw_data:
            return []
        # 处理可能的双重嵌套: [[{...}]]
        if isinstance(raw_data, list) and len(raw_data) > 0 and isinstance(raw_data[0], list):
            return self._extract_list(raw_data[0])
        return raw_data if isinstance(raw_data, list) else [raw_data]

    def _get_float(self, data: Any, default: float = 0.0) -> float:
        """安全获取浮点数"""
        val = self._extract_value(data)
        if val is None:
            return default
        try:
            return float(val)
        except (ValueError, TypeError):
            return default

    async def execute(self, task_data: Dict[str, Any]):
        """执行分片抓取任务"""
        self.task_data = task_data
        self.task_id = self._extract_value(task_data.get("taskId"))
        self.user_id = self._extract_value(task_data.get("userId"))
        self.task_type = self._extract_value(task_data.get("taskType"))
        
        # 提取数据并处理 Jackson 序列化格式
        accounts = self._extract_list(task_data.get("accounts", []))
        category_ids = self._extract_list(task_data.get("categoryIds", []))
        
        # 提取请求间隔配置
        # 如果是系统任务 (task_type: 2=分类, 3=商品, 4=印花)，优先使用环境变量配置，除非 payload 中有明确的用户自定义标识
        if self.task_type in [2, 3, 4]:
            self.scan_interval_min = float(os.getenv("CRAWL_INTERVAL_MIN", 15))
            self.scan_interval_max = float(os.getenv("CRAWL_INTERVAL_MAX", 20))
            logger.info(f"[Task-ID: {self.task_id}] 检测到系统任务，使用系统间隔: {self.scan_interval_min}-{self.scan_interval_max}s")
        else:
            self.scan_interval_min = self._get_float(task_data.get("scanIntervalMin"), 15.0)
            self.scan_interval_max = self._get_float(task_data.get("scanIntervalMax"), 18.0)
            logger.info(f"[Task-ID: {self.task_id}] 使用用户配置间隔: {self.scan_interval_min}-{self.scan_interval_max}s")
        
        if not accounts:
            logger.error(f"[Task-ID: {self.task_id}] 没有可用账号")
            return

        # 重置任务状态
        self.pending_categories = set(category_ids)
        self.finished_categories = set()
        self.active_accounts = {acc["accountId"]: acc for acc in accounts}
        self.account_shards = {} # 初始化分片映射
        self._start_time = time.time()
        self._processed_history.clear()
        self.account_limiters.clear()
        
        # 初始化频率限制器
        for acc_id, acc in self.active_accounts.items():
            qps = acc.get("frequency") or 1.0
            self.account_limiters[acc_id] = AsyncLimiter(qps, 1)

        logger.info(f"🚀 [Task-ID: {self.task_id}] 启动分片执行器: {task_data.get('name')}, 账号数: {len(accounts)}, 待处理分类: {len(category_ids)}")

        final_status = 2 # 默认完成
        try:
            while self.pending_categories and self.active_accounts:
                # 检查停止信号
                if await self._check_stop_signal():
                    logger.info(f"🛑 [Task-ID: {self.task_id}] 收到停止信号，正在退出...")
                    final_status = 0
                    break

                # 1. 分片映射：均匀分配待抓取分类
                self.account_shards = self._shard_categories()
                
                # 2. 启动并发执行
                tasks = []
                for acc_id, shard_cats in self.account_shards.items():
                    if shard_cats:
                        tasks.append(self._execute_account_shard(acc_id, shard_cats))
                
                if not tasks:
                    break

                results = await asyncio.gather(*tasks, return_exceptions=True)
                
                # 3. 处理异常与动态剔除
                for res in results:
                    if isinstance(res, TaskStoppedException):
                        raise res # 向上抛出以退出主循环
                    elif isinstance(res, AccountInvalidException):
                        await self._handle_account_failure(res)
                    elif isinstance(res, Exception):
                        logger.error(f"[Task-ID: {self.task_id}] 抓取过程中发生未知错误: {res}")
                
                # 如果还有待处理任务，且刚才所有任务都失败了（或者发生了异常），增加一个小延迟防止死循环空转
                if self.pending_categories:
                    await asyncio.sleep(1)
        except TaskStoppedException:
            logger.info(f"🛑 [Task-ID: {self.task_id}] 任务已被手动停止")
            final_status = 0
        except Exception as e:
            logger.error(f"❌ [Task-ID: {self.task_id}] 任务执行异常: {e}")
            final_status = 3

        logger.info(f"🏁 [Task-ID: {self.task_id}] 分片抓取结束。完成: {len(self.finished_categories)}, 剩余: {len(self.pending_categories)}")
        
        # 回调后端更新状态
        await self._callback_status(final_status)

    async def _check_stop_signal(self, raise_exception: bool = False) -> bool:
        """检查 Redis 中的停止信号"""
        if not self.redis_async:
            return False
        stop_key = f"{REDIS_TASK_STOP_SIGNAL_PREFIX}{self.task_id}"
        is_stopped = await self.redis_async.exists(stop_key)
        if is_stopped and raise_exception:
            raise TaskStoppedException()
        return is_stopped

    async def _callback_status(self, status: int):
        """回调后端更新任务状态"""
        url = f"{BACKEND_URL}/task/callback/status"
        payload = {
            "id": self.task_id,
            "status": status
        }
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.post(url, json=payload)
                if resp.status_code == 200:
                    logger.info(f"✅ [Task-ID: {self.task_id}] 状态回调成功: {status}")
                else:
                    logger.error(f"❌ [Task-ID: {self.task_id}] 状态回调失败: {resp.status_code}, {resp.text}")
        except Exception as e:
            logger.error(f"❌ [Task-ID: {self.task_id}] 状态回调异常: {e}")

    def _shard_categories(self) -> Dict[int, List[int]]:
        """将 pending_categories 均匀分配给 active_accounts"""
        shards = {acc_id: [] for acc_id in self.active_accounts.keys()}
        cats = sorted(list(self.pending_categories)) # 排序确保确定性
        acc_ids = sorted(list(self.active_accounts.keys()))
        
        for i, cat_id in enumerate(cats):
            acc_id = acc_ids[i % len(acc_ids)]
            shards[acc_id].append(cat_id)
            
        return shards

    async def _execute_account_shard(self, account_id: int, category_ids: List[int]):
        """执行单个账号的分片抓取"""
        account = self.active_accounts[account_id]
        limiter = self.account_limiters[account_id]
        raw_name = account.get("accountName")
        # 移除 Acc- 前缀，仅保留纯账号标识
        account_name = raw_name.replace("Acc-", "") if raw_name else f"{account_id}"
        
        # 设置协程隔离上下文
        account_id_var.set(account_id)
        account_name_var.set(account_name)
        
        processed_cats = []
        try:
            for i, cat_id in enumerate(category_ids):
                # 每次处理新分类前检查停止信号
                await self._check_stop_signal(raise_exception=True)

                # 模拟人类操作的休息时间 (抓完一组后暂停 20-30 秒)
                if i > 0:
                    sleep_time = random.uniform(20, 30)
                    logger.info(f"💤 [账号: {account_name}] 完成一组抓取，休息 {sleep_time:.2f} 秒...")
                    # 暂停期间也要能响应停止信号
                    # 我们可以把长暂停拆分成短暂停，或者在 sleep 后立即检查
                    # 这里采用拆分暂停的方式
                    slept = 0
                    while slept < sleep_time:
                        await self._check_stop_signal(raise_exception=True)
                        chunk = min(2, sleep_time - slept)
                        await asyncio.sleep(chunk)
                        slept += chunk

                logger.info(f"🔍 [账号: {account_name}] 正在处理分类: {cat_id}")
                
                # 执行抓取 (将限流器传入内部，实现更细粒度的控制)
                success = False
                try:
                    success = await self._crawl_category(account, cat_id, limiter)
                except Exception as ce:
                    logger.exception(f"❌ [账号: {account_name}] 处理分类 {cat_id} 时发生未捕获异常: {ce}")
                
                if success:
                    processed_cats.append(cat_id)
                    self.finished_categories.add(cat_id)
                    self.pending_categories.remove(cat_id)
                    self._processed_history.append((time.time(), 1))
                    # 更新 Redis 进度与心跳
                    await self._update_progress()
                else:
                    logger.warning(f"⚠️ [账号: {account_name}] 处理分类失败: {cat_id}")
                        
        except AccountInvalidException as e:
            raise e
        except TaskStoppedException as e:
            raise e
        except Exception as e:
            logger.error(f"[账号: {account_name}] 抓取异常: {e}")
            
        return processed_cats

    async def _crawl_category(self, account: Dict[str, Any], category_id: int, limiter: AsyncLimiter) -> bool:
        """实际抓取逻辑 (适配不同任务类型)"""
        if self.task_type == 2: # 同步分类
            return await self._crawl_category_tree(account, category_id, limiter)
        elif self.task_type == 3: # 同步商品
            return await self._crawl_category_goods(account, category_id, limiter)
        elif self.task_type == 4: # 同步印花
            return await self._crawl_stickers(account, category_id, limiter)
        else:
            logger.error(f"Unknown task type: {self.task_type}")
            return False

    async def _crawl_stickers(self, account: Dict[str, Any], page_num: int, limiter: AsyncLimiter) -> bool:
        """同步印花逻辑：抓取指定页码的印花 (此时 category_id 被视为 page_num)"""
        acc_id = account["accountId"]
        raw_name = account.get("accountName")
        account_name = raw_name.replace("Acc-", "") if raw_name else f"{acc_id}"
        proxy = account.get("proxy")
        
        from spiders.buff_sticker_spider import fetch_stickers_api
        
        try:
            profile = BrowserProfile(
                cookie=account["buffCookie"],
                user_agent=account.get("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            )
            
            # 模拟人类随机延迟
            delay = random.uniform(self.scan_interval_min, self.scan_interval_max)
            logger.info(f"⏳ [账号: {account_name}] 第 {page_num} 页印花等待 {delay:.2f} 秒...")
            
            # 延迟期间检查停止信号
            slept = 0
            while slept < delay:
                await self._check_stop_signal(raise_exception=True)
                chunk = min(1, delay - slept)
                await asyncio.sleep(chunk)
                slept += chunk
            
            async with limiter:
                if proxy:
                    async with httpx.AsyncClient(proxy=proxy, timeout=10.0, verify=False) as proxy_client:
                        data = await fetch_stickers_api(proxy_client, page_num=page_num, profile=profile)
                else:
                    data = await fetch_stickers_api(self.client, page_num=page_num, profile=profile)
            
            if data and data.items:
                # 印花按页抓取，直接入库
                save_func = get_save_sticker_func()
                await save_func(data.items)
                logger.info(f"✅ [账号: {account_name}] 成功同步第 {page_num} 页印花 ({len(data.items)} 条)")
                return True
            
            return False
        except Exception as e:
            logger.error(f"❌ [账号: {account_name}] 同步印花第 {page_num} 页失败: {e}")
            return False

    async def _crawl_category_tree(self, account: Dict[str, Any], p_cat_id: int, limiter: AsyncLimiter) -> bool:
        """同步分类树逻辑：抓取一级分类下的所有二级分类"""
        acc_id = account["accountId"]
        raw_name = account.get("accountName")
        account_name = raw_name.replace("Acc-", "") if raw_name else f"{acc_id}"
        
        # 从 Payload 中获取分类元数据
        categories_meta = self.task_data.get("categoryMeta") or {}
        cat_meta = categories_meta.get(str(p_cat_id)) or {}
        
        type_internal = cat_meta.get("internalName")
        type_name = cat_meta.get("name")
        
        if not type_internal:
            logger.error(f"❌ [账号: {account_name}] Payload 中缺失分类 {p_cat_id} 的元数据，跳过该分类")
            return True # 返回 True 表示处理完成 (尽管是失败的)，防止主循环死循环

        logger.info(f"🚀 [账号: {account_name}] 正在同步分类树: {type_name} ({type_internal})")
        
        headers = {
            "Cookie": account["buffCookie"],
            "User-Agent": account.get("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        proxy = account.get("proxy")
        
        processed_in_type = set()
        redis_key = f"niro:spider:temp:category:{self.task_id}:{p_cat_id}"
        
        try:
            # 清理旧的临时数据
            if self.redis_async:
                await self.redis_async.delete(redis_key)

            page = 1
            # 默认抓取页数逻辑：other 为 50 页，其他为 20 页
            is_other = type_internal.lower() == "other"
            max_pages = 50 if is_other else 20
            
            consecutive_empty_count = 0 # 连续无新数据的页数
            logger.info(f"📊 [账号: {account_name}] 分类同步启动: {type_name}, 预设最大页数: {max_pages}")

            while page <= max_pages:
                # 模拟人类随机延迟
                delay = random.uniform(self.scan_interval_min, self.scan_interval_max)
                logger.info(f"⏳ [账号: {account_name}] 第 {page}/{max_pages} 页等待 {delay:.2f} 秒...")
                
                # 延迟期间检查停止信号
                slept = 0
                while slept < delay:
                    await self._check_stop_signal(raise_exception=True)
                    chunk = min(1, delay - slept)
                    await asyncio.sleep(chunk)
                    slept += chunk

                async with limiter:
                    url = "https://buff.163.com/api/market/goods"
                    params = {
                        "game": "csgo",
                        "page_num": page,
                        "tab": "selling",
                        "category_group": type_internal,
                        "sort_by": "price.asc"
                    }
                    
                    if proxy:
                        async with httpx.AsyncClient(proxy=proxy, timeout=self.client.timeout, verify=False) as proxy_client:
                            response = await proxy_client.get(url, headers=headers, params=params)
                    else:
                        response = await self.client.get(url, headers=headers, params=params)
                
                if response.status_code == 401:
                    raise AccountInvalidException(acc_id, "Login Required (401)", [p_cat_id])
                if response.status_code == 429:
                    raise AccountInvalidException(acc_id, "Rate Limited (429)", [p_cat_id])
                response.raise_for_status()
                
                data = response.json()
                if data.get("code") != "OK":
                    msg = data.get('msg', 'Unknown API Error')
                    logger.error(f"❌ [账号: {account_name}] API 错误: {msg}")
                    return False
                
                # 解析子分类
                items = data.get("data", {}).get("items", [])
                if not items:
                    logger.info(f"ℹ️ [账号: {account_name}] 第 {page} 页无数据")
                    if page == 1:
                        return False
                    break

                page_new_categories = []
                for item in items:
                    tags = item.get("goods_info", {}).get("info", {}).get("tags", {})
                    for tag_key, tag_info in tags.items():
                        if tag_key in ["type", "weapon"]:
                            internal_name = tag_info.get("internal_name")
                            if internal_name and internal_name not in processed_in_type:
                                processed_in_type.add(internal_name)
                                page_new_categories.append({
                                    "name": tag_info.get("localized_name"),
                                    "internal_name": internal_name,
                                    "parent_id": p_cat_id,
                                    "category_type": tag_key
                                })
                
                # 检查去重后的新数据
                if not page_new_categories:
                    consecutive_empty_count += 1
                    logger.info(f"ℹ️ [账号: {account_name}] 第 {page} 页无新分类 (连续 {consecutive_empty_count} 页)")
                else:
                    consecutive_empty_count = 0 # 重置计数
                    # 保存到 Redis
                    if self.redis_async:
                        await self.redis_async.rpush(redis_key, *[json.dumps(c, ensure_ascii=False) for c in page_new_categories])
                        logger.info(f"💾 [账号: {account_name}] 第 {page} 页抓取到 {len(page_new_categories)} 个新分类，已暂存 Redis")
                
                # 连续 3 页无新数据，认为已获取所有二级分类
                if consecutive_empty_count >= 3:
                    logger.info(f"✅ [账号: {account_name}] 连续 3 页无新数据，判定已获取所有二级分类，提前结束")
                    break
                
                page += 1
            
            # 抓取完所有页后，从 Redis 读取数据并统一入库
            has_data = False
            if self.redis_async:
                stored_items = await self.redis_async.lrange(redis_key, 0, -1)
                if stored_items:
                    all_sub_categories = [json.loads(i) for i in stored_items]
                    save_func = get_save_category_func()
                    await save_func(all_sub_categories)
                    logger.info(f"✅ [账号: {account_name}] 成功同步 {len(all_sub_categories)} 个子分类入库")
                    has_data = True
                    await self.redis_async.delete(redis_key)
            
            return has_data
            
        except Exception as e:
            if isinstance(e, AccountInvalidException): raise e
            logger.error(f"❌ [账号: {account_name}] 同步分类树失败: {e}")
            return False

    async def _crawl_category_goods(self, account: Dict[str, Any], category_id: int, limiter: AsyncLimiter) -> bool:
        """同步分类商品逻辑：抓取分类下的所有商品"""
        acc_id = account["accountId"]
        raw_name = account.get("accountName")
        account_name = raw_name.replace("Acc-", "") if raw_name else f"{acc_id}"
        
        # 从 Payload 中获取分类元数据
        categories_meta = self.task_data.get("categoryMeta") or {}
        cat_meta = categories_meta.get(str(category_id)) or {}
        
        internal_name = cat_meta.get("internalName")
        cat_type = cat_meta.get("categoryType") or "category"
        
        if not internal_name:
            logger.error(f"❌ [账号: {account_name}] Payload 中缺失分类 {category_id} 的元数据，跳过该分类")
            return True # 返回 True 表示处理完成 (尽管是失败的)，防止主循环死循环

        headers = {
            "Cookie": account["buffCookie"],
            "User-Agent": account.get("userAgent")
        }
        proxy = account.get("proxy")
        
        redis_key = f"niro:spider:temp:goods:{self.task_id}:{category_id}"
 
        try:
            # 清理旧数据
            if self.redis_async:
                await self.redis_async.delete(redis_key)

            page = 1
            total_pages = 1 # 初始页数，第一页请求后会更新
            max_safe_pages = 2000 # 安全上限，防止死循环

            logger.info(f"🚀 [账号: {account_name}] 商品同步启动: {internal_name} ({cat_type})")

            while page <= total_pages and page <= max_safe_pages:
                # 模拟人类随机延迟
                delay = random.uniform(self.scan_interval_min, self.scan_interval_max)
                logger.info(f"⏳ [账号: {account_name}] 第 {page}/{total_pages} 页等待 {delay:.2f} 秒...")
                
                # 延迟期间检查停止信号
                slept = 0
                while slept < delay:
                    await self._check_stop_signal(raise_exception=True)
                    chunk = min(1, delay - slept)
                    await asyncio.sleep(chunk)
                    slept += chunk

                async with limiter:
                    url = "https://buff.163.com/api/market/goods"
                    params = {
                        "game": "csgo",
                        "page_num": page,
                        "tab": "selling",
                        "sort_by": "price.asc"
                    }
                    params[cat_type] = internal_name
                    
                    if proxy:
                        async with httpx.AsyncClient(proxy=proxy, timeout=self.client.timeout, verify=False) as proxy_client:
                            response = await proxy_client.get(url, headers=headers, params=params)
                    else:
                        response = await self.client.get(url, headers=headers, params=params)
                
                if response.status_code == 401:
                    raise AccountInvalidException(acc_id, "Login Required (401)", [category_id])
                if response.status_code == 429:
                    raise AccountInvalidException(acc_id, "Rate Limited (429)", [category_id])
                response.raise_for_status()
                
                data = response.json()
                if data.get("code") != "OK":
                    msg = data.get('msg', 'Unknown API Error')
                    logger.error(f"❌ [账号: {account_name}] API 错误: {msg}")
                    return False # 直接返回 False，触发上层警告
                
                # 动态更新总页数
                if page == 1:
                    api_total = data.get("data", {}).get("total_page")
                    if api_total:
                        total_pages = min(api_total, max_safe_pages)
                        logger.info(f"📊 [账号: {account_name}] 检测到商品总页数: {total_pages}")
                    else:
                        logger.warning(f"⚠️ [账号: {account_name}] 无法获取总页数，可能该分类下无商品")

                items = data.get("data", {}).get("items", [])
                if not items:
                    logger.info(f"ℹ️ [账号: {account_name}] 第 {page} 页无数据")
                    if page == 1:
                        return False # 第一页就没数据，判定为失败
                    break
                
                page_goods = []
                for item in items:
                    page_goods.append({
                        "goods_id": item["id"],
                        "name": item["name"],
                        "market_hash_name": item.get("market_hash_name"),
                        "short_name": item.get("short_name"),
                        "category_id": category_id
                    })
                
                # 每页保存到 Redis
                if page_goods and self.redis_async:
                    await self.redis_async.rpush(redis_key, *[json.dumps(g, ensure_ascii=False) for g in page_goods])
                    logger.info(f"💾 [账号: {account_name}] 第 {page} 页抓取到 {len(page_goods)} 个商品，已暂存 Redis")
                
                page += 1
                    
            # 抓取完所有页后，从 Redis 读取数据并统一入库
            has_data = False
            if self.redis_async:
                stored_items = await self.redis_async.lrange(redis_key, 0, -1)
                if stored_items:
                    all_goods = [json.loads(i) for i in stored_items]
                    save_func = get_save_goods_func()
                    await save_func(all_goods, category_id, self.redis_async)
                    logger.info(f"✅ [账号: {account_name}] 成功同步 {len(all_goods)} 个商品入库")
                    has_data = True
                    await self.redis_async.delete(redis_key)
                
            return has_data
        except Exception as e:
            if isinstance(e, AccountInvalidException): raise e
            logger.error(f"❌ [账号: {account_name}] 同步商品失败: {e}")
            return False

    async def _handle_account_failure(self, exc: AccountInvalidException):
        """处理账号失效：剔除账号、通知后端、回收任务"""
        acc_id = exc.account_id
        reason = exc.reason
        logger.error(f"❌ 账号 {acc_id} 失效剔除! 原因: {reason}")
        
        # 1. 从活跃列表中移除
        if acc_id in self.active_accounts:
            del self.active_accounts[acc_id]
        if acc_id in self.account_limiters:
            del self.account_limiters[acc_id]
            
        # 2. 通知后端 API
        await self._report_account_invalid(acc_id, reason)

    async def _report_account_invalid(self, account_id: int, reason: str):
        """调用后端 API 标记账号失效"""
        try:
            # 优先从环境变量获取后端地址，默认为 localhost:8080
            backend_url = os.getenv("NIRO_BACKEND_URL", "http://localhost:8080")
            api_url = f"{backend_url}/buff/account/report/status"
            
            payload = {
                "id": account_id,
                "status": "INVALID",
                "warningMsg": reason
            }
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.post(api_url, json=payload)
                if resp.status_code == 200:
                    logger.info(f"✅ 已成功同步账号 {account_id} 失效状态至后端")
                else:
                    logger.warning(f"⚠️ 同步账号状态失败: {resp.status_code} - {resp.text}")
        except Exception as e:
            logger.error(f"❌ 报告账号状态异常: {e}")

    def _calculate_tps(self) -> float:
        """计算最近 1 分钟的 TPS"""
        now = time.time()
        one_min_ago = now - 60
        while self._processed_history and self._processed_history[0][0] < one_min_ago:
            self._processed_history.popleft()
        
        total_processed = sum(count for ts, count in self._processed_history)
        duration = min(60.0, now - self._start_time)
        return round(total_processed / duration, 2) if duration > 0 else 0

    async def _update_progress(self):
        """异步更新 Redis 中的任务进度与心跳"""
        if not self.task_id: return
        
        # 1. 计算账号级统计
        account_stats = {}
        for acc_id, items in self.account_shards.items():
            acc_info = self.active_accounts.get(acc_id, {})
            raw_name = acc_info.get("accountName")
            acc_name = raw_name.replace("Acc-", "") if raw_name else f"{acc_id}"
            
            total = len(items)
            finished = len([i for i in items if i in self.finished_categories])
            account_stats[acc_name] = {
                "total": total,
                "finished": finished,
                "percentage": round(finished / total * 100, 2) if total > 0 else 0,
                "tps": 0 # 暂时不计算单号 TPS
            }

        # 2. 更新任务统计数据
        total = len(self.pending_categories) + len(self.finished_categories)
        finished = len(self.finished_categories)
        progress = {
            "total": total,
            "finished": finished,
            "percentage": round(finished / total * 100, 2) if total > 0 else 0,
            "tps": self._calculate_tps(),
            "update_time": int(time.time()),
            "pending_categories": list(self.pending_categories),
            "account_stats": account_stats
        }
        
        stats_key = f"niro:stats:task:{self.task_id}"
        # 使用统一的异步 Redis 客户端
        if self.redis_async or redis_client_async:
            r = self.redis_async or redis_client_async
            await r.set(stats_key, json.dumps(progress, ensure_ascii=False), ex=3600)
            # 3. 更新任务心跳
            heartbeat_hash = "niro:task:heartbeat"
            await r.hset(heartbeat_hash, str(self.task_id), int(time.time() * 1000))
        else:
            logger.error("❌ 未找到可用的异步 Redis 客户端，进度更新失败")
