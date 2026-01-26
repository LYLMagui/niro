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
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type, before_sleep_log

from engine.context import trace_id_var, task_id_var, account_id_var, account_name_var
from utils.browser_helper import BrowserProfile
from utils.exception_handler import LoginRequiredError
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
        self.category_retry_counts: Dict[int, int] = {} # [新增] 失败重试计数器
        self.task_id: Optional[int] = None
        self.user_id: Optional[int] = None
        self.task_type: Optional[int] = None
        self.sync_tag: Optional[str] = None
        
        # TPS 计算相关
        self._processed_count = 0
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
        
        # 1. 版本管理：创建基于当前时间的唯一版本字符串
        self.sync_tag = f"v_{int(time.time())}"
        
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

        # [新增] 断点续传逻辑
        if self.redis_async:
            progress_key = f"niro:task:progress:{self.task_id}"
            finished_history = set()
            # 检查 Key 是否存在
            if await self.redis_async.exists(progress_key):
                raw_members = await self.redis_async.smembers(progress_key)
                if raw_members:
                    finished_history = {int(m) for m in raw_members}
                    # 计算剩余任务
                    self.pending_categories = self.pending_categories - finished_history
                    logger.info(f"🔄 [Task-ID: {self.task_id}] 检测到历史进度: 已完成 {len(finished_history)} 个，剩余 {len(self.pending_categories)} 个，准备续传...")
            
            # 如果剩余为 0，直接结束
            if not self.pending_categories:
                if not category_ids:
                    logger.warning(f"⚠️ [Task-ID: {self.task_id}] 初始分类列表为空！请检查后端任务下发逻辑或数据库分类表。")
                else:
                    logger.info(f"✅ [Task-ID: {self.task_id}] 任务已全部完成 (基于历史进度)")
                
                await self._callback_status(2)
                return
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

        # 回调后端更新状态为运行中
        await self._callback_status(1)

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
        
        # [新增] 任务成功完成后清理进度 Key
        if final_status == 2 and self.redis_async:
            progress_key = f"niro:task:progress:{self.task_id}"
            await self.redis_async.delete(progress_key)
        
        # 检查死信队列
        if self.redis_async:
            dlq_key = f"niro:task:{self.task_id}:failed_categories"
            failed_count = await self.redis_async.llen(dlq_key)
            if failed_count > 0:
                logger.critical(f"💀 [Task-ID: {self.task_id}] 任务存在 {failed_count} 个死信分类，请检查 Redis 队列: {dlq_key}")

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
        """将 pending_categories 根据账号权重均匀分配给 active_accounts (优先选择 NORMAL 账号)"""
        # 1. 筛选目标账号
        normal_accounts = [acc_id for acc_id, acc in self.active_accounts.items() if acc.get("role") == "NORMAL"]
        target_acc_ids = sorted(normal_accounts if normal_accounts else list(self.active_accounts.keys()))
        
        if not target_acc_ids:
            return {}

        # 2. 获取并计算权重
        weights = {acc_id: max(1, int(self.active_accounts[acc_id].get("weight") or 1)) for acc_id in target_acc_ids}
        total_weight = sum(weights.values())
        
        # 核心：使用确定性排序确保分片一致性
        cats = sorted(list(self.pending_categories))
        total_cats = len(cats)
        
        if total_cats == 0:
            return {acc_id: [] for acc_id in target_acc_ids}

        # 3. 基于权重的分配逻辑 (确定性加权轮询)
        shards = {acc_id: [] for acc_id in target_acc_ids}
        
        allocated_count = 0
        for i, acc_id in enumerate(target_acc_ids):
            if i == len(target_acc_ids) - 1:
                # 最后一个账号承包剩余所有任务，确保不漏掉
                count = total_cats - allocated_count
            else:
                # 按比例分配，使用 floor 确保不越界
                count = int((weights[acc_id] / total_weight) * total_cats)
            
            # 确定性地取出对应数量的分类
            shard_cats = cats[allocated_count : allocated_count + count]
            shards[acc_id].extend(shard_cats)
            allocated_count += count

        # 打印分配详情，验证负载均衡
        weight_desc = ", ".join([f"{self.active_accounts[aid].get('accountName')}(W:{weights[aid]}, N:{len(shards[aid])})" for aid in target_acc_ids])
        logger.info(f"⚖️ [Task-ID: {self.task_id}] 负载均衡分片: {weight_desc}")
            
        return shards

    async def _execute_account_shard(self, account_id: int, category_ids: List[int]):
        """执行单个账号的分片抓取 (生产者-消费者模式)"""
        account = self.active_accounts[account_id]
        limiter = self.account_limiters[account_id]
        raw_name = account.get("accountName")
        account_name = raw_name.replace("Acc-", "") if raw_name else f"{acc_id}"
        
        # 设置协程隔离上下文
        account_id_var.set(account_id)
        account_name_var.set(account_name)
        
        # 初始化队列与信号量
        queue = asyncio.Queue()
        # [优化] 将并发数从 3 降低为 1，避免多 Worker 同时请求导致请求频率过快触发 429
        # 如果需要更高并发，应通过增加账号数量来实现，而不是单账号高并发
        concurrency = 1 
        semaphore = asyncio.Semaphore(concurrency)
        
        processed_cats = []
        total_shard_count = len(category_ids)
        # 进度追踪 (使用 list 包装以在闭包中修改)
        progress_tracker = {"processed": 0, "success": 0}
        
        shard_start_time = time.time()
        shard_exception = None # 用于捕获 Worker 中的致命异常
        recent_history = deque() # [新增] 用于存储最近 60 秒的请求时间戳
        
        # --- 消费者 (Worker) ---
        async def worker():
            nonlocal shard_exception
            while True:
                try:
                    # 如果已有致命异常，快速消费队列并退出
                    if shard_exception:
                        try:
                            _ = queue.get_nowait()
                            queue.task_done()
                        except asyncio.QueueEmpty:
                            break
                        continue

                    try:
                        cat_id = await queue.get()
                    except asyncio.CancelledError:
                        break
                    
                    try:
                        async with semaphore:
                            # 检查停止信号
                            if await self._check_stop_signal(raise_exception=False):
                                queue.task_done()
                                continue
                            
                            if shard_exception: 
                                queue.task_done()
                                continue

                            # 获取元数据用于日志
                            categories_meta = self.task_data.get("categoryMeta") or {}
                            cat_meta = categories_meta.get(str(cat_id)) or {}
                            cat_name = cat_meta.get("name") or f"ID:{cat_id}"
                            
                            idx = progress_tracker["processed"]
                            progress = (idx / total_shard_count) * 100
                            elapsed = time.time() - shard_start_time
                            
                            # [新增] 计算最近 1 分钟实时 RPM (Requests Per Minute)
                            now = time.time()
                            while recent_history and recent_history[0] < now - 60:
                                recent_history.popleft()
                            
                            # 模仿扫货任务的逻辑：直接统计最近 60s 内的请求数，不进行除法归一化
                            current_rpm = len(recent_history)

                            if account_id in self.active_accounts:
                                # 前端可能仍使用 current_tps 字段，这里存 RPM 数值
                                self.active_accounts[account_id]["current_tps"] = current_rpm

                            logger.info(f"🔄 [Worker] 进度: {progress:.1f}% ({idx}/{total_shard_count}) | RPM: {current_rpm}/m | 处理分类: {cat_name}")

                            # 执行抓取，传入 recent_history 以便内部实时更新 RPM
                            success, p_count = await self._crawl_category(account, cat_id, limiter, request_history=recent_history)
                            
                            # 注意：内部已经更新了 recent_history，这里不需要再次 append
                            # 如果内部没有更新（比如抛出异常），这里可能需要补充，但为了避免复杂，
                            # 我们假设内部函数负责所有请求相关的计数。
                            
                            if success:
                                processed_cats.append(cat_id)
                                self.finished_categories.add(cat_id)
                                if cat_id in self.pending_categories:
                                    self.pending_categories.remove(cat_id)
                                progress_tracker["success"] += 1
                                await self._update_progress()

                                # [新增] Redis 持久化进度
                                if self.redis_async:
                                    progress_key = f"niro:task:progress:{self.task_id}"
                                    await self.redis_async.sadd(progress_key, cat_id)
                                    await self.redis_async.expire(progress_key, 86400 * 7) # 续期 7 天

                                    # 成功后清除重试计数
                                    if cat_id in self.category_retry_counts:
                                        del self.category_retry_counts[cat_id]
                            else:
                                # [新增] 失败重试逻辑
                                current_retry = self.category_retry_counts.get(cat_id, 0) + 1
                                self.category_retry_counts[cat_id] = current_retry
                                
                                if current_retry >= 3: # 最大重试 3 次
                                    logger.error(f"❌ 分类 {cat_name} (ID:{cat_id}) 重试次数耗尽 ({current_retry}/3)，移入死信队列")
                                    if cat_id in self.pending_categories:
                                        self.pending_categories.remove(cat_id) # 放弃该任务，避免死循环
                                    
                                    # 写入 Redis 死信队列
                                    if self.redis_async:
                                        dlq_key = f"niro:task:dlq:{self.task_id}"
                                        await self.redis_async.rpush(dlq_key, cat_id)
                                else:
                                    logger.warning(f"⚠️ 分类 {cat_name} 处理失败，将在下一轮重试 ({current_retry}/3)")

                    except AccountInvalidException as ae:
                        logger.warning(f"⚠️ 账号失效 ({ae.reason})，触发分片熔断")
                        shard_exception = ae
                    except Exception as e:
                        logger.exception(f"❌ Worker 异常: {e}")
                    finally:
                        progress_tracker["processed"] += 1
                        queue.task_done()
                        
                        # 模拟休息 (Worker 级)
                        if not shard_exception and idx < total_shard_count - 1:
                            # 并发模式下适当减少休息时间
                            await asyncio.sleep(random.uniform(5, 10))

                except asyncio.CancelledError:
                    break
        
        # --- 生产者 (Producer) ---
        for cat_id in category_ids:
            queue.put_nowait(cat_id)
            
        # 启动 Workers
        workers = [asyncio.create_task(worker()) for _ in range(concurrency)]
        
        try:
            await queue.join()
        except Exception as e:
            logger.error(f"Shard Execution Interrupted: {e}")
        finally:
            for w in workers:
                w.cancel()
            await asyncio.gather(*workers, return_exceptions=True)
            
        if shard_exception:
            raise shard_exception
            
        return processed_cats

    async def _crawl_category(self, account: Dict[str, Any], category_id: int, limiter: AsyncLimiter, request_history: Optional[deque] = None) -> (bool, int):
        """实际抓取逻辑 (适配不同任务类型)"""
        if self.task_type == 2: # 同步分类
            return await self._crawl_category_tree(account, category_id, limiter, request_history=request_history), 0
        elif self.task_type == 3: # 同步商品
            return await self._crawl_category_goods(account, category_id, limiter, request_history=request_history)
        elif self.task_type == 4: # 同步印花
            return await self._crawl_stickers(account, category_id, limiter, request_history=request_history), 0
        else:
            logger.error(f"Unknown task type: {self.task_type}")
            return False, 0

    async def _crawl_stickers(self, account: Dict[str, Any], page_num: int, limiter: AsyncLimiter, request_history: Deque[float] = None) -> bool:
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
            
            # [新增] RPM 计算与日志
            rpm = 0
            if request_history is not None:
                now = time.time()
                while request_history and request_history[0] < now - 60:
                    request_history.popleft()
                rpm = len(request_history)
                # 更新到 account 对象以便前端展示 (可选)
                account["current_tps"] = rpm
                
            logger.info(f"⏳ [印花] 第 {page_num} 页等待 {delay:.2f} 秒... | RPM: {rpm}/m")
            
            # 延迟期间检查停止信号
            slept = 0
            while slept < delay:
                await self._check_stop_signal(raise_exception=True)
                chunk = min(1, delay - slept)
                await asyncio.sleep(chunk)
                slept += chunk
            
            async with limiter:
                # 记录请求
                if request_history is not None:
                    request_history.append(time.time())
                    
                if proxy:
                    async with httpx.AsyncClient(proxy=proxy, timeout=10.0, verify=False) as proxy_client:
                        data = await fetch_stickers_api(proxy_client, page_num=page_num, profile=profile)
                else:
                    data = await fetch_stickers_api(self.client, page_num=page_num, profile=profile)
            
            if data and data.items:
                # 印花按页抓取，直接入库
                save_func = get_save_sticker_func()
                save_success = await save_func(data.items, self.redis_async)
                if save_success:
                    logger.info(f"✅ 成功同步第 {page_num} 页印花 ({len(data.items)} 条)")
                    return True
                else:
                    logger.error(f"❌ 同步印花第 {page_num} 页失败: 保存数据失败")
                    return False
            
            return False
        except LoginRequiredError as le:
            raise AccountInvalidException(acc_id, str(le), [page_num])
        except Exception as e:
            if isinstance(e, AccountInvalidException): raise e
            logger.error(f"❌ 同步印花第 {page_num} 页失败: {e}")
            return False

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        retry=retry_if_exception_type((httpx.TimeoutException, httpx.ConnectError, httpx.ReadTimeout, ConnectionError)),
        before_sleep=before_sleep_log(logger, "WARNING")
    )
    async def _crawl_category_tree(self, account: Dict[str, Any], p_cat_id: int, limiter: AsyncLimiter, request_history: Deque[float] = None) -> bool:
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
            logger.error(f"❌ Payload 中缺失分类 {p_cat_id} 的元数据，跳过该分类")
            return True # 返回 True 表示处理完成 (尽管是失败的)，防止主循环死循环

        logger.info(f"🚀 正在同步分类树: {type_name} ({type_internal})")
        
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
            logger.info(f"📊 分类同步启动: {type_name}, 预设最大页数: {max_pages}")

            while page <= max_pages:
                # 模拟人类随机延迟
                delay = random.uniform(self.scan_interval_min, self.scan_interval_max)
                
                # [新增] RPM 计算与日志
                rpm = 0
                if request_history is not None:
                    now = time.time()
                    while request_history and request_history[0] < now - 60:
                        request_history.popleft()
                    rpm = len(request_history)
                    account["current_tps"] = rpm
                    
                logger.info(f"⏳ [{type_name}] 第 {page}/{max_pages} 页等待 {delay:.2f} 秒... | RPM: {rpm}/m")
                
                # 延迟期间检查停止信号
                slept = 0
                while slept < delay:
                    await self._check_stop_signal(raise_exception=True)
                    chunk = min(1, delay - slept)
                    await asyncio.sleep(chunk)
                    slept += chunk

                async with limiter:
                    # 记录请求
                    if request_history is not None:
                        request_history.append(time.time())
                        
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
                
                # 预检：如果返回的是 HTML，说明 Cookie 已失效
                resp_text = response.text
                if resp_text.strip().startswith("<!DOCTYPE") or resp_text.strip().startswith("<html"):
                    raise AccountInvalidException(acc_id, "Login Required (HTML Redirect)", [p_cat_id])
                    
                response.raise_for_status()
                
                try:
                    data = response.json()
                except json.JSONDecodeError as e:
                    logger.error(f"❌ JSON 解析失败: {e}, 响应内容: {resp_text[:100]}...")
                    break
                except Exception as e:
                    if "code" in str(e):
                        msg = str(e)
                        logger.error(f"❌ API 错误: {msg}")
                        break
                    raise e

                items = data.get("data", {}).get("items")
                if not data or not items:
                    logger.info(f"ℹ️ 第 {page} 页无数据")
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
                    logger.info(f"ℹ️ 第 {page} 页无新分类 (连续 {consecutive_empty_count} 页)")
                else:
                    consecutive_empty_count = 0 # 重置计数
                    # 保存到 Redis
                    if self.redis_async:
                        await self.redis_async.rpush(redis_key, *[json.dumps(c, ensure_ascii=False) for c in page_new_categories])
                        logger.info(f"💾 第 {page} 页抓取到 {len(page_new_categories)} 个新分类，已暂存 Redis")
                
                # 动态设置提前退出的阈值：Other 分类为 5 页，其他为 3 页
                threshold = 5 if is_other else 3
                if consecutive_empty_count >= threshold:
                    logger.info(f"✅ 连续 {consecutive_empty_count} 页无新数据 (阈值: {threshold})，判定已获取所有二级分类，提前结束")
                    break
                
                page += 1
            
            # 抓取完所有页后，从 Redis 读取数据并统一入库
            has_data = False
            if self.redis_async:
                stored_items = await self.redis_async.lrange(redis_key, 0, -1)
                if stored_items:
                    all_sub_categories = [json.loads(i) for i in stored_items]
                    save_func = get_save_category_func()
                    save_success = await save_func(all_sub_categories, self.redis_async, parent_id=p_cat_id)
                    
                    if save_success:
                        logger.info(f"✅ 成功同步 {len(all_sub_categories)} 个子分类入库")
                        has_data = True
                        await self.redis_async.delete(redis_key)
                    else:
                        logger.error(f"❌ 同步子分类入库失败，保留 Redis 数据")
                        return False
            
            return has_data
            
        except Exception as e:
            if isinstance(e, AccountInvalidException): raise e
            logger.exception(f"❌ 同步分类树失败: {e}")
            return False

    async def _crawl_category_goods(self, account: Dict[str, Any], category_id: int, limiter: AsyncLimiter, request_history: Deque[float] = None) -> (bool, int):
        """同步分类商品逻辑：抓取分类下的所有商品 (支持增量更新与 Hash 跳过)"""
        acc_id = account["accountId"]
        raw_name = account.get("accountName")
        account_name = raw_name.replace("Acc-", "") if raw_name else f"{acc_id}"
        
        # 从 Payload 中获取分类元数据
        categories_meta = self.task_data.get("categoryMeta") or {}
        cat_meta = categories_meta.get(str(category_id)) or {}
        
        cat_name = cat_meta.get("name") or f"ID:{category_id}"
        internal_name = cat_meta.get("internalName")
        cat_type = cat_meta.get("categoryType") or "category"
        
        if not internal_name:
            logger.error(f"❌ Payload 中缺失分类 {cat_name} 的元数据，跳过该分类")
            return True, 0 

        headers = {
            "Cookie": account["buffCookie"],
            "User-Agent": account.get("userAgent")
        }
        proxy = account.get("proxy")
        
        redis_key = f"niro:spider:temp:goods:{self.task_id}:{category_id}"
        hash_key = f"niro:spider:hash:category:{category_id}"
        total_processed_count = 0
 
        try:
            # 清理旧数据
            if self.redis_async:
                await self.redis_async.delete(redis_key)

            page = 1
            total_pages = 1 
            max_safe_pages = 2000 

            logger.info(f"🚀 商品同步启动: {cat_name} ({internal_name}) | 版本: {self.sync_tag}")

            while page <= total_pages and page <= max_safe_pages:
                # 模拟人类随机延迟 (分段变速策略)
                delay = random.uniform(self.scan_interval_min, self.scan_interval_max)
                page_info = f"{page}/{total_pages}" if total_pages > 1 else f"{page}"
                
                # [新增] RPM 计算与日志
                rpm = 0
                if request_history is not None:
                    now = time.time()
                    while request_history and request_history[0] < now - 60:
                        request_history.popleft()
                    rpm = len(request_history)
                    account["current_tps"] = rpm

                logger.info(f"⏳ [{cat_name}] 第 {page_info} 页等待 {delay:.2f} 秒... | RPM: {rpm}/m")
                
                # 延迟期间检查停止信号
                slept = 0
                while slept < delay:
                    await self._check_stop_signal(raise_exception=True)
                    chunk = min(1, delay - slept)
                    await asyncio.sleep(chunk)
                    slept += chunk

                async with limiter:
                    # 记录请求
                    if request_history is not None:
                        request_history.append(time.time())

                    url = "https://buff.163.com/api/market/goods"
                    params = {
                        "game": "csgo",
                        "page_num": page,
                        "tab": "selling",
                        "sort_by": "price.asc",
                        "category": internal_name
                    }
                    
                    if proxy:
                        async with httpx.AsyncClient(proxy=proxy, timeout=self.client.timeout, verify=False) as proxy_client:
                            response = await proxy_client.get(url, headers=headers, params=params)
                    else:
                        response = await self.client.get(url, headers=headers, params=params)
                
                if response.status_code == 401:
                    raise AccountInvalidException(acc_id, "Login Required (401)", [category_id])
                if response.status_code == 429:
                    raise AccountInvalidException(acc_id, "Rate Limited (429)", [category_id])
                
                # 预检：如果返回的是 HTML，说明 Cookie 已失效
                resp_text = response.text
                if resp_text.strip().startswith("<!DOCTYPE") or resp_text.strip().startswith("<html"):
                    raise AccountInvalidException(acc_id, "Login Required (HTML Redirect)", [category_id])
                    
                response.raise_for_status()
                
                try:
                    data = response.json()
                except Exception as e:
                    logger.error(f"❌ JSON 解析失败: {e}, 响应内容: {resp_text[:100]}...")
                    raise AccountInvalidException(acc_id, "Invalid JSON Response", [category_id])
                
                if data.get("code") != "OK":
                    msg = data.get('msg', 'Unknown API Error')
                    logger.error(f"❌ API 错误: {msg}")
                    return False, total_processed_count 
                
                items = data.get("data", {}).get("items", [])
                
                # 3. Hash 跳过机制：仅在第一页检查
                if page == 1 and self.redis_async:
                    import hashlib
                    # 提取第一页商品 ID 并排序计算 Hash
                    item_ids = sorted([str(item["id"]) for item in items])
                    current_hash = hashlib.md5(",".join(item_ids).encode()).hexdigest()
                    
                    old_hash = await self.redis_async.get(hash_key)
                    if old_hash and old_hash == current_hash:
                        logger.info(f"⏭️ 分类 {cat_name} 数据未变动 (Hash 命中)，跳过后续同步")
                        return True, 0
                    
                    # 记录新 Hash
                    await self.redis_async.set(hash_key, current_hash, ex=86400 * 7) # 有效期 7 天

                # 动态更新总页数
                if page == 1:
                    api_total = data.get("data", {}).get("total_page")
                    if api_total:
                        total_pages = min(api_total, max_safe_pages)
                        logger.info(f"📊 检测到商品总页数: {total_pages}")
                    else:
                        logger.warning(f"⚠️ 无法获取总页数，可能该分类下无商品")

                if not items:
                    logger.info(f"ℹ️ 第 {page} 页无数据")
                    if page == 1:
                        return False, total_processed_count 
                    break
                
                # 4. 暂存数据到 Redis
                if self.redis_async:
                    await self.redis_async.rpush(redis_key, *[json.dumps(item, ensure_ascii=False) for item in items])
                    await self.redis_async.expire(redis_key, 7200)  # 1小时有效期
                
                total_processed_count += len(items)
                self._processed_count += len(items)
                self._processed_history.append((time.time(), len(items)))
                
                # 5. 分批落库机制 (每 5 页保存一次)
                if page % 5 == 0 and self.redis_async:
                    stored_items = await self.redis_async.lrange(redis_key, 0, -1)
                    if stored_items:
                        page_goods = [json.loads(i) for i in stored_items]
                        save_func = get_save_goods_func()
                        saved_count = await save_func(page_goods, category_id, self.redis_async, self.sync_tag, cat_name)
                        
                        if saved_count > 0:
                            await self.redis_async.delete(redis_key)
                            logger.info(f"🔄 [分批保存] 已处理前 {page} 页数据 ({len(page_goods)} 条)")
                        else:
                            logger.warning(f"⚠️ [分批保存] 写入失败 (0条)，保留 Redis 暂存数据以供重试")

                page += 1

            # --- 全部分页抓取完成后，执行批量保存 (处理剩余数据) ---
            if total_processed_count > 0 and self.redis_async:
                # 从 Redis 读取剩余暂存数据
                stored_items = await self.redis_async.lrange(redis_key, 0, -1)
                if stored_items:
                    logger.info(f"💾 分类 {cat_name} 抓取完毕，开始入库剩余 {len(stored_items)} 条数据...")
                    page_goods = [json.loads(i) for i in stored_items]
                    
                    # 批量保存商品
                    save_func = get_save_goods_func()
                    rows = await save_func(page_goods, category_id, self.redis_async, self.sync_tag, cat_name)
                    
                    if rows > 0:
                        logger.info(f"✅ 分类 {cat_name} 剩余数据入库成功: {rows} 条受影响")
                        # 清理 Redis 暂存数据
                        await self.redis_async.delete(redis_key)
                    else:
                        logger.error(f"❌ 分类 {cat_name} 数据入库失败 (0条)，保留 Redis 数据")
                else:
                    logger.info(f"✅ 分类 {cat_name} 抓取完毕 (数据已全部分批入库)")
                
                # [Fix] 最终检查：如果 Redis 中仍有暂存数据，说明保存失败，任务应标记为失败
                remaining = await self.redis_async.llen(redis_key)
                if remaining > 0:
                    logger.error(f"❌ 分类 {cat_name} 仍有 {remaining} 条数据滞留 Redis 未入库，标记为任务失败")
                    return False, total_processed_count

            return True, total_processed_count
            
        except AccountInvalidException as ae:
            raise ae
        except Exception as e:
                logger.error(f"❌ 同步分类 {cat_name} 商品失败: {repr(e)}")
                return False, total_processed_count

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
                "tps": acc_info.get("current_tps", 0)
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
