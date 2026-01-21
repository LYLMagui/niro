import asyncio
import time
import random
import json
from typing import List, Dict, Any, Optional
import httpx
from loguru import logger

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

    async def close(self):
        await self.client.aclose()

    async def _request(self, method: str, url: str, profile: BrowserProfile, proxy: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """封装基础请求逻辑，包含自动重试和代理切换"""
        # 1. 获取基础 Headers
        headers = profile.get_headers()
        
        # 2. 如果 kwargs 中包含 headers，将其弹出并合并，避免重复传递给 _do_request 导致参数冲突
        if "headers" in kwargs:
            extra_headers = kwargs.pop("headers")
            if isinstance(extra_headers, dict):
                headers.update(extra_headers)
        
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
                    return await self._do_request(proxy_client, method, url, headers, attempt, **kwargs)
            else:
                return await self._do_request(client, method, url, headers, attempt, **kwargs)
        
        return {"error": "MAX_RETRIES_EXCEEDED"}

    async def _do_request(self, client: httpx.AsyncClient, method: str, url: str, headers: dict, attempt: int, **kwargs) -> Dict[str, Any]:
        try:
            response = await client.request(method, url, headers=headers, **kwargs)
            
            if response.status_code == 429:
                logger.warning(f"🚫 [429] 触发频率限制，等待重试 ({attempt + 1}/3)")
                await asyncio.sleep(2 ** attempt + random.random())
                return await self._do_request(client, method, url, headers, attempt + 1, **kwargs) if attempt < 2 else {"error": "RATE_LIMITED"}
            
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
            logger.info(f"🚀 [Task-ID: {task_id}] 启动下单监听模式: {task_name}")
            trade_job = asyncio.create_task(self._trade_task_loop(task_data))
            monitor_task = asyncio.create_task(self._monitor_stop_signal(task_id, [trade_job]))
            await asyncio.gather(trade_job, monitor_task, return_exceptions=True)
            await self._callback_status(task_id, 0 if monitor_task.done() and not monitor_task.cancelled() else 2)
            return

        # 3. 判断是否为系统任务（需要分片协同）
        if task_type and self._get_float(task_type) >= 2:
            logger.info(f"🚀 [Task-ID: {task_id}] 启动分片协同执行器: {task_name}")
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
                
                if not sell_order_ids:
                    continue
                
                # 随机选择一个可用的下单账号
                available_buyers = []
                for buyer in buy_accounts:
                    buyer_id = self._extract_value(buyer.get("accountId"))
                    if not await self.redis.exists(f"niro:account:busy:{buyer_id}"):
                        available_buyers.append(buyer)
                
                if not available_buyers:
                    logger.warning(f"⏳ [下单任务: {task_name}] 收到信号，但所有下单账号均繁忙，跳过本次处理")
                    continue
                
                buyer = random.choice(available_buyers)
                if not buyer.get("profile"):
                    buyer["profile"] = BrowserHelper.create_profile(cookie=buyer.get("buffCookie"))
                
                logger.info(f"🎯 [下单任务: {task_name}] 接收到购买信号，指派 [{buyer.get('accountName')}] 执行购买 (ID数: {len(sell_order_ids)})")
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
                logger.info(f"🛑 [Task-ID: {task_id}] 收到停止信号，正在取消所有扫描子任务...")
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
                    logger.info(f"✅ [Task-ID: {task_id}] 状态回调成功: {status}")
                else:
                    logger.error(f"❌ [Task-ID: {task_id}] 状态回调失败: {resp.status_code}, {resp.text}")
        except Exception as e:
            logger.error(f"❌ [Task-ID: {task_id}] 状态回调异常: {e}")

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
            logger.info(f"👤 [账号: {acc_name}] {status_text} {'(周期模式: ' + str(duration_minutes) + '/' + str(rest_period) + 'min)' if is_cycle_mode else '(持续模式)'}")

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
                            logger.info(f"😴 [账号: {acc_name}] 工作周期结束 ({duration_minutes}min)，进入强制休眠 ({rest_period}min)...")
                            # 休眠期间仍需响应取消信号
                            try:
                                await asyncio.sleep(rest_period * 60)
                            except asyncio.CancelledError:
                                raise
                            
                            logger.info(f"⏰ [账号: {acc_name}] 休眠结束，重新开始工作周期")
                            cycle_start_time = time.time()
                            continue

                    # 0. 动态准入控制 (精准错峰)
                    wait_time = await self._get_dynamic_wait_time(task_id, ideal_interval)
                    if wait_time > 0:
                        # 静默处理：微小的等待不再打印 INFO 日志，改为 DEBUG
                        if wait_time > 1.0:
                            logger.debug(f"🛡️ [账号: {acc_name}] 流量整形中：精准错峰等待 {int(wait_time * 1000)}ms")
                        await asyncio.sleep(wait_time)

                    # 1. 执行扫描
                    action_text = "监控" if is_monitor_only else "扫描"
                    start_time = time.time()
                    items = await self.get_goods_list(goods_id, profile)
                    latency = (time.time() - start_time) * 1000
                    
                    # 2. 处理结果与行情展示
                    if items:
                        # 无论是否命中，都输出行情概览
                        current_min_price = float(items[0].get("price", 0))
                        price_diff = current_min_price - max_price
                        diff_str = f"+¥{price_diff:.2f}" if price_diff > 0 else f"-¥{abs(price_diff):.2f}"
                        status_icon = "⏳" if price_diff > 0 else "🎯"
                        
                        goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                        logger.info(
                            f"💰 [行情] {goods_name} | 最低: ¥{current_min_price:.2f} | 目标: ≤¥{max_price:.2f} | 差距: {diff_str} {status_icon}"
                        )
                        
                        # 处理匹配逻辑
                        await self._process_items(task_data, items, account)
                    
                    # 3. 周期扫描完成摘要
                    next_wait = random.uniform(interval_min, interval_max)
                    logger.info(f"✅ [账号: {acc_name}] 周期扫描完成 | 响应: {int(latency)}ms | 下次准入: {next_wait:.1f}s后 | 状态: 安全 🛡️")
                    
                    await asyncio.sleep(next_wait)
                    
                except asyncio.CancelledError:
                    logger.info(f"🛑 [账号: {acc_name}] 扫描任务已取消")
                    break
                except Exception as e:
                    logger.error(f"⚠️ [账号: {acc_name}] 扫描循环异常: {e}")
                    await asyncio.sleep(interval_min)
        except Exception as e:
            logger.exception(f"💥 [账号: 未知] 协程初始化失败: {e}")

    def _get_csrf_token(self, profile: BrowserProfile) -> str:
        """从 Cookie 中提取 CSRF Token"""
        if not profile.cookie:
            return ""
        import re
        match = re.search(r'csrf_token=([^;]+)', profile.cookie)
        return match.group(1) if match else ""

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
                logger.error(f"❌ [账号: {acc_name}] 下单失败：缺少 buffCookie")
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
        
        headers = profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        headers.update({
            "X-CSRFToken": self._get_csrf_token(profile),
            "Content-Type": "application/json",
        })

        logger.info(f"🛒 [账号: {acc_name}] [发起购买] GoodsID={goods_id} | ItemID={item_id} | Price={price}")
        
        try:
            # 使用统一的 _request 逻辑，包含 HTML 预检和异常处理
            res_json = await self._request("POST", url, profile, json=payload, headers=headers)
            
            if res_json.get("code") == "OK":
                data = res_json.get("data", {})
                order_id = data.get("id")
                logger.info(f"✅ [账号: {acc_name}] 下单成功! 订单号: {order_id}")
                
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
                logger.error(f"❌ [账号: {acc_name}] 下单失败: {code} - {error_msg}")
                
                # 风控判断 (根据实际 Buff 返回码调整)
                if code in ["Risk Control", "Account Banned", "Action Forbidden"]:
                    return "RISK", error_msg
                
                # 其他失败 (如库存不足、余额不足等)
                return "FAILED", error_msg
        except LoginRequiredError:
            logger.error(f"🔑 [账号: {acc_name}] Cookie 已失效，无法下单")
            raise
        except Exception as e:
            logger.error(f"⚠️ [账号: {acc_name}] 下单请求异常: {e}")
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
        
        # 1. 价格过滤
        if int(task_type) == 1:
            matched_items = []
            for it in items:
                buy_price = self._get_float(it.get("price"), 0.0)
                ref_price = self._get_float(it.get("sell_min_price"), 0.0)
                if ref_price <= 0:
                    continue
                profit = ref_price * 0.975 - buy_price
                if profit >= min_profit:
                    it["estimated_profit"] = profit
                    matched_items.append(it)
        else:
            matched_items = [it for it in items if self._get_float(it.get("price"), 0.0) <= max_price]
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

            logger.info(f"🔍 [账号: {acc_name}] [监控发现] 匹配到 {len(new_items)} 个挂单，已推送通知")
            
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

        # 3. 关联任务健康检查 (如果设置了 target_task_id)
        if target_task_id:
            heartbeat_key = "niro:task:heartbeat"
            last_heartbeat = await self.redis.hget(heartbeat_key, str(target_task_id))
            # 如果超过 60 秒没有心跳，视为失效
            if not last_heartbeat or (int(time.time() * 1000) - int(last_heartbeat)) > 60000:
                logger.error(f"🚨 [Task-ID: {task_id}] 关联的下单任务 [{target_task_id}] 已失效或未启动，路由中断")
                
                # 发送告警通知
                goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                alert_title = "🚨 任务路由失效告警"
                alert_desc = f"扫描任务: {goods_name} (ID: {task_id})\n" \
                             f"关联下单任务 (ID: {target_task_id}) 状态异常\n" \
                             f"原因: 下单任务未启动或已意外停止，请检查配置！"
                self.notifier.send_textcard(alert_title, alert_desc, user_id=user_id)
                return

        # 4. 饰品加锁与去重 (批量撮合)
        buy_count = int(task_data.get("buyCount") or 0)
        success_count = int(task_data.get("successCount") or 0)
        remaining_count = max(0, buy_count - success_count)
        
        if buy_count > 0 and remaining_count <= 0:
            return

        pending_it_ids = []
        for it in matched_items:
            if buy_count > 0 and len(pending_it_ids) >= remaining_count:
                break
                
            it_id = it.get("id")
            pending_key = f"niro:pending_purchase:{it_id}"
            lock_key = f"niro:lock:item:{it_id}"
            
            if await self.redis.exists(pending_key): continue
            if await self.redis.set(lock_key, "locked", ex=60, nx=True):
                if await self.redis.set(pending_key, "processing", ex=120, nx=True):
                    pending_it_ids.append(it_id)
                else:
                    await self.redis.delete(lock_key)

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
            await self.redis.rpush(queue_key, json.dumps(signal_data))
            logger.warning(f"📡 [账号: {acc_name}] 匹配成功，已将信号路由至下单任务 [{target_task_id}] | 聚合数: {len(pending_it_ids)}")
        else:
            # 本地模式：指派绑定的下单账号执行
            available_buyers = []
            for buyer in buy_accounts:
                buyer_id = self._extract_value(buyer.get("accountId"))
                busy_key = f"niro:account:busy:{buyer_id}"
                if not await self.redis.exists(busy_key):
                    available_buyers.append(buyer)
            
            if not available_buyers:
                log_throttle_key = f"niro:log:busy_throttle:{task_id}"
                if not await self.redis.exists(log_throttle_key):
                    logger.debug(f"⏳ [账号: {acc_name}] 下单账号均处于 Busy 状态，跳过本次撮合")
                    await self.redis.set(log_throttle_key, "1", ex=10)
                # 释放刚刚锁定的饰品
                await self._release_items(pending_it_ids)
                return

            buyer = random.choice(available_buyers)
            if not buyer.get("profile"):
                buyer["profile"] = BrowserHelper.create_profile(cookie=buyer.get("buffCookie"))
                
            logger.warning(f"🚀 [账号: {acc_name}] 发现机会，指派 [{buyer.get('accountName')}] 发起异步批量下单 (聚合数: {len(pending_it_ids)})")
            asyncio.create_task(self.async_buy_v3(pending_it_ids, buyer, task_data))

    async def async_buy_v3(self, sell_order_ids: List[str], account: Dict[str, Any], task_data: Dict[str, Any]):
        """
        [Mission] 异步化重写 buy_v3 (五步核心流程) - 优化版
        引入“人类行为模拟”冷却机制和加固确认逻辑
        """
        acc_id = self._extract_value(account.get("accountId"))
        acc_name = account.get("accountName") or "未知账号"
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
            await asyncio.sleep(random.uniform(0.1, 0.3))

            # --- 1. 批量预览 (Preview) ---
            preview_data, trace_id, error_code = await self._batch_buy_preview(goods_id, current_ids, profile)
            
            if error_code == "Already Paying":
                logger.error(f"🚨 [账号: {acc_name}] 触发 Already Paying 熔断，标记 Busy 200s")
                await self.redis.set(busy_key, "1", ex=200)
                await self._release_items(current_ids)
                should_clear_busy = False
                return

            if not preview_data:
                logger.error(f"❌ [账号: {acc_name}] 批量预览失败: {error_code}")
                await self._release_items(current_ids)
                return

            pay_method = BuffPaymentMethod.BALANCE.value
            selected_method = next((m for m in preview_data.get("pay_methods", []) if m.get("value") == int(pay_method)), None)
            if not selected_method:
                logger.error(f"❌ [账号: {acc_name}] 不支持余额支付")
                await self._release_items(current_ids)
                return
            
            # total_price 是包含手续费的总价，用于创建订单和支付
            total_price = selected_method.get("price_with_pay_fee")
            # unit_price 是饰品的单价（不含手续费），用于最终确认请求
            # 注意：预览接口返回的 price 是所有饰品的原始总价
            original_total_price = preview_data.get("price")
            unit_price = float(original_total_price) / len(current_ids)
            
            # 模拟真人思考与选择支付方式
            await asyncio.sleep(random.uniform(0.8, 1.5))

            # --- 2. 创建订单 (Create) ---
            logger.info(f"📝 [账号: {acc_name}] 准备预创建订单，待下单数量: {len(current_ids)}")
            batch_buy_id = await self._batch_buy_create(goods_id, pay_method, total_price, len(current_ids), profile, trace_id)
            if not batch_buy_id:
                await self._release_items(current_ids)
                return
            
            # 设置初始 Busy 标志
            await self.redis.set(busy_key, "1", ex=200)
            logger.info(f"🔒 [账号: {acc_name}] 已锁定 Busy 状态 | BatchID: {batch_buy_id}")

            # --- 3. 支付预处理 (Pay Prep) ---
            await asyncio.sleep(random.uniform(0.5, 0.8))
            pay_res = await self._batch_buy_pay(batch_buy_id, goods_id, profile)
            if not pay_res or pay_res.get("code") != "OK":
                logger.error(f"❌ [账号: {acc_name}] 支付预处理失败 | Msg: {pay_res.get('msg') if pay_res else 'Request Failed'}")
                await self._release_items(current_ids)
                return
            
            pay_data = pay_res.get("data", {})
            if pay_data.get("auto_pay") is False:
                epay_url = pay_data.get("elements", {}).get("url")
                if epay_url:
                    logger.warning(f"⚠️ [账号: {acc_name}] 需手动支付授权！延长 Busy 锁至 180s")
                    await self.redis.set(busy_key, "1", ex=180) # 延长锁时间，给人工预留时间
                    logger.warning(f"👉 支付链接: {epay_url}")
                    
                    try:
                        goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                        title = "⚠️ 需手动支付授权"
                        description = f"账号: {acc_name}\n饰品: {goods_name}\n金额: ¥{total_price}\n状态: 等待人工操作"
                        self.notifier.send_textcard(title, description, url=epay_url, btntxt="点击前往支付", user_id=user_id)
                    except Exception as ne:
                        logger.error(f"⚠️ 支付链接推送失败: {ne}")
            
            logger.info(f"💰 [账号: {acc_name}] 支付预处理完成，开始轮询支付状态...")

            # --- 4. 轮询状态 (Polling Status) ---
            is_ready = False
            for i in range(180):
                check_res = await self._batch_buy_check_state(batch_buy_id, goods_id, profile)
                
                if not check_res or check_res.get("code") != "OK":
                    logger.warning(f"⚠️ [账号: {acc_name}] 轮询支付状态异常 ({i}s): {check_res.get('msg') if check_res else 'Request Failed'}")
                
                state = check_res.get("data", {}).get("state") if check_res else None
                if state == 2:
                    is_ready = True
                    logger.info(f"✅ [账号: {acc_name}] 支付状态已就绪 (耗时: {i}s)")
                    break
                
                if state == 4:
                    logger.error(f"❌ [账号: {acc_name}] 支付失败或已关闭 (State: 4)")
                    break
                
                if i > 0 and i % 10 == 0:
                    logger.info(f"⏳ [账号: {acc_name}] 支付状态轮询中... 当前状态: {state} ({i}s/180s)")
                
                await asyncio.sleep(1.0 + random.uniform(0, 0.2)) # 增加微小抖动

            if not is_ready:
                logger.error(f"❌ [账号: {acc_name}] 支付状态轮询超时")
                await self._release_items(current_ids)
                return

            # --- 5. 最终确认 (Final Confirm) ---
            # 增加确认前的冷静期，模拟真人从支付完成回到页面的动作
            await asyncio.sleep(random.uniform(0.8, 1.5))
            
            url = "https://buff.163.com/api/market/goods/buy"
            # 协议核心修正：最终确认请求的 price 必须是饰品单价（不含手续费），且为字符串格式
            price_str = f"{unit_price:.2f}"
            
            payload = {
                "game": BuffGameType.CSGO.value,
                "goods_id": int(goods_id),
                "sell_order_id": current_ids[0],
                "price": price_str,
                "batch": 1,
                "pay_method": int(pay_method),
                "allow_tradable_cooldown": 0,
                "hide_non_epay": False,
                "batch_id": "",
                "batch_buy_id": batch_buy_id,
                "steamid": None
            }
            
            logger.info(f"🚀 [账号: {acc_name}] 发起最终确认 | Price(Unit): {price_str} | BatchID: {batch_buy_id}")
            
            # 加固：重新获取最新的 CSRF Token 并构建请求
            # 协议深度对齐：移除 buff-cashier-trace-id，增加 X-Requested-With 和 Accept
            headers = profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
            headers.update({
                "X-CSRFToken": self._get_csrf_token(profile),
                "Content-Type": "application/json",
                "X-Requested-With": "XMLHttpRequest",
                "Accept": "application/json, text/javascript, */*; q=0.01"
            })
            # 注意：最终确认请求在 HAR 中并不携带 buff-cashier-trace-id

            # 详尽记录最终响应，防止 None 陷阱
            try:
                # 直接调用 _request，它内部会处理 JSON 解析
                res_json = await self._request("POST", url, profile, json=payload, headers=headers)
                
                if res_json.get("code") == "OK":
                    order_id = res_json.get("data", {}).get("id")
                    logger.info(f"✨ [账号: {acc_name}] 批量下单圆满成功! 订单号: {order_id}")
                    self._last_buy_times[acc_id] = time.time() # 记录成功时间
                    
                    goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                    title = f"✨ 批量下单成功 ({len(current_ids)}件)"
                    description = f"账号: {acc_name}\n饰品: {goods_name}\n总价: ¥{price_str}\n订单号: {order_id}"
                    self.notifier.send_textcard(title, description, user_id=user_id)
                else:
                    error_msg = res_json.get("msg") or res_json.get("error") or "Unknown Error"
                    logger.error(f"❌ [账号: {acc_name}] 最终确认失败 | Code: {res_json.get('code')} | Msg: {error_msg} | Payload: {payload}")
                    await self._release_items(current_ids)
            except Exception as final_e:
                logger.error(f"💥 [账号: {acc_name}] 最终确认阶段发生异常: {final_e}")
                await self._release_items(current_ids)

        except Exception as e:
            logger.exception(f"💥 [账号: {acc_name}] 异步下单链路崩溃: {e}")
            await self._release_items(current_ids)
        finally:
            # --- 6. 强制余温冷却 (Post-Order Cooldown) ---
            # 成功下单后休息 1-2 分钟，确保账号安全
            cooldown = random.uniform(60.0, 120.0)
            logger.info(f"🧊 [账号: {acc_name}] 下单任务结束，进入强制余温冷却 (1-2分钟): {cooldown:.2f}s")
            await asyncio.sleep(cooldown)
            
            if should_clear_busy:
                await self.redis.delete(busy_key)
                logger.info(f"🔓 [账号: {acc_name}] Busy 锁已释放")

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
        headers = profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        headers.update({
            "X-CSRFToken": self._get_csrf_token(profile),
            "Content-Type": "application/json"
        })
        
        try:
            # 使用临时的 AsyncClient 获取 TraceID，因为 _request 目前只返回 json
            async with httpx.AsyncClient(timeout=10.0, verify=False) as client:
                resp = await client.post(url, headers=headers, json=payload)
                trace_id = resp.headers.get("buff-cashier-trace-id")
                data = resp.json()
                if data.get("code") == "OK":
                    return data.get("data"), trace_id, "OK"
                return None, trace_id, data.get("code")
        except Exception as e:
            return None, None, str(e)

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
        headers = profile.get_headers(referer=f"https://buff.163.com/goods/{goods_id}?from=market")
        headers.update({
            "X-CSRFToken": self._get_csrf_token(profile),
            "Content-Type": "application/json",
            "buff-cashier-trace-id": trace_id
        })
        res = await self._request("POST", url, profile, json=payload, headers=headers)
        return res.get("data", {}).get("batch_buy_id") if res.get("code") == "OK" else None

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
