import asyncio
import time
import random
from typing import List, Dict, Any, Optional
import httpx
from loguru import logger

from engine.context import set_context, account_id_var, account_name_var
from utils.browser_helper import BrowserHelper, BrowserProfile
from utils.proxy_helper import get_proxies

from redis.asyncio import Redis

from engine.sharded_executor import ShardedSpiderExecutor

class AsyncBuffSpider:
    def __init__(self, redis: Optional[Redis] = None):
        self.redis = redis
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

    async def scan_task(self, task_data: Dict[str, Any]):
        """执行扫描任务逻辑 (支持分片采集与多账号协同)"""
        task_id = task_data.get("taskId")
        task_type = task_data.get("taskType")
        task_name = task_data.get("name")
        
        # 判断是否为系统任务（需要分片协同）
        if task_type and task_type >= 2:
            logger.info(f"🚀 [Task-ID: {task_id}] 启动分片协同执行器: {task_name}")
            executor = ShardedSpiderExecutor(self.client)
            await executor.execute(task_data)
            return

        # 普通扫货任务逻辑（保持原样或后续迁移）
        goods_id = task_data.get("goodsId")
        accounts = task_data.get("accounts", [])
        
        if not accounts:
            logger.error(f"❌ 任务 [{task_name}] 未绑定任何账号，无法执行")
            return

        # 扫描间隔
        interval_min = task_data.get("scanIntervalMin", 15)
        interval_max = task_data.get("scanIntervalMax", 30)

        logger.info(f"🔍 开始执行扫描任务: {task_name} (ID: {task_id}, GoodsID: {goods_id})")

        # 为每个账号创建一个扫描协程
        scan_jobs = []
        for account in accounts:
            job = asyncio.create_task(self._account_scan_loop(task_data, account))
            scan_jobs.append(job)
        
        # 等待所有账号扫描任务结束 (通常是由于外部取消或任务停止)
        await asyncio.gather(*scan_jobs, return_exceptions=True)

    async def _account_scan_loop(self, task_data: Dict[str, Any], account: Dict[str, Any]):
        """单个账号的扫描循环"""
        task_id = task_data.get("taskId")
        goods_id = task_data.get("goodsId")
        account_id = account.get("accountId")
        
        # 扫描间隔
        interval_min = task_data.get("scanIntervalMin", 15)
        interval_max = task_data.get("scanIntervalMax", 30)
        
        profile = BrowserProfile(
            cookie=account.get("buffCookie"),
            user_agent=account.get("userAgent")
        )
        
        # 设置账号级上下文
        account_name_var.set(f"Acc-{account_id}")
        account_id_var.set(account_id)

        logger.info(f"👤 账号 [{account_id}] 扫描启动")

        while True:
            try:
                # 1. 执行扫描
                logger.debug(f"正在扫描商品: {goods_id}")
                items = await self.get_goods_list(goods_id, profile)
                
                if items:
                    # 2. 处理匹配逻辑
                    await self._process_items(task_data, items, account)
                
                # 3. 随机等待
                wait_time = random.uniform(interval_min, interval_max)
                await asyncio.sleep(wait_time)
                
            except asyncio.CancelledError:
                logger.info(f"🛑 账号 [{account_id}] 扫描任务已取消")
                break
            except Exception as e:
                logger.error(f"⚠️ 账号 [{account_id}] 扫描异常: {e}")
                await asyncio.sleep(interval_min)

    async def _process_items(self, task_data: Dict[str, Any], items: List[Dict[str, Any]], account: Dict[str, Any]):
        """处理获取到的商品列表，执行匹配与下单"""
        task_id = task_data.get("taskId")
        max_price = task_data.get("maxPrice")
        
        # 暂时只实现价格匹配逻辑
        for item in items:
            item_id = item.get("id")
            price = float(item.get("price"))
            
            # 价格过滤
            if price > float(max_price):
                continue
            
            # 磨损过滤 (后续完善)
            # ...
            
            logger.info(f"🎯 发现匹配商品: {item_id}, 价格: {price} <= {max_price}")
            
            # 分布式锁：防止多账号重复购买同一饰品
            lock_key = f"niro:lock:item:{item_id}"
            # 尝试获取锁，有效期 10 秒
            if await self.redis.set(lock_key, "locked", ex=10, nx=True):
                try:
                    logger.warning(f"🔒 已获取锁 [{lock_key}]，准备执行下单...")
                    # TODO: 执行下单 API
                    # await self._create_order(item, account)
                finally:
                    # 锁由 TTL 自动释放，或者下单成功后不主动释放防止其他账号再次尝试
                    pass
            else:
                logger.info(f"⏩ 商品 {item_id} 已被其他账号锁定")
