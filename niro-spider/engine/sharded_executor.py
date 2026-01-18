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
from storage.redis_pool import redis_client

# 动态导入蜘蛛逻辑中的保存方法 (避免循环依赖)
def get_save_category_func():
    from spiders.get_buff_goods_category import save_categories
    return save_categories

def get_save_goods_func():
    from spiders.get_buff_goods import save_goods_batch
    return save_goods_batch

class AccountInvalidException(Exception):
    """账号失效异常 (401/429)"""
    def __init__(self, account_id: int, reason: str, category_ids: List[int]):
        self.account_id = account_id
        self.reason = reason
        self.category_ids = category_ids
        super().__init__(f"Account {account_id} invalid: {reason}")

class ShardedSpiderExecutor:
    def __init__(self, spider_client: httpx.AsyncClient):
        self.client = spider_client
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

    def _extract_list(self, data: Any) -> List[Any]:
        """处理 Jackson 序列化可能带有的类型信息 (e.g. ['java.util.ArrayList', [...]])"""
        if isinstance(data, list) and len(data) == 2 and isinstance(data[0], str) and data[0].startswith("java.util"):
            return data[1]
        return data if isinstance(data, list) else []

    async def execute(self, task_data: Dict[str, Any]):
        """执行分片抓取任务"""
        self.task_id = task_data.get("taskId")
        self.user_id = task_data.get("userId")
        self.task_type = task_data.get("taskType")
        
        # 提取数据并处理 Jackson 序列化格式
        accounts = self._extract_list(task_data.get("accounts", []))
        category_ids = self._extract_list(task_data.get("categoryIds", []))
        
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

        while self.pending_categories and self.active_accounts:
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
                if isinstance(res, AccountInvalidException):
                    await self._handle_account_failure(res)
                elif isinstance(res, Exception):
                    logger.error(f"[Task-ID: {self.task_id}] 抓取过程中发生未知错误: {res}")

        logger.info(f"🏁 [Task-ID: {self.task_id}] 分片抓取结束。完成: {len(self.finished_categories)}, 剩余: {len(self.pending_categories)}")

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
        
        # 设置协程隔离上下文
        account_id_var.set(account_id)
        account_name_var.set(account.get("accountName", f"Acc-{account_id}"))
        
        processed_cats = []
        try:
            for cat_id in category_ids:
                async with limiter:
                    logger.info(f"[Account-ID: {account_id}] Processing Category: {cat_id}")
                    
                    # 执行抓取
                    success = await self._crawl_category(account, cat_id)
                    
                    if success:
                        processed_cats.append(cat_id)
                        self.finished_categories.add(cat_id)
                        self.pending_categories.remove(cat_id)
                        self._processed_history.append((time.time(), 1))
                        # 更新 Redis 进度与心跳
                        await self._update_progress()
                    else:
                        logger.warning(f"[Account-ID: {account_id}] Failed to process Category: {cat_id}")
                        
        except AccountInvalidException as e:
            raise e
        except Exception as e:
            logger.error(f"[Account-ID: {account_id}] 抓取异常: {e}")
            
        return processed_cats

    async def _crawl_category(self, account: Dict[str, Any], category_id: int) -> bool:
        """实际抓取逻辑 (适配不同任务类型)"""
        if self.task_type == 2: # 同步分类
            return await self._crawl_category_tree(account, category_id)
        elif self.task_type == 3: # 同步商品
            return await self._crawl_category_goods(account, category_id)
        else:
            logger.error(f"Unknown task type: {self.task_type}")
            return False

    async def _crawl_category_tree(self, account: Dict[str, Any], p_cat_id: int) -> bool:
        """同步分类树逻辑：抓取一级分类下的所有二级分类"""
        acc_id = account["accountId"]
        acc_name = account.get("accountName", f"Acc-{acc_id}")
        
        # 1. 获取一级分类信息
        from storage.models import BuffGoodsCategory
        from storage.database import Session
        session = Session()
        try:
            p_cat = session.query(BuffGoodsCategory).filter(BuffGoodsCategory.id == p_cat_id).first()
            if not p_cat:
                logger.error(f"[Account-ID: {acc_id}] Category ID {p_cat_id} not found in DB")
                return False
            type_internal = p_cat.internal_name
            type_name = p_cat.name
        finally:
            Session.remove()

        logger.info(f"[Account-ID: {acc_id}] 正在同步分类树: {type_name} ({type_internal})")
        
        headers = {
            "Cookie": account["buffCookie"],
            "User-Agent": account.get("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        proxy = account.get("proxy")
        
        processed_in_type = set()
        all_sub_categories = []
        
        try:
            for page in range(1, 11): # 分类树通常不需要抓太多页
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
                    logger.error(f"[Account-ID: {acc_id}] API Error: {data.get('msg')}")
                    break
                
                # 解析子分类
                items = data.get("data", {}).get("items", [])
                for item in items:
                    tags = item.get("goods_info", {}).get("info", {}).get("tags", {})
                    # 提取该商品所属的所有标签作为潜在分类
                    for tag_key, tag_info in tags.items():
                        # 我们只关心类型和武器标签
                        if tag_key in ["type", "weapon"]:
                            internal_name = tag_info.get("internal_name")
                            if internal_name and internal_name not in processed_in_type:
                                processed_in_type.add(internal_name)
                                all_sub_categories.append({
                                    "name": tag_info.get("localized_name"),
                                    "internal_name": internal_name,
                                    "parent_id": p_cat_id,
                                    "category_type": tag_key
                                })
                
                if not items:
                    break
                    
            if all_sub_categories:
                save_func = get_save_category_func()
                save_func(all_sub_categories)
                logger.info(f"[Account-ID: {acc_id}] 成功同步 {len(all_sub_categories)} 个子分类")
                return True
            
            return False
            
        except Exception as e:
            if isinstance(e, AccountInvalidException): raise e
            logger.error(f"[Account-ID: {acc_id}] 同步分类树失败: {e}")
            return False

    async def _crawl_category_goods(self, account: Dict[str, Any], category_id: int) -> bool:
        """同步分类商品逻辑：抓取分类下的所有商品"""
        acc_id = account["accountId"]
        
        # 1. 获取分类元数据
        from storage.models import BuffGoodsCategory
        from storage.database import Session
        session = Session()
        try:
            cat = session.query(BuffGoodsCategory).filter(BuffGoodsCategory.id == category_id).first()
            if not cat: return False
            internal_name = cat.internal_name
            cat_type = cat.category_type or "category"
        finally:
            Session.remove()

        headers = {
            "Cookie": account["buffCookie"],
            "User-Agent": account.get("userAgent")
        }
        proxy = account.get("proxy")
        
        try:
            all_goods = []
            for page in range(1, 100): # 最大 100 页
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
                if data.get("code") != "OK": break
                
                items = data.get("data", {}).get("items", [])
                if not items: break
                
                for item in items:
                    all_goods.append({
                        "goods_id": item["id"],
                        "name": item["name"],
                        "market_hash_name": item.get("market_hash_name"),
                        "short_name": item.get("short_name"),
                        "category_id": category_id
                    })
                
                # 批量保存以减轻内存压力
                if len(all_goods) >= 100:
                    save_func = get_save_goods_func()
                    save_func(all_goods, category_id)
                    all_goods = []
                    
            if all_goods:
                save_func = get_save_goods_func()
                save_func(all_goods, category_id)
                
            return True
        except Exception as e:
            if isinstance(e, AccountInvalidException): raise e
            logger.error(f"[Account-ID: {acc_id}] 同步商品失败: {e}")
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
            acc_name = acc_info.get("accountName", f"Acc-{acc_id}")
            
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
        await redis_client.set(stats_key, json.dumps(progress, ensure_ascii=False), ex=3600)
        
        # 3. 更新任务心跳
        heartbeat_hash = "niro:task:heartbeat"
        await redis_client.hset(heartbeat_hash, str(self.task_id), int(time.time() * 1000))
