import asyncio
import time
import random
import json
import uuid
from typing import List, Dict, Any, Optional, Union
import httpx
from loguru import logger
from collections import deque

from engine.context import set_context, account_id_var, account_name_var
from utils.browser_helper import BrowserHelper, BrowserProfile
from utils.proxy_helper import get_proxies

from redis.asyncio import Redis

from engine.sharded_executor import ShardedSpiderExecutor
from config.constants import REDIS_TASK_STOP_SIGNAL_PREFIX, REDIS_TASK_LAST_SCAN_PREFIX, REDIS_TASK_NEXT_SLOT_PREFIX, SCAN_ADMISSION_INTERVAL, REDIS_TASK_STATS_PREFIX
from config.settings import BACKEND_URL
from utils.notifier import Notifier
from utils.exception_handler import LoginRequiredError
from enums.buff_enums import BuffPaymentMethod, BuffGameType

class AsyncBuffSpider:
    def __init__(self, redis: Optional[Redis] = None):
        self.redis = redis
        self.notifier = Notifier()
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(10.0, connect=5.0),
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
            follow_redirects=True
        )
        # 记录账号上一次成功下单的时间戳，用于 Sequential Guard
        self._last_buy_times = {}
        
        # TPS 计算相关：改为按 account_id 隔离的字典
        # key: account_id, value: deque[(timestamp, count)]
        self._account_history = {}
        self._start_time = time.time()

    async def close(self):
        await self.client.aclose()

    async def _request(self, method: str, url: str, profile: BrowserProfile, proxy: Optional[str] = None, return_raw: bool = False, **kwargs) -> Union[Dict[str, Any], httpx.Response]:
        """封装基础请求逻辑，包含自动重试和代理切换"""
        # 1. 如果 kwargs 中包含 headers，先弹出
        extra_headers = kwargs.pop("headers", {})
        
        # 2. 获取基础 Headers (包含基础 User-Agent, cookie 等)
        # 如果是 goods 相关的 API，自动构造 referer
        referer = None
        if "goods_id=" in url or "/goods/" in url:
            import re
            match = re.search(r'goods_id=(\d+)', url) or re.search(r'/goods/(\d+)', url)
            if match:
                referer = f"https://buff.163.com/goods/{match.group(1)}?from=market"
        
        headers = profile.get_headers(referer=referer) if referer else profile.get_headers()
        
        # 3. 合并自定义 Headers 并强制所有 Key 为小写
        # 这样可以彻底解决 X-CSRFToken 与 x-csrftoken 同时存在导致的 CSRF 校验失败
        final_headers = {k.lower(): v for k, v in headers.items()}
        if isinstance(extra_headers, dict):
            for k, v in extra_headers.items():
                final_headers[k.lower()] = v
        
        # 如果方法参数没传 proxy，则尝试从全局配置获取
        if not proxy:
            proxies = get_proxies()
            proxy = proxies.get("http") if proxies else None
        
        for attempt in range(3):
            # httpx 不支持在 request 中传 proxy，必须在 Client 构造时传
            # 这里为了灵活性，如果需要使用特定代理，我们创建一个临时的 Client（或者你可以维护一个 Client 池）
            # 对于爬虫来说，通常一个任务内的账号共享代理，或者每个账号固定代理
            client = self.client
            if proxy:
                # 注意：频繁创建 Client 有开销，但对于带代理的请求，这是 httpx 的标准做法
                # 如果性能受限，可以考虑在 executor 层为每个 proxy 维护一个长连接 Client
                async with httpx.AsyncClient(
                    proxy=proxy,
                    timeout=self.client.timeout,
                    follow_redirects=True,
                    verify=False # 某些代理可能需要关闭验证
                ) as proxy_client:
                    return await self._do_request(proxy_client, method, url, final_headers, attempt, profile, return_raw=return_raw, **kwargs)
            else:
                return await self._do_request(client, method, url, final_headers, attempt, profile, return_raw=return_raw, **kwargs)
        
        return {"error": "MAX_RETRIES_EXCEEDED"}

    async def _do_request(self, client: httpx.AsyncClient, method: str, url: str, headers: dict, attempt: int, profile: BrowserProfile, return_raw: bool = False, **kwargs) -> Union[Dict[str, Any], httpx.Response]:
        try:
            response = await client.request(method, url, headers=headers, **kwargs)
            
            # 立即同步服务器下发的新 Cookie (如 CSRF Token)，确保后续请求携带最新凭证
            if profile:
                profile.update_cookies(response.cookies)

            if response.status_code == 403:
                # 记录 403 详细信息
                logger.error(f"❌ [403 Forbidden] 响应内容: {response.text[:200]}")
                logger.error(f"❌ [403 Forbidden] 请求头中 x-csrftoken: {headers.get('x-csrftoken')}")
            
            if response.status_code == 429:
                logger.error(f"🚫 [429] 触发频率限制，等待重试 ({attempt + 1}/3)")
                await asyncio.sleep(2 ** attempt + random.random())
                return await self._do_request(client, method, url, headers, attempt + 1, profile, return_raw=return_raw, **kwargs) if attempt < 2 else {"error": "RATE_LIMITED"}
            
            # 如果要求返回原始 Response，直接返回（不进行 code/html 检查，由调用方处理）
            if return_raw:
                return response

            response.raise_for_status()
            
            # 预检：如果返回的是 HTML，说明 Cookie 已失效或被重定向到登录页
            resp_text = response.text
            # 只有在请求 Buff 域名且返回 HTML 时才判定为登录失效
            if "buff.163.com" in str(url) and (resp_text.strip().startswith("<!DOCTYPE") or resp_text.strip().startswith("<html")):
                logger.error(f"🔑 [Cookie失效] 收到 HTML 登录重定向响应")
                raise LoginRequiredError("Buff Login Required (HTML Redirect)")

            try:
                data = response.json()
            except Exception as e:
                # 如果 URL 包含 epay.163.com，说明是激活链接，不需要解析 JSON
                if "epay.163.com" in str(url):
                    return {"code": "OK", "msg": "Epay Activation Hit"}
                
                logger.error(f"解析 JSON 失败: {e}, 响应内容: {resp_text[:100]}...")
                raise LoginRequiredError("Buff Response Parse Failed")
            
            if data.get("code") == "Login Required":
                logger.error("🔑 [Cookie失效] 账号需要重新登录")
                raise LoginRequiredError("Buff Login Required")
            
            return data
            
        except LoginRequiredError:
            raise
        except Exception as e:
            logger.error(f"🌐 [请求异常] {url} 错误: {e}")
            if attempt >= 2:
                raise
            await asyncio.sleep(1)
            # 这里递归调用可能有问题，改为外层循环更好，但先这样修复参数错误
            return {"error": "REQUEST_FAILED"}

    async def get_goods_list(self, goods_id: int, profile: BrowserProfile, page_num: int = 1) -> List[Dict[str, Any]]:
        """获取饰品在售列表 (异步版)"""
        url = f"https://buff.163.com/api/market/goods/sell_order"
        params = {
            "game": "csgo",
            "goods_id": goods_id,
            "page_num": page_num,
            "sort_by": "price.asc",
            "mode": "",
            "allow_tradable_cooldown": "1",
            "_": int(time.time() * 1000)
        }
        
        data = await self._request("GET", url, profile, params=params)
        
        if data.get("code") == "OK":
            payload = data.get("data", {})
            items = payload.get("items", [])
            goods_infos = payload.get("goods_infos", {})
            if items and goods_infos:
                for item in items:
                    goods_id = item.get("goods_id")
                    goods_info = goods_infos.get(str(goods_id)) if goods_id is not None else None
                    if goods_info:
                        item["sell_min_price"] = goods_info.get("sell_min_price")
                        item["buy_max_price"] = goods_info.get("buy_max_price")
            return items
        
        return []

    async def _get_dynamic_wait_time(self, task_id: str, ideal_interval: float) -> float:
        """
        核心硬核逻辑：基于 Redis 令牌桶的动态准入控制 (Admission Control)
        实现原理：
        1. 使用 Lua 脚本保证 Redis 操作的原子性。
        2. 维护一个 'next_slot' 时间戳，表示下一个账号可以发起请求的最早时间。
        3. 账号申请准入时，如果当前时间早于 next_slot，则计算延迟，并将 next_slot 向后推移。
        4. 如果当前时间晚于 next_slot，说明系统有空闲，直接从当前时间开始推移。
        """
        # Lua 脚本：原子地获取并更新槽位
        # KEYS[1]: next_slot_key
        # ARGV[1]: current_time (float)
        # ARGV[2]: ideal_interval (float)
        # 返回值: 需要等待的秒数 (float)
        lua_script = """
        local next_slot_key = KEYS[1]
        local now = tonumber(ARGV[1])
        local interval = tonumber(ARGV[2])
        
        local next_slot = tonumber(redis.call('GET', next_slot_key) or 0)
        local wait_time = 0
        
        if next_slot > now then
            -- 需要排队
            wait_time = next_slot - now
            redis.call('SET', next_slot_key, next_slot + interval, 'EX', 600)
        else
            -- 系统空闲，从现在开始计算下一个槽位
            redis.call('SET', next_slot_key, now + interval, 'EX', 600)
        end
        
        return tostring(wait_time)
        """
        
        next_slot_key = f"{REDIS_TASK_NEXT_SLOT_PREFIX}{task_id}"
        now = time.time()
        
        try:
            # 执行 Lua 脚本
            # redis-py 的 eval 接受 (script, numkeys, *keys_and_args)
            wait_time_str = await self.redis.eval(lua_script, 1, next_slot_key, str(now), str(ideal_interval))
            wait_time = float(wait_time_str)
            
            # 增加随机呼吸感抖动 (±10%)，模拟人类行为并避开行为审计
            if wait_time > 0:
                jitter = wait_time * random.uniform(-0.1, 0.1)
                wait_time = max(0, wait_time + jitter)
            
            return wait_time
        except Exception as e:
            logger.error(f"⚠️ 动态准入控制失败，回退到原始逻辑: {e}")
            return 0.0

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

    async def scan_task(self, task_data: Dict[str, Any]):
        """执行扫描任务逻辑 (支持分片采集与多账号协同)"""
        task_id = self._extract_value(task_data.get("taskId"))
        task_type = self._extract_value(task_data.get("taskType"))
        task_name = self._extract_value(task_data.get("name"))
        run_mode = self._extract_value(task_data.get("runMode"))
        
        # 1. 账号过滤
        raw_accounts = self._extract_list(task_data.get("accounts", []))
        if not raw_accounts:
            logger.error(f"❌ 任务 [{task_name}] 未绑定任何账号，无法执行")
            return

        scan_accounts = []
        buy_accounts = []
        for acc in raw_accounts:
            actual_acc = self._extract_value(acc)
            if isinstance(actual_acc, list) and len(actual_acc) > 0:
                actual_acc = actual_acc[0]
            
            role = self._extract_value(actual_acc.get("role"))
            if role in ["SCAN", "BOTH"]:
                scan_accounts.append(actual_acc)
            if role in ["TRADE", "BOTH"]:
                buy_accounts.append(actual_acc)

        task_data["scan_accounts"] = scan_accounts
        task_data["buy_accounts"] = buy_accounts

        # 2. 判断运行模式
        if run_mode == "TRADE":
            logger.info(f"🚀 [任务ID: {task_id}] 启动下单监听模式: {task_name}")
            trade_job = asyncio.create_task(self._trade_task_loop(task_data))
            heartbeat_job = asyncio.create_task(self._task_heartbeat_loop(task_id))
            monitor_task = asyncio.create_task(self._monitor_stop_signal(task_id, [trade_job, heartbeat_job]))
            
            await asyncio.gather(trade_job, heartbeat_job, monitor_task, return_exceptions=True)
            
            await self._callback_status(task_id, 0 if monitor_task.done() and not monitor_task.cancelled() else 2)
            return

        # 3. 判断是否为系统任务（需要分片协同）
        if task_type and self._get_float(task_type) >= 2:
            logger.info(f"🚀 [任务ID: {task_id}] 启动分片协同执行器: {task_name}")
            executor = ShardedSpiderExecutor(self.client, redis_async=self.redis)
            await executor.execute(task_data)
            return

        # 4. 普通扫货/监控任务逻辑
        goods_id = self._extract_value(task_data.get("goodsId"))
        
        if not scan_accounts:
            logger.error(f"❌ 任务 [{task_name}] 未绑定任何扫描账号，无法执行")
            return
        
        logger.info(f"🚀 开始执行扫描任务: {task_name} (ID: {task_id}, GoodsID: {goods_id}, 账号数: {len(scan_accounts)})")
        if not buy_accounts and not task_data.get("targetTaskId"):
            logger.warning(f"💡 [系统提示] 当前任务未配置下单账号或关联任务，将进入“纯监控模式”")
        
        # 为每个扫描账号创建一个扫描协程
        scan_jobs = []
        for account in scan_accounts:
            job = asyncio.create_task(self._account_scan_loop(task_data, account))
            scan_jobs.append(job)

        # 监控任务停止信号
        monitor_task = asyncio.create_task(self._monitor_stop_signal(task_id, scan_jobs))

        # 等待所有账号扫描任务结束
        await asyncio.gather(*scan_jobs, monitor_task, return_exceptions=True)

        # 任务结束回调后端
        await self._callback_status(task_id, 0 if monitor_task.done() and not monitor_task.cancelled() else 2)

    async def _task_heartbeat_loop(self, task_id: int):
        """TRADE 任务心跳协程：定时更新 Redis 心跳 Key"""
        heartbeat_key = f"niro:task:alive:{task_id}"
        logger.info(f"💓 [任务ID: {task_id}] 心跳协程已启动")
        
        # 启动即刻发送一次心跳，避免扫描端初始化太快误判离线
        try:
            await self.redis.set(heartbeat_key, "1", ex=30)
        except:
            pass

        while True:
            try:
                # 设置 30s 过期，每 10s 更新一次
                await self.redis.set(heartbeat_key, "1", ex=30)
            except Exception as e:
                logger.error(f"⚠️ [任务ID: {task_id}] 更新心跳异常: {e}")
            
            try:
                await asyncio.sleep(10)
            except asyncio.CancelledError:
                logger.info(f"🛑 [任务ID: {task_id}] 心跳协程已停止")
                # 任务停止时主动清理心跳
                try:
                    await self.redis.delete(heartbeat_key)
                except:
                    pass
                break

    async def _trade_task_loop(self, task_data: Dict[str, Any]):
        """下单任务循环：监听 Redis 信号并执行购买"""
        task_id = self._extract_value(task_data.get("taskId"))
        task_name = self._extract_value(task_data.get("name"))
        buy_accounts = task_data.get("buy_accounts", [])

        queue_key = f"niro:queue:trade:{task_id}"
        
        logger.info(f"🎧 [下单任务: {task_name}] 正在监听信号队列: {queue_key}")
        
        while True:
            try:
                # 阻塞式弹出购买信号
                result = await self.redis.blpop(queue_key, timeout=5)
                if not result:
                    continue
                
                _, signal_json = result
                signal_data = json.loads(signal_json)
                sell_order_ids = signal_data.get("sell_order_ids", [])
                source_task_id = signal_data.get("task_id")
                
                if not sell_order_ids:
                    continue

                logger.info(f"📥 [下单任务: {task_name}] 监听到来自扫描任务 [{source_task_id}] 的信号 | 待处理 ID: {sell_order_ids}")
                
                # 随机选择一个可用的下单账号
                available_buyers = []
                for buyer in buy_accounts:
                    buyer_id = self._extract_value(buyer.get("accountId"))
                    try:
                        if not await self.redis.exists(f"niro:account:busy:{buyer_id}"):
                            available_buyers.append(buyer)
                    except Exception as re:
                        logger.error(f"⚠️ 检查账号忙碌状态异常 (Redis): {re}")
                        # Redis 异常时保守起见，假设忙碌
                        continue
                
                if not available_buyers:
                    logger.warning(f"⏳ [下单任务: {task_name}] 收到信号，但所有下单账号均繁忙，跳过本次处理")
                    continue
                
                buyer = random.choice(available_buyers)
                buyer_id = self._extract_value(buyer.get("accountId"))
                
                # 下单前置保护：立即设置 Busy 状态，防止并发重入
                try:
                    await self.redis.set(f"niro:account:busy:{buyer_id}", "1", ex=120)
                except Exception as re:
                    logger.error(f"⚠️ 设置账号忙碌状态失败 (Redis): {re}")
                
                if not buyer.get("profile"):
                    buyer["profile"] = BrowserHelper.create_profile(cookie=buyer.get("buffCookie"))
                
                logger.warning(f"🚀 [下单任务: {task_name}] 已指派账号 [{buyer.get('accountName')}] 执行购买流程")
                # async_buy_v3 内部会负责下单完成后的 Busy 状态维护（清理或延长）
                asyncio.create_task(self.async_buy_v3(sell_order_ids, buyer, task_data))
                
            except asyncio.CancelledError:
                logger.info(f"🛑 [下单任务: {task_name}] 已停止监听")
                break
            except Exception as e:
                logger.error(f"⚠️ [下单任务: {task_name}] 监听循环异常: {e}")
                await asyncio.sleep(1)

    async def _monitor_stop_signal(self, task_id: int, jobs: List[asyncio.Task]):
        """监控 Redis 停止信号"""
        stop_key = f"{REDIS_TASK_STOP_SIGNAL_PREFIX}{task_id}"
        while True:
            if await self.redis.exists(stop_key):
                logger.info(f"🛑 [任务ID: {task_id}] 收到停止信号，正在取消所有扫描子任务...")
                for job in jobs:
                    job.cancel()
                break
            await asyncio.sleep(5)

    async def _callback_status(self, task_id: int, status: int):
        """回调后端更新任务状态"""
        url = f"{BACKEND_URL}/task/callback/status"
        payload = {
            "id": task_id,
            "status": status
        }
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.post(url, json=payload)
                if resp.status_code == 200:
                    logger.info(f"✅ [任务ID: {task_id}] 状态回调成功: {status}")
                else:
                    logger.error(f"❌ [任务ID: {task_id}] 状态回调失败: {resp.status_code}, {resp.text}")
        except Exception as e:
            logger.error(f"❌ [任务ID: {task_id}] 状态回调异常: {e}")

    async def _account_scan_loop(self, task_data: Dict[str, Any], account: Dict[str, Any]):
        """单个账号的扫描循环"""
        try:
            task_id = self._extract_value(task_data.get("taskId"))
            goods_id = self._extract_value(task_data.get("goodsId"))
            account_id = self._extract_value(account.get("accountId"))
            
            # 扫描间隔
            interval_min = self._get_float(task_data.get("scanIntervalMin"), 15.0)
            interval_max = self._get_float(task_data.get("scanIntervalMax"), 30.0)
            max_price = self._get_float(task_data.get("maxPrice"), 0.0)
            
            # 工作周期配置
            duration_minutes = self._get_float(task_data.get("durationMinutes"), 0.0)
            rest_period = self._get_float(task_data.get("restPeriod"), 0.0)
            is_cycle_mode = duration_minutes > 0 and rest_period > 0
            
            # 使用工具类创建 Profile，自动处理指纹参数，避免手动实例化参数不匹配
            profile = BrowserHelper.create_profile(cookie=account.get("buffCookie"))
            
            # 设置账号级上下文
            raw_name = account.get("accountName")
            acc_name = f"{raw_name}" if raw_name else f"{account_id}"
            account_name_var.set(acc_name)
            account_id_var.set(account_id)

            # 扫描逻辑
            # 判断是否为纯监控模式 (无下单账号)
            is_monitor_only = not task_data.get("buy_accounts")
            status_text = "监控启动" if is_monitor_only else "扫描启动"
            logger.info(f"👤 {status_text} {'(周期模式: ' + str(duration_minutes) + '/' + str(rest_period) + 'min)' if is_cycle_mode else '(持续模式)'}")

            cycle_start_time = time.time()
            
            # 计算理想间隔 (用于动态准入)
            scan_accounts_count = len(task_data.get("scan_accounts", [1]))
            ideal_interval = interval_min / max(1, scan_accounts_count)

            while True:
                try:
                    # 检查周期循环逻辑
                    if is_cycle_mode:
                        elapsed_minutes = (time.time() - cycle_start_time) / 60
                        if elapsed_minutes >= duration_minutes:
                            logger.info(f"😴 工作周期结束 ({duration_minutes}min)，进入强制休眠 ({rest_period}min)...")
                            # 休眠期间仍需响应取消信号
                            try:
                                await asyncio.sleep(rest_period * 60)
                            except asyncio.CancelledError:
                                raise
                            
                            logger.info(f"⏰ 休眠结束，重新开始工作周期")
                            cycle_start_time = time.time()
                            continue

                    # 0. 动态准入控制 (精准错峰)
                    wait_time = await self._get_dynamic_wait_time(task_id, ideal_interval)
                    if wait_time > 0:
                        # 静默处理：微小的等待不再打印 INFO 日志，改为 DEBUG
                        if wait_time > 1.0:
                            logger.debug(f"🛡️ 流量整形中：精准错峰等待 {int(wait_time * 1000)}ms")
                        await asyncio.sleep(wait_time)

                    # 1. 执行扫描
                    action_text = "监控" if is_monitor_only else "扫描"
                    start_time = time.time()
                    items = await self.get_goods_list(goods_id, profile)
                    latency = (time.time() - start_time) * 1000
                    
                    # 2. 处理结果与行情展示
                    if items:
                        # 计算动态 RPM (基于最近 60s 记录) - 按账号隔离
                        if account_id not in self._account_history:
                            self._account_history[account_id] = deque(maxlen=100)
                        
                        history_queue = self._account_history[account_id]
                        history_queue.append((time.time(), 1)) # 记录本次请求
                        
                        # 计算该账号最近 60s 内的请求总数
                        now = time.time()
                        current_rpm = 0
                        
                        for ts, count in history_queue:
                            if now - ts <= 60:
                                current_rpm += count
                        
                        # 无论是否命中，都输出行情概览
                        current_min_price = float(items[0].get("price", 0))
                        price_diff = current_min_price - max_price
                        diff_str = f"+¥{price_diff:.2f}" if price_diff > 0 else f"-¥{abs(price_diff):.2f}"
                        status_icon = "⏳" if price_diff > 0 else "🎯"
                        
                        goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                        logger.info(
                            f"💰 [行情] {goods_name} | 最低: ¥{current_min_price:.2f} | 目标: ≤¥{max_price:.2f} | 差距: {diff_str} {status_icon} | RPM: {current_rpm}/m"
                        )
                        
                        # 处理匹配逻辑
                        await self._process_items(task_data, items, account)
                    
                    # 3. 周期扫描完成摘要
                    next_wait = random.uniform(interval_min, interval_max)
                    logger.info(f"✅ 周期扫描完成 | 响应: {int(latency)}ms | 下次准入: {next_wait:.1f}s后 | 状态: 安全 🛡️")
                    
                    await asyncio.sleep(next_wait)
                    
                except asyncio.CancelledError:
                    logger.info(f"🛑 扫描任务已取消")
                    break
                except Exception as e:
                    logger.error(f"⚠️ 扫描循环异常: {e}")
                    await asyncio.sleep(interval_min)
        except Exception as e:
            logger.exception(f"💥 协程初始化失败: {e}")

    def _get_csrf_token(self, profile: BrowserProfile) -> str:
        """从 Cookie 中提取 CSRF Token (兼容 csrf_token 和 csrftoken)"""
        if not profile.cookie:
            return ""
        import re
        # 批量购买接口通常使用 csrftoken。我们先尝试匹配 csrftoken，再匹配 csrf_token
        # 稳健匹配：处理引号包裹或非引号包裹的情况
        # 先找 csrftoken
        match = re.search(r'csrftoken=(?:"([^"]+)"|([^; ]+))', profile.cookie)
        if not match:
            # 再找 csrf_token
            match = re.search(r'csrf_token=(?:"([^"]+)"|([^; ]+))', profile.cookie)
            
        if match:
            # group(1) 是引号内的内容，group(2) 是非引号的内容
            token = match.group(1) or match.group(2) or ""
            return token
        return ""

    async def _create_order(self, item: Dict[str, Any], account: Dict[str, Any], task_data: Dict[str, Any]):
        """执行下单购买"""
        # 兼容性修复：处理原始账号字典提取
        raw_acc_name = account.get("accountName") or account.get("name")
        acc_name = f"{raw_acc_name}" if raw_acc_name else "未知账号"
        
        # 核心修复：如果账号对象中没有 profile，则根据 cookie 动态创建一个
        profile = account.get("profile")
        if not profile:
            cookie = account.get("buffCookie")
            if not cookie:
                logger.error(f"❌ 下单失败：缺少 buffCookie")
                return "ERROR", "Missing buffCookie"
            profile = BrowserHelper.create_profile(cookie=cookie)
        
        goods_id = self._extract_value(task_data.get("goodsId"))
        item_id = item.get("id")
        price = item.get("price")
        
        url = "https://buff.163.com/api/market/goods/buy"
        payload = {
            "game": BuffGameType.CSGO.value,
            "goods_id": int(goods_id),
            "sell_order_id": str(item_id),
            "price": float(price),
            "pay_method": BuffPaymentMethod.BALANCE.value,
            "allow_tradable_cooldown": 0,
        }
        
        headers = {
            "x-csrftoken": self._get_csrf_token(profile),
            "Content-Type": "application/json",
            "X-Requested-With": "XMLHttpRequest",
            "Origin": "https://buff.163.com",
            "Referer": f"https://buff.163.com/goods/{goods_id}?from=market"
        }

        logger.info(f"🛒 [发起购买] GoodsID={goods_id} | ItemID={item_id} | Price={price}")
        
        try:
            # 使用统一的 _request 逻辑，包含 HTML 预检和异常处理
            res_json = await self._request("POST", url, profile, json=payload, headers=headers)
            
            if res_json.get("code") == "OK":
                data = res_json.get("data", {})
                order_id = data.get("id")
                logger.info(f"✅ 下单成功! 订单号: {order_id}")
                
                # 推送成功通知
                goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                user_id = self._extract_value(task_data.get("userId"))
                title = "✅ 下单成功"
                description = f"账号: {acc_name}\n饰品: {goods_name}\n价格: ¥{price}\n订单号: {order_id}"
                self.notifier.send_textcard(title, description, user_id=user_id)
                return "SUCCESS", None
            else:
                error_msg = res_json.get("error") or res_json.get("msg") or "未知错误"
                code = res_json.get("code")
                logger.error(f"❌ 下单失败: {code} - {error_msg}")
                
                # 风控判断 (根据实际 Buff 返回码调整)
                if code in ["Risk Control", "Account Banned", "Action Forbidden"]:
                    return "RISK", error_msg
                
                # 其他失败 (如库存不足、余额不足等)
                return "FAILED", error_msg
        except LoginRequiredError:
            logger.error(f"🔑 Cookie 已失效，无法下单")
            raise
        except Exception as e:
            logger.error(f"⚠️ 下单请求异常: {e}")
            return "ERROR", str(e)

    async def _process_items(self, task_data: Dict[str, Any], items: List[Dict[str, Any]], account: Dict[str, Any]):
        """处理获取到的商品列表，执行匹配与信号路由"""
        task_id = self._extract_value(task_data.get("taskId"))
        user_id = self._extract_value(task_data.get("userId"))
        max_price = self._get_float(task_data.get("maxPrice"), 0.0)
        min_profit = self._get_float(task_data.get("minProfit"), 0.0)
        task_type = self._get_float(task_data.get("taskType"), 0.0)
        acc_name = account_name_var.get()
        buy_accounts = task_data.get("buy_accounts") or []
        target_task_id = self._extract_value(task_data.get("targetTaskId"))
        
        # 获取当前任务配置的支付方式
        payment_method_str = task_data.get("paymentMethod", "BALANCE")
        try:
            target_pay_method = getattr(BuffPaymentMethod, payment_method_str).value
        except (AttributeError, KeyError):
            target_pay_method = BuffPaymentMethod.BALANCE.value

        # 1. 价格过滤与支付方式匹配
        if int(task_type) == 1:
            matched_items = []
            for it in items:
                # 检查支付方式匹配
                supported_methods = it.get("supported_pay_methods", [])
                # 如果挂单明确指定了支付方式列表，且不包含目标支付方式，则过滤
                if supported_methods and target_pay_method not in supported_methods:
                    continue

                buy_price = self._get_float(it.get("price"), 0.0)
                ref_price = self._get_float(it.get("sell_min_price"), 0.0)
                if ref_price <= 0:
                    continue
                profit = ref_price * 0.975 - buy_price
                if profit >= min_profit:
                    it["estimated_profit"] = profit
                    matched_items.append(it)
        else:
            matched_items = []
            for it in items:
                # 检查支付方式匹配
                supported_methods = it.get("supported_pay_methods", [])
                if supported_methods and target_pay_method not in supported_methods:
                    continue
                
                if self._get_float(it.get("price"), 0.0) <= max_price:
                    matched_items.append(it)
        
        if not matched_items:
            return

        # 2. 检查路由目标 (优先使用关联任务)
        if not buy_accounts and not target_task_id:
            # 纯监控模式：去重报送
            new_items = []
            for it in matched_items:
                it_id = it.get("id")
                report_cache_key = f"niro:report:cache:{it_id}"
                if not await self.redis.exists(report_cache_key):
                    new_items.append(it)
            
            if not new_items: return

            for it in new_items:
                await self.redis.set(f"niro:report:cache:{it.get('id')}", "1", ex=180)

            logger.info(f"🔍 [监控发现] 匹配到 {len(new_items)} 个挂单，已推送通知")
            
            try:
                goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                goods_id = self._extract_value(task_data.get("goodsId"))
                link = f"https://buff.163.com/goods/{goods_id}"
                
                count = len(new_items)
                first_item = new_items[0]
                item_id = first_item.get("id")
                price = first_item.get("price")
                paintwear = first_item.get("paintwear")

                title = f"🔍 发现捡漏机会 ({count}个)" if count > 1 else "🔍 发现捡漏机会"
                description = f"饰品: {goods_name}\n" \
                             f"价格: ¥{price} (预设: ¥{max_price})\n" \
                             f"磨损: {paintwear or 'N/A'}\n" \
                             f"挂单ID: {item_id}{'...' if count > 1 else ''}"
                
                self.notifier.send_textcard(title, description, url=link, btntxt="立即前往秒杀", user_id=user_id)
            except Exception as ne:
                logger.error(f"⚠️ 推送通知失败: {ne}")
            return

        # 3. 关联任务/下单终端前置校验
        skip_reason = None
        try:
            if target_task_id:
                # 优先检查新的 niro:task:alive:{id} 心跳
                alive_key = f"niro:task:alive:{target_task_id}"
                if not await self.redis.exists(alive_key):
                    # 如果没有心跳，再检查一下该任务是否正在监听队列 (作为刚启动时的容错)
                    queue_key = f"niro:queue:trade:{target_task_id}"
                    # 注意：BLPOP 不会产生 key，所以这里我们只能依赖心跳或手动查询任务状态
                # 此时我们可以尝试降级检查旧心跳
                old_heartbeat_key = "niro:task:heartbeat"
                last_heartbeat = await self.redis.hget(old_heartbeat_key, str(target_task_id))
                
                if not last_heartbeat or (int(time.time() * 1000) - int(last_heartbeat)) > 120000: # 延长至2分钟
                    # 双重检查都失败，才判定离线
                    skip_reason = f"目标终端离线 (Task {target_task_id} 无心跳)"
            else:
                # 本地模式检查是否有可用账号
                any_available = False
                for buyer in buy_accounts:
                    buyer_id = self._extract_value(buyer.get("accountId"))
                    if not await self.redis.exists(f"niro:account:busy:{buyer_id}"):
                        any_available = True
                        break
                if not any_available:
                    skip_reason = "所有目标账号繁忙"
        except Exception as re:
            logger.error(f"⚠️ Redis 前置校验异常: {re}")

        if skip_reason:
            # 记录连续跳过次数
            skip_counter_key = f"niro:task:skip_count:{task_id}"
            try:
                count = await self.redis.incr(skip_counter_key)
                await self.redis.expire(skip_counter_key, 300)
                
                logger.info(f"⏳ [任务ID: {task_id}] {skip_reason}，正在跳过... (连续次数: {count})")
                
                # 连续 N 次跳过则报警 (例如 10 次)
                if count >= 10:
                    await self.redis.delete(skip_counter_key)
                    goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                    alert_title = "🚨 下单终端异常告警"
                    alert_desc = f"扫描任务: {goods_name} (ID: {task_id})\n" \
                                 f"原因: {skip_reason}\n" \
                                 f"状态: 连续 {count} 次尝试失败，下单端可能已离线或持续繁忙。"
                    self.notifier.send_textcard(alert_title, alert_desc, user_id=user_id)
            except Exception as re:
                logger.error(f"⚠️ Redis 更新跳过计数失败: {re}")
            return

        # 重置跳过计数
        try:
            await self.redis.delete(f"niro:task:skip_count:{task_id}")
        except:
            pass

        # 4. 饰品加锁与去重 (批量撮合)
        # buy_count = int(task_data.get("buyCount") or 0)
        # success_count = int(task_data.get("successCount") or 0)
        # remaining_count = max(0, buy_count - success_count)
        
        # if buy_count > 0 and remaining_count <= 0:
        #     return

        pending_it_ids = []
        quota_key = f"niro:task:quota:{task_id}"

        for it in matched_items:
            # if buy_count > 0 and len(pending_it_ids) >= remaining_count:
            #     break
            
            # 1. 【新增】尝试申请配额
            # 只有设置了 buyCount 的任务才会有配额 Key，如果没有 Key (例如不限量任务)，decr 会变成 -1
            # 所以我们需要先判断一下配额 Key 是否存在，或者简单地允许不限量任务不做检查？
            # 根据后端逻辑，只有 buyCount > 0 才会 set quotaKey。
            # 如果 quotaKey 不存在 (decr 返回 -1)，说明是不限量任务或者 Key 过期/未初始化。
            # 为了稳健，我们可以先假设如果不限量任务，就不走这个逻辑。
            # 但用户指令要求明确，我们按照指令逻辑写。
            
            # 注意：Redis 的 decr 如果 key 不存在，会先设为 0 再减 1，返回 -1。
            # 这会导致不限量的任务直接 break。
            # 因此需要判断 task_data 中是否有 buyCount > 0。
            
            buy_count = int(task_data.get("buyCount") or 0)
            if buy_count > 0:
                remaining = await self.redis.decr(quota_key)
                if remaining < 0:
                    # 票发完了：把刚才减成负数的 1 加回去，并打断循环
                    await self.redis.incr(quota_key)
                    logger.info(f"🛑 [任务 {task_id}] 配额已满，停止匹配更多饰品")
                    break
            
            it_id = it.get("id")
            pending_key = f"niro:pending_purchase:{it_id}"
            lock_key = f"niro:lock:item:{it_id}"
            
            try:
                if await self.redis.exists(pending_key): 
                    # 已经被处理了，如果扣了配额要退回去
                    if buy_count > 0:
                        await self.redis.incr(quota_key)
                    continue
                
                if await self.redis.set(lock_key, "locked", ex=60, nx=True):
                    if await self.redis.set(pending_key, "processing", ex=120, nx=True):
                        pending_it_ids.append(it_id)
                    else:
                        await self.redis.delete(lock_key)
                        # 加锁 pending 失败，退票
                        if buy_count > 0:
                            await self.redis.incr(quota_key)
                else:
                    # 加锁 lock 失败，退票
                    if buy_count > 0:
                        await self.redis.incr(quota_key)
                        
            except Exception as re:
                logger.error(f"⚠️ [任务ID: {task_id}] 饰品加锁操作异常 (Redis): {re}")
                # 异常退票
                if buy_count > 0:
                    await self.redis.incr(quota_key)
                continue

        if not pending_it_ids:
            return

        # 5. 分发购买信号
        if target_task_id:
            # 路由模式：推送到指定下单任务的队列
            queue_key = f"niro:queue:trade:{target_task_id}"
            signal_data = {
                "task_id": task_id,
                "sell_order_ids": pending_it_ids,
                "timestamp": int(time.time())
            }
            try:
                await self.redis.rpush(queue_key, json.dumps(signal_data))
                logger.warning(f"📤 匹配成功，已将信号发送至下单任务 [{target_task_id}] | 订单ID: {pending_it_ids}")
            except Exception as re:
                logger.error(f"❌ 推送下单信号至队列失败: {re}")
                await self._release_items(pending_it_ids)
        else:
            # 本地模式：指派绑定的下单账号执行
            available_buyers = []
            for buyer in buy_accounts:
                buyer_id = self._extract_value(buyer.get("accountId"))
                busy_key = f"niro:account:busy:{buyer_id}"
                try:
                    if not await self.redis.exists(busy_key):
                        available_buyers.append(buyer)
                except Exception as re:
                    logger.error(f"⚠️ [任务ID: {task_id}] 检查账号忙碌状态异常 (Redis): {re}")
                    continue
            
            if not available_buyers:
                # 这种情况在步骤 3 已经拦截，理论上不应该进入这里，但作为兜底逻辑保留
                await self._release_items(pending_it_ids)
                return

            buyer = random.choice(available_buyers)
            buyer_id = self._extract_value(buyer.get("accountId"))
            busy_key = f"niro:account:busy:{buyer_id}"
            
            # 立即设置 Busy 状态，防止本地并发重入
            try:
                await self.redis.set(busy_key, "1", ex=120)
            except Exception as re:
                logger.error(f"⚠️ [任务ID: {task_id}] 设置账号忙碌状态失败 (Redis): {re}")

            if not buyer.get("profile"):
                buyer["profile"] = BrowserHelper.create_profile(cookie=buyer.get("buffCookie"))
                
            logger.warning(f"🚀 发现机会，指派 [{buyer.get('accountName')}] 发起异步批量下单 (聚合数: {len(pending_it_ids)})")
            asyncio.create_task(self.async_buy_v3(pending_it_ids, buyer, task_data))

    async def _report_order_result(self, task_data: Dict[str, Any], account: Dict[str, Any], item_id: str,
                                   status: int, error_msg: str, order_id: str = "", price: float = 0.0,
                                   goods_name: str = "", market_hash_name: str = "", goods_img: str = "",
                                   paintwear: float = 0.0, extra_info: Dict = None):
        """
        [Mission] 上报订单结果到 Redis
        """
        try:
            user_id = self._extract_value(task_data.get("userId"))
            task_id = self._extract_value(task_data.get("taskId")) or self._extract_value(task_data.get("targetTaskId"))
            account_id = self._extract_value(account.get("accountId"))
            
            report_data = {
                "platform": "BUFF",
                "userId": int(user_id) if user_id else 0,
                "taskId": int(task_id) if task_id else 0,
                "accountId": int(account_id) if account_id else 0,
                "goodsName": goods_name,
                "marketHashName": market_hash_name,
                "goodsImg": goods_img,
                "price": price,
                "paintwear": paintwear,
                "orderId": order_id,
                "status": status, # 1成功, 2失败
                "errorMsg": error_msg,
                "errorCode": "", # 可选
                "extraInfo": extra_info or {},
                "timestamp": int(time.time() * 1000)
            }
            
            # 异步推送到 Redis 列表
            key = "niro:order:report"
            await self.redis.lpush(key, json.dumps(report_data, ensure_ascii=False))
            logger.info(f"📤 [订单上报] 已推送订单结果: {order_id} | Status: {status}")

            if status == 2: # 失败
                quota_key = f"niro:task:quota:{task_id}"
                # 注意：这里我们只恢复1个配额，因为 _report_order_result 是针对单个订单/商品调用的
                # 只有当任务设置了 buyCount 时才恢复
                # 我们可以尝试去获取任务信息，或者直接根据 task_id 操作
                # 由于 _report_order_result 没有传入 buyCount 信息，我们只能假定存在 quota_key 就 incr
                # 或者更稳妥地，我们先检查 key 是否存在
                # 考虑到性能，直接 incr 也行，反正如果没有初始化这个 key，incr 会变成 1，也不会有大问题（只要 backend 逻辑能处理）
                # 但为了严谨，最好还是判断一下 key 是否存在。
                # 简化逻辑：直接 incr，如果 key 不存在会新建，但通常如果不限量的任务我们不会初始化这个 key。
                # 如果是不限量的任务，incr 后 key 变成正数，对逻辑无影响（decr 会变成 -1，-2 等）。
                # 只有当 key 是正数且被减到 0 时才会触发限制。
                
                # 为了避免给不限量的任务产生垃圾 key，我们最好确认一下。
                # 但这里没有 task_data 的完整信息（参数里有 task_data，可以取）
                
                buy_count = int(task_data.get("buyCount") or 0)
                if buy_count > 0:
                    await self.redis.incr(quota_key)
                    logger.info(f"🔄 订单 {order_id} 失败，已自动恢复 1 个购买配额")
            
        except Exception as e:
            logger.error(f"❌ [订单上报] 异常: {e}")

    async def async_buy_v3(self, sell_order_ids: List[str], account: Dict[str, Any], task_data: Dict[str, Any]):
        """
        [Mission] 异步化重写 buy_v3 (五步核心流程) - 优化版
        引入“人类行为模拟”冷却机制和加固确认逻辑
        """
        acc_id = self._extract_value(account.get("accountId"))
        acc_name = account.get("accountName") or "未知账号"
        
        # 设置账号级上下文
        account_name_var.set(acc_name)
        account_id_var.set(acc_id)
        
        profile = account.get("profile")
        goods_id = self._extract_value(task_data.get("goodsId"))
        user_id = self._extract_value(task_data.get("userId"))
        busy_key = f"niro:account:busy:{acc_id}"
        
        current_ids = list(sell_order_ids)
        trace_id = None
        should_clear_busy = True 
        
        try:
            # --- 0. 下单间隔保护 (Sequential Guard) ---
            last_buy_time = self._last_buy_times.get(acc_id, 0)
            elapsed = time.time() - last_buy_time
            if elapsed < 120:  # 保护期延长至 120 秒
                guard_delay = random.uniform(5.0, 10.0)
                logger.info(f"保护期内 (已过 {elapsed:.1f}s/120s)，增加额外延迟: {guard_delay:.2f}s")
                await asyncio.sleep(guard_delay)
            
            # 微秒级抖动，模拟点击开始
            await asyncio.sleep(random.uniform(0.1, 0.5))

            # --- 1. 批量预览 (Preview) ---
            logger.info(f"🔍 步骤 1/5: 发起批量预览 | ID: {current_ids}")
            preview_data, trace_id, error_code = await self._batch_buy_preview(goods_id, current_ids, profile)
            
            if error_code == "Already Paying":
                logger.error(f"🚨 触发 Already Paying 熔断，标记 Busy 200s")
                await self.redis.set(busy_key, "1", ex=200)
                
                # 批量退票：本次尝试了多少个，就退多少个
                buy_count = int(task_data.get("buyCount") or 0)
                if buy_count > 0:
                    quota_key = f"niro:task:quota:{self._extract_value(task_data.get('taskId'))}"
                    await self.redis.incrby(quota_key, len(current_ids))
                    
                await self._release_items(current_ids)
                should_clear_busy = False
                return

            if not preview_data:
                logger.error(f"❌ 批量预览失败: {error_code}")
                # 批量退票：本次尝试了多少个，就退多少个
                buy_count = int(task_data.get("buyCount") or 0)
                if buy_count > 0:
                    quota_key = f"niro:task:quota:{self._extract_value(task_data.get('taskId'))}"
                    await self.redis.incrby(quota_key, len(current_ids))
                    
                await self._release_items(current_ids)
                return

            logger.info(f"✅ 预览成功 | 预估总价: ¥{preview_data.get('price')}")

            # 动态获取支付方式 (从任务数据中提取)
            payment_method_str = task_data.get("paymentMethod", "BALANCE")
            try:
                preferred_pay_method = getattr(BuffPaymentMethod, payment_method_str).value
            except (AttributeError, KeyError):
                logger.warning(f"⚠️ 未知支付方式: {payment_method_str}, 默认使用 BALANCE")
                preferred_pay_method = BuffPaymentMethod.BALANCE.value
            
            # 支付方式决策逻辑 (含余额不足自动切换)
            pay_methods = preview_data.get("pay_methods", [])
            selected_method = next((m for m in pay_methods if m.get("value") == int(preferred_pay_method)), None)
            pay_method = preferred_pay_method

            # 如果首选支付方式不可用或余额不足，执行自动切换
            if not selected_method or not selected_method.get("enough", False):
                if preferred_pay_method == BuffPaymentMethod.BUFF_BALANCE.value:
                    logger.warning(f"⚠️ BUFF 余额不足或不可用，尝试自动切换至 网易支付/余额...")
                    fallback_method = next((m for m in pay_methods if m.get("value") == BuffPaymentMethod.BALANCE.value), None)
                    if fallback_method and fallback_method.get("enough", False):
                        selected_method = fallback_method
                        pay_method = BuffPaymentMethod.BALANCE.value
                        logger.info(f"✅ 自动切换成功，当前支付方式: 网易支付/余额")
                    else:
                        logger.error(f"❌ BUFF 余额和网易支付均不足或不可用")
                        await self._release_items(current_ids)
                        return
                else:
                    # 提升日志级别为 ERROR，以便 UI 实时监控捕获
                    logger.error(f"❌ 支付方式 ({BuffPaymentMethod.get_label(preferred_pay_method)}) 余额不足或不可用")
                    await self._release_items(current_ids)
                    return
            
            # total_price 是包含手续费的总价，用于创建订单和支付
            total_price = selected_method.get("price_with_pay_fee")
            
            # 提取每个饰品的单价 (对齐 JSON 行为)
            # 模拟官网逻辑：预览数据中通常包含每个 item 的价格信息
            item_prices = {}
            if "items" in preview_data:
                for item in preview_data["items"]:
                    item_prices[item["sell_order_id"]] = item.get("price")
            else:
                # 备选方案：如果 preview_data 没给，则尝试从原始任务数据中获取（假设单价一致或已计算好）
                avg_unit_price = float(preview_data.get("price")) / len(current_ids)
                for it_id in current_ids:
                    item_prices[it_id] = f"{avg_unit_price:.2f}"

            # 模拟真人思考与选择支付方式
            await asyncio.sleep(random.uniform(0.5, 1.2))
            
            # --- 1.5 模拟真人收银台行为: 获取支付描述 ---
            await self._get_pay_method_description(pay_method, total_price, goods_id, profile)
            await asyncio.sleep(random.uniform(0.3, 0.6))

            # --- 2. 创建订单 (Create) ---
            batch_buy_id = None
            if pay_method == BuffPaymentMethod.BUFF_BALANCE.value:
                logger.info(f"💰 检测到纯 BUFF 余额支付 (59)，跳过订单预创建与支付流程")
            else:
                logger.info(f"📝 步骤 2/5: 准备预创建订单，待下单数量: {len(current_ids)}")
                batch_buy_id = await self._batch_buy_create(goods_id, pay_method, total_price, len(current_ids), profile, trace_id)
                if not batch_buy_id:
                    logger.error(f"❌ 订单创建失败")
                    await self._release_items(current_ids)
                    return
                
                # 设置初始 Busy 标志
                await self.redis.set(busy_key, "1", ex=200)
                logger.info(f"✅ 订单预创建成功 | BatchID: {batch_buy_id}")

                # --- 3. 支付预处理 (Pay Prep) ---
                logger.info(f"💰 步骤 3/5: 发起支付预处理...")
                await asyncio.sleep(random.uniform(0.5, 0.8))
                pay_res = await self._batch_buy_pay(batch_buy_id, goods_id, profile)
                if not pay_res or pay_res.get("code") != "OK":
                    logger.error(f"❌ 支付预处理失败 | Msg: {pay_res.get('msg') if pay_res else 'Request Failed'}")
                    await self._release_items(current_ids)
                    return
                
                pay_data = pay_res.get("data", {})
                if pay_data.get("auto_pay") is False:
                    epay_url = pay_data.get("elements", {}).get("url")
                    if epay_url:
                        logger.warning(f"⚠️ 需手动支付授权！延长 Busy 锁至 180s")
                        await self.redis.set(busy_key, "1", ex=180) # 延长锁时间，给人工预留时间
                        logger.warning(f"👉 支付链接: {epay_url}")
                        
                        try:
                            goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                            title = "⚠️ 需手动支付授权"
                            description = f"账号: {acc_name}\n饰品: {goods_name}\n金额: ¥{total_price}\n状态: 等待人工操作"
                            self.notifier.send_textcard(title, description, url=epay_url, btntxt="点击前往支付", user_id=user_id)
                        except Exception as ne:
                            logger.error(f"⚠️ 支付链接推送失败: {ne}")
                
                logger.info(f"💰 支付预处理完成，开始轮询支付状态...")

                # --- 4. 轮询状态 (Polling Status) ---
                logger.info(f"⏳ 步骤 4/5: 轮询支付状态...")
                is_ready = False
                for i in range(180):
                    check_res = await self._batch_buy_check_state(batch_buy_id, goods_id, profile)
                    
                    if not check_res or check_res.get("code") != "OK":
                        logger.warning(f"⚠️ 轮询支付状态异常 ({i}s): {check_res.get('msg') if check_res else 'Request Failed'}")
                    
                    state = check_res.get("data", {}).get("state") if check_res else None
                    if state == 2:
                        is_ready = True
                        logger.info(f"✅ 支付状态已就绪 (耗时: {i}s)")
                        break
                    
                    if state == 4:
                        logger.error(f"❌ 支付失败或已关闭 (State: 4)")
                        break
                    
                    if i > 0 and i % 10 == 0:
                        logger.info(f"⏳ 支付状态轮询中... 当前状态: {state} ({i}s/180s)")
                    
                    await asyncio.sleep(1.0 + random.uniform(0, 0.2)) # 增加微小抖动

                if not is_ready:
                    logger.error(f"❌ 支付状态轮询超时")
                    await self._release_items(current_ids)
                    return

            # --- 5. 最终确认 (Final Confirm) ---
            logger.info(f"🚀 步骤 5/5: 发起最终确认请求 (对齐 JSON: 循环下单模式)...")
            # 增加确认前的冷静期，模拟真人从支付完成回到页面的动作
            await asyncio.sleep(random.uniform(1.2, 2.0))
            
            url = "https://buff.163.com/api/market/goods/buy"
            successful_orders = []
            
            # 优先从预览数据中获取 batch_id，如果没有则生成随机 ID
            batch_id = preview_data.get("batch_id") or uuid.uuid4().hex
            
            # 协议核心修正：循环发起每个饰品的购买请求
            for i, it_id in enumerate(current_ids):
                price_str = str(item_prices.get(it_id, "0.00"))
                
                payload = {
                    "game": BuffGameType.CSGO.value,
                    "goods_id": int(goods_id),
                    "sell_order_id": it_id,
                    "price": price_str,
                    "batch": 1,
                    "pay_method": int(pay_method),
                    "allow_tradable_cooldown": 0,
                    "hide_non_epay": False,
                    "batch_id": batch_id,
                    "steamid": None
                }
                
                # 只有在非纯余额支付且有 batch_buy_id 时才添加该字段
                if batch_buy_id:
                    payload["batch_buy_id"] = batch_buy_id
                
                logger.info(f"🚀 发起第 {i+1}/{len(current_ids)} 件确认 | ID: {it_id} | Price: {price_str}")
                
                # 增加 CSRF 自动重试循环
                for attempt in range(2):
                    headers = profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
                    headers.update({
                        "x-csrftoken": self._get_csrf_token(profile),
                        "Content-Type": "application/json",
                        "X-Requested-With": "XMLHttpRequest",
                        "Accept": "application/json, text/javascript, */*; q=0.01"
                    })

                    try:
                        res_json = await self._request("POST", url, profile, json=payload, headers=headers)
                        
                        # CSRF 重试逻辑
                        if res_json.get("code") == "CSRF Verification Error":
                            if attempt == 0:
                                logger.warning(f"🔄 捕获 CSRF 校验失败 (Confirm #{i+1})，Cookie 已自动更新，正在发起重试...")
                                continue
                            else:
                                logger.error(f"🔑 CSRF 校验连续失败 (Confirm #{i+1}) | Code: {res_json.get('code')}")

                        if res_json.get("code") == "OK":
                            order_id = res_json.get("data", {}).get("id")
                            successful_orders.append(order_id)
                            logger.info(f"✨ 第 {i+1} 件下单成功! 订单号: {order_id}")
                            
                            # 触发异步上报 (成功)
                            goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                            market_hash_name = self._extract_value(task_data.get("marketHashName")) or goods_name
                            goods_img = self._extract_value(task_data.get("iconUrl")) or ""
                            
                            asyncio.create_task(self._report_order_result(
                                task_data, account, it_id,
                                status=1, # 成功
                                error_msg="",
                                order_id=order_id,
                                price=float(price_str),
                                goods_name=goods_name,
                                market_hash_name=market_hash_name,
                                goods_img=goods_img
                            ))
                            
                            # 每件之间稍微停顿，模拟真人点击
                            if i < len(current_ids) - 1:
                                await asyncio.sleep(random.uniform(0.5, 1.0))
                            break # 成功则退出重试循环
                        else:
                            error_msg = res_json.get("msg") or res_json.get("error") or "Unknown Error"
                            logger.error(f"❌ 第 {i+1} 件确认失败 | Msg: {error_msg}")
                            
                            # 触发异步上报 (失败)
                            goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                            market_hash_name = self._extract_value(task_data.get("marketHashName")) or goods_name
                            goods_img = self._extract_value(task_data.get("iconUrl")) or ""
                            
                            asyncio.create_task(self._report_order_result(
                                task_data, account, it_id,
                                status=2, # 失败
                                error_msg=f"下单失败: {error_msg}",
                                price=float(price_str),
                                goods_name=goods_name,
                                market_hash_name=market_hash_name,
                                goods_img=goods_img
                            ))
                            
                            if attempt == 1: # 如果是最后一次尝试且失败
                                pass
                    except Exception as e:
                        logger.error(f"💥 第 {i+1} 件确认异常: {e}")

            if successful_orders:
                # 记录最后成功下单时间
                self._last_buy_times[acc_id] = time.time()
                
                # --- 6. 刷新余额 (Refresh Balance) ---
                logger.info(f"💰 步骤 6/6: 刷新账号余额...")
                balance_info = await self._refresh_account_balance(profile)
                if balance_info:
                    await self._report_account_info(
                        acc_id, 
                        balance=balance_info.get("balance"),
                        pending_balance=balance_info.get("pending_balance")
                    )

                # 发送通知
                goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                title = f"✨ 批量下单成功 ({len(successful_orders)}/{len(current_ids)}件)"
                description = f"账号: {acc_name}\n饰品: {goods_name}\n状态: 下单已完成，请在 App 中手动发送报价"
                self.notifier.send_textcard(title, description, user_id=user_id)
            else:
                logger.error(f"❌ 批量下单全部失败")
                await self._release_items(current_ids)

        except Exception as e:
            logger.exception(f"❌ 批量下单流程异常: {e}")
            # 批量退票：本次尝试了多少个，就退多少个
            buy_count = int(task_data.get("buyCount") or 0)
            if buy_count > 0:
                quota_key = f"niro:task:quota:{self._extract_value(task_data.get('taskId'))}"
                try:
                    await self.redis.incrby(quota_key, len(current_ids))
                except:
                    pass
            await self._release_items(current_ids)
        finally:
            # --- 6. 强制余温冷却 (Post-Order Cooldown) ---
            # 成功下单后休息 1-2 分钟，确保账号安全
            cooldown = random.uniform(60.0, 120.0)
            logger.info(f"🧊 下单任务结束，进入强制余温冷却 (1-2分钟): {cooldown:.2f}s")
            await asyncio.sleep(cooldown)
            
            if should_clear_busy:
                await self.redis.delete(busy_key)
                logger.info(f"🔓 Busy 锁已释放")

    async def _release_items(self, item_ids: List[str]):
        """统一释放饰品锁"""
        for it_id in item_ids:
            await self.redis.delete(f"niro:lock:item:{it_id}", f"niro:pending_purchase:{it_id}")

    async def _batch_buy_preview(self, goods_id: int, sell_order_ids: List[str], profile: BrowserProfile):
        """[Mission] 异步版预览"""
        url = "https://buff.163.com/api/market/goods/batch_buy/preview"
        payload = {
            "game": BuffGameType.CSGO.value,
            "goods_id": int(goods_id),
            "sell_orders": sell_order_ids,
            "select_epay": 1,  # 补全关键字段：支付渠道预选
            "steamid": None
        }
        
        # 增加 CSRF 自动重试循环 (最多重试 1 次)
        for attempt in range(2):
            headers = {
                "x-csrftoken": self._get_csrf_token(profile),
                "content-type": "application/json",
                "x-requested-with": "XMLHttpRequest",
                "origin": "https://buff.163.com",
                "referer": f"https://buff.163.com/goods/{goods_id}?from=market"
            }
            
            try:
                # 构造完整请求头用于日志打印 (包含 Cookie)
                full_headers = profile.get_headers(referer=headers.get("referer"))
                full_headers.update(headers)
                
                # 仅在第一次尝试时打印，避免刷屏
                if attempt == 0:
                    logger.info(f"<cyan><b>[DEBUG]</b></cyan> <yellow>Batch Preview Request:</yellow>\nURL: {url}\nPayload: {json.dumps(payload, indent=2, ensure_ascii=False)}\nHeaders: {json.dumps(full_headers, indent=2)}")
                
                resp = await self._request("POST", url, profile, json=payload, headers=headers, return_raw=True)
                
                if isinstance(resp, dict): # 可能是重试失败返回的错误字典
                    logger.error(f"<red><b>[DEBUG]</b></red> <red>Batch Preview Request Failed (Dict Return)!</red>\nError: {resp.get('error')}")
                    return None, None, resp.get("error", "Request Failed")

                trace_id = resp.headers.get("buff-cashier-trace-id")
                data = resp.json()
                
                # CSRF 重试逻辑：如果是 CSRF 错误，且是第一次尝试，则重试
                # (因为 _request 内部已经调用了 profile.update_cookies，下次循环时 _get_csrf_token 会拿到新 Token)
                if data.get("code") == "CSRF Verification Error":
                    if attempt == 0:
                        logger.warning(f"🔄 捕获 CSRF 校验失败，Cookie 已自动更新，正在发起重试...")
                        continue
                    else:
                        # 如果重试后依然失败，打印详细诊断信息
                        logger.error(f"🔑 CSRF 校验连续失败 | Cookie长度: {len(profile.cookie) if profile.cookie else 0} | Token提取: {'成功' if self._get_csrf_token(profile) else '失败'}")
                
                # 仅打印状态信息，不再打印 Response Body
                if data.get("code") != "OK":
                    logger.error(f"<red><b>[DEBUG]</b></red> <red>Batch Preview Failed!</red> | Code: {data.get('code')}")
                else:
                    logger.success(f"<green><b>[DEBUG]</b></green> <green>Batch Preview Success!</green>")
                
                if data.get("code") == "OK":
                    return data.get("data"), trace_id, "OK"
                
                return None, trace_id, data.get("code")
            except Exception as e:
                logger.exception(f"💥 _batch_buy_preview 异常: {e}")
                return None, None, str(e)
        
        return None, None, "CSRF Retry Failed"

    async def _batch_buy_create(self, goods_id: int, pay_method: int, total_price: Any, num: int, profile: BrowserProfile, trace_id: str):
        """[Mission] 异步版创建"""
        url = "https://buff.163.com/api/market/goods/batch_buy/create"
        payload = {
            "game": BuffGameType.CSGO.value,
            "goods_id": int(goods_id),
            "pay_method": int(pay_method),
            "frozen_amount": float(total_price), # 修正：必须是 float 数字
            "max_price": str(total_price),      # 修正：必须是 str 字符串
            "num": str(num),                    # 修正：必须是 str 字符串
            "steamid": None
        }
        
        # 增加 CSRF 自动重试循环
        for attempt in range(2):
            headers = {
                "x-csrftoken": self._get_csrf_token(profile),
                "content-type": "application/json",
                "x-requested-with": "XMLHttpRequest",
                "origin": "https://buff.163.com",
                "referer": f"https://buff.163.com/goods/{goods_id}?from=market",
                "buff-cashier-trace-id": trace_id
            }
            
            # 构造完整请求头用于日志打印 (包含 Cookie)
            full_headers = profile.get_headers(referer=headers.get("referer"))
            full_headers.update(headers)
            
            # 仅在第一次尝试时打印，避免刷屏
            if attempt == 0:
                logger.info(f"<cyan><b>[DEBUG]</b></cyan> <yellow>Batch Create Request:</yellow>\nURL: {url}\nPayload: {json.dumps(payload, indent=2, ensure_ascii=False)}\nHeaders: {json.dumps(full_headers, indent=2)}")
            
            res = await self._request("POST", url, profile, json=payload, headers=headers)
            
            # CSRF 重试逻辑
            if res.get("code") == "CSRF Verification Error":
                if attempt == 0:
                    logger.warning(f"🔄 捕获 CSRF 校验失败 (Create)，Cookie 已自动更新，正在发起重试...")
                    continue
                else:
                    logger.error(f"🔑 CSRF 校验连续失败 (Create) | Code: {res.get('code')}")
            
            # 仅打印状态信息，不再打印 Response Body
            if res.get("code") != "OK":
                logger.error(f"<red><b>[DEBUG]</b></red> <red>Batch Create Failed!</red> | Code: {res.get('code')}")
            else:
                logger.success(f"<green><b>[DEBUG]</b></green> <green>Batch Create Success!</green>")
                
            return res.get("data", {}).get("batch_buy_id") if res.get("code") == "OK" else None
            
        return None

    async def _batch_buy_pay(self, batch_buy_id: str, goods_id: int, profile: BrowserProfile):
        """[Mission] 异步版支付预处理"""
        url = "https://buff.163.com/api/market/goods/batch_buy/epay_page_pay"
        params = {"batch_buy_id": batch_buy_id, "_": int(time.time() * 1000)}
        return await self._request("GET", url, profile, params=params)

    async def _batch_buy_check_state(self, batch_buy_id: str, goods_id: int, profile: BrowserProfile):
        """[Mission] 异步版状态轮询"""
        url = "https://buff.163.com/api/market/goods/batch_buy/check_state"
        params = {"batch_buy_id": batch_buy_id, "_": int(time.time() * 1000)}
        return await self._request("GET", url, profile, params=params)

    async def _get_pay_method_description(self, pay_method: int, amount: float, goods_id: int, profile: BrowserProfile):
        """[Mission] 获取支付方式描述 (模拟真人收银台行为)"""
        url = "https://buff.163.com/api/asset/get_pay_method_description/"
        params = {
            "hide_non_epay": "false",
            "query_from": "cashier",
            "pay_method": str(pay_method),
            "amount": f"{float(amount):.2f}",
            "_": int(time.time() * 1000)
        }
        headers = profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        return await self._request("GET", url, profile, params=params, headers=headers)

    async def _refresh_account_balance(self, profile: BrowserProfile) -> Optional[Dict[str, float]]:
        """获取账号最新的余额信息"""
        url = f"https://buff.163.com/api/asset/get_brief_asset/?_={int(time.time() * 1000)}"
        headers = profile.get_headers(referer="https://buff.163.com/user-center/asset/pending_divide/")
        headers.update({"X-Requested-With": "XMLHttpRequest"})
        try:
            res = await self._request("GET", url, profile, headers=headers)
            if res.get("code") == "OK":
                data = res.get("data", {})
                return {
                    "balance": float(data.get("cash_amount", 0)),
                    "pending_balance": float(data.get("pending_divide_amount", 0))
                }
        except Exception as e:
            logger.error(f"❌ 刷新账号余额异常: {e}")
        return None

    async def _report_account_info(self, acc_id: int, status: str = None, balance: float = None, pending_balance: float = None, warning_msg: str = None):
        """向后端报告账号最新状态和余额"""
        url = f"{BACKEND_URL}/api/buff/account/report/status"
        payload = {"id": acc_id}
        if status: payload["status"] = status
        if balance is not None: payload["balance"] = balance
        if pending_balance is not None: payload["pendingBalance"] = pending_balance
        if warning_msg: payload["warningMsg"] = warning_msg
        
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.post(url, json=payload)
                if resp.status_code == 200:
                    logger.info(f"📊 状态/余额上报成功")
                else:
                    logger.warning(f"⚠️ 状态/余额上报失败: {resp.status_code}")
        except Exception as e:
            logger.error(f"❌ 状态/余额上报异常: {e}")
