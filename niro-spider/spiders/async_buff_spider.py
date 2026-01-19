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

class AsyncBuffSpider:
    def __init__(self, redis: Optional[Redis] = None):
        self.redis = redis
        self.notifier = Notifier()
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(10.0, connect=5.0),
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
            follow_redirects=True
        )

    async def close(self):
        await self.client.aclose()

    async def _request(self, method: str, url: str, profile: BrowserProfile, proxy: Optional[str] = None, **kwargs) -> Dict[str, Any]:
        """封装基础请求逻辑，包含自动重试和代理切换"""
        headers = profile.get_headers()
        
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
            data = response.json()
            
            if data.get("code") == "Login Required":
                logger.error("🔑 [Cookie失效] 账号需要重新登录")
                return {"error": "LOGIN_REQUIRED"}
            
            return data
            
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
            items = data.get("data", {}).get("items", [])
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
        
        # 判断是否为系统任务（需要分片协同）
        if task_type and self._get_float(task_type) >= 2:
            logger.info(f"🚀 [Task-ID: {task_id}] 启动分片协同执行器: {task_name}")
            executor = ShardedSpiderExecutor(self.client, redis_async=self.redis)
            await executor.execute(task_data)
            return

        # 普通扫货任务逻辑
        goods_id = self._extract_value(task_data.get("goodsId"))
        raw_accounts = self._extract_list(task_data.get("accounts", []))
        
        if not raw_accounts:
            logger.error(f"❌ 任务 [{task_name}] 未绑定任何账号，无法执行")
            return

        # 过滤出扫描账号和下单账号
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

        if not scan_accounts:
            logger.error(f"❌ 任务 [{task_name}] 未绑定任何扫描账号，无法执行")
            return

        # 将账号信息放入 task_data 供子协程共享
        task_data["scan_accounts"] = scan_accounts
        task_data["buy_accounts"] = buy_accounts
        
        logger.info(f"🔍 开始执行扫描任务: {task_name} (ID: {task_id}, GoodsID: {goods_id}, 账号数: {len(scan_accounts)})")
        if not buy_accounts:
            logger.warning(f"💡 [系统提示] 当前任务未配置下单账号，将进入“纯监控模式”")
        
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

    async def _process_items(self, task_data: Dict[str, Any], items: List[Dict[str, Any]], account: Dict[str, Any]):
        """处理获取到的商品列表，执行匹配与下单"""
        task_id = self._extract_value(task_data.get("taskId"))
        user_id = self._extract_value(task_data.get("userId"))
        max_price = self._get_float(task_data.get("maxPrice"), 0.0)
        acc_name = account_name_var.get()
        # 使用 scan_task 中过滤好的下单账号列表
        buy_accounts = task_data.get("buy_accounts") or []
        
        # 1. 价格过滤与分组
        matched_items = []
        for item in items:
            price = float(item.get("price"))
            if price <= max_price:
                matched_items.append(item)
        
        if not matched_items:
            return

        # 按价格分组
        price_groups = {}
        for item in matched_items:
            p = float(item.get("price"))
            if p not in price_groups:
                price_groups[p] = []
            price_groups[p].append(item)

        # 2. 遍历价格分组处理
        for price, group in price_groups.items():
            count = len(group)
            # 取第一个作为代表展示
            first_item = group[0]
            item_id = first_item.get("id")
            paintwear = first_item.get("paintwear")
            
            # 检查是否有下单账号
            if not buy_accounts:
                # 纯监控模式：检查是否有新挂单（只要组内有一个是新的就报，但跳过已报过的 ID）
                new_items_in_group = []
                for it in group:
                    it_id = it.get("id")
                    report_cache_key = f"niro:report:cache:{it_id}"
                    if not await self.redis.exists(report_cache_key):
                        new_items_in_group.append(it)
                
                if not new_items_in_group:
                    continue

                # 记录所有新挂单为已报
                for it in new_items_in_group:
                    await self.redis.set(f"niro:report:cache:{it.get('id')}", "1", ex=180)

                # 合并日志输出
                count_suffix = f" (该价位共 {count} 个挂单)" if count > 1 else ""
                logger.info(
                    f"🔍 [账号: {acc_name}] [发现捡漏机会]{count_suffix}\n"
                    f"   挂单ID: {item_id}{' 等' if count > 1 else ''}\n"
                    f"   价格: ¥{price} (低于预设 ¥{max_price})\n"
                    f"   磨损: {paintwear or 'N/A'}"
                )
                logger.info(f"💡 [账号: {acc_name}] [系统提示] 当前任务未配置下单账号，仅记录行情，未触发尝试下单。")
                
                # 推送合并通知
                try:
                    goods_name = self._extract_value(task_data.get("name")) or "未知饰品"
                    goods_id = self._extract_value(task_data.get("goodsId"))
                    link = f"https://buff.163.com/goods/{goods_id}"
                    
                    title = f"🔍 发现捡漏机会 ({count}个)" if count > 1 else "🔍 发现捡漏机会"
                    description = f"饰品: {goods_name}\n" \
                                 f"价格: ¥{price} (预设: ¥{max_price})\n" \
                                 f"磨损: {paintwear or 'N/A'}\n" \
                                 f"挂单ID: {item_id}{'...' if count > 1 else ''}"
                    
                    self.notifier.send_textcard(title, description, url=link, btntxt="立即前往秒杀", user_id=user_id)
                except Exception as ne:
                    logger.error(f"⚠️ 推送通知失败: {ne}")

                # 更新统计
                stats_key = f"{REDIS_TASK_STATS_PREFIX}{task_id}"
                try:
                    stats_json = await self.redis.get(stats_key)
                    stats = json.loads(stats_json) if stats_json else {}
                    stats["discovery_count"] = stats.get("discovery_count", 0) + len(new_items_in_group)
                    await self.redis.set(stats_key, json.dumps(stats), ex=86400)
                except Exception as e:
                    logger.error(f"⚠️ 更新任务统计失败: {e}")
                continue

            # 实战模式：仍需对组内每个挂单尝试加锁下单
            for it in group:
                it_id = it.get("id")
                it_price = float(it.get("price"))
                
                # 分布式锁：防止多账号重复购买同一饰品
                lock_key = f"niro:lock:item:{it_id}"
                if await self.redis.set(lock_key, "locked", ex=10, nx=True):
                    try:
                        logger.info(
                            f"🎯 [账号: {acc_name}] [发现匹配挂单]\n"
                            f"   挂单ID: {it_id}\n"
                            f"   价格: ¥{it_price} (低于预设 ¥{max_price})"
                        )
                        logger.warning(f"🔒 [账号: {acc_name}] 已获取锁 [{lock_key}]，准备执行下单...")
                        # TODO: 执行下单 API
                        # await self._create_order(it, account)
                    finally:
                        pass
                else:
                    logger.debug(f"⏩ [账号: {acc_name}] 挂单 {it_id} 已被其他账号锁定")
