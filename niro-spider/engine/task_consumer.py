import asyncio
import json
import uuid
import secrets
import signal
import time
import os
import sys
from typing import List, Dict, Any

# 将项目根目录添加到 sys.path
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(current_dir)
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from loguru import logger
from redis.asyncio import Redis

from config import settings
from engine.context import set_context
from spiders.async_buff_spider import AsyncBuffSpider
from utils.logger import setup_logging

# 队列优先级定义 (符合 v2.4.0 规范：Redis 阻塞式监听模式)
QUEUES = [
    "niro:tasks:priority:high",
    "niro:tasks:priority:medium",
    "niro:tasks:priority:low"
]

class TaskConsumer:
    def __init__(self):
        self.redis: Redis = None
        self.running = True
        self._tasks = set()
        self.spider = None

    async def init_redis(self):
        self.redis = Redis(
            host=settings.REDIS_HOST,
            port=settings.REDIS_PORT,
            password=settings.REDIS_PASSWORD,
            db=settings.REDIS_DB,
            decode_responses=True
        )
        self.spider = AsyncBuffSpider(redis=self.redis)
        logger.info(f"🚀 [Async Engine] 已建立 Redis 连接: {settings.REDIS_HOST}:{settings.REDIS_PORT}")

    async def stop(self):
        self.running = False
        if self._tasks:
            logger.info(f"正在安全退出，等待 {len(self._tasks)} 个任务完成...")
            await asyncio.gather(*self._tasks, return_exceptions=True)
        if self.spider:
            await self.spider.close()
        if self.redis:
            await self.redis.close()
        logger.info("👋 异步引擎已平滑停止")

    async def process_task(self, task_data: Dict[str, Any]):
        """处理单个任务：注入上下文并执行"""
        task_id = task_data.get("taskId")
        trace_id = (task_data.get("traceId") or secrets.token_urlsafe(9)).lower()
        user_id = task_data.get("userId")
        
        # 1. 注入核心上下文
        set_context(trace_id=trace_id, task_id=task_id, user_id=user_id)
        
        logger.info(f"📥 [New Task] Name: {task_data.get('name')} | TaskID: {task_id} | TraceID: {trace_id}")
        
        # 2. 更新任务状态为“运行中”
        await self._update_task_status(task_id, "RUNNING")
        
        # 3. 启动心跳更新
        heartbeat_task = asyncio.create_task(self._update_heartbeat(task_id))
        
        try:
            # 4. 执行异步爬虫逻辑
            await self.spider.scan_task(task_data)
            
            # 5. 任务反馈 (由 spider 内部 callback 完成)
            # await self._update_task_status(task_id, "COMPLETED")
            
        except asyncio.CancelledError:
            logger.warning(f"🛑 [Task Cancelled] TaskID: {task_id}")
            # await self._update_task_status(task_id, "CANCELLED")
        except Exception as e:
            logger.exception(f"❌ [Task Failed] TaskID: {task_id} | Error: {e}")
            # await self._update_task_status(task_id, "FAILED", error_msg=str(e))
        finally:
            heartbeat_task.cancel()
            try:
                await heartbeat_task
            except asyncio.CancelledError:
                pass

    async def _update_task_status(self, task_id: int, status: str, error_msg: str = None):
        """反馈机制：[已废弃] 状态更新已统一由 spider 内部通过 HTTP Callback 完成"""
        return
        # if not task_id: return
        # ... (rest of commented code)

    async def _update_heartbeat(self, task_id: int):
        """定期更新任务心跳 (用于故障自愈)"""
        try:
            while True:
                await self.redis.hset("niro:task:heartbeat", str(task_id), int(time.time() * 1000))
                await asyncio.sleep(20) # 缩短心跳间隔
        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.error(f"💓 [Heartbeat Error] TaskID: {task_id} | {e}")

    async def start(self):
        await self.init_redis()
        logger.info(f"👂 正在监听任务队列: {QUEUES}")
        
        while self.running:
            try:
                # 阻塞式弹出任务，超时时间 5 秒
                result = await self.redis.blpop(QUEUES, timeout=5)
                if not result:
                    continue
                
                queue_name, data_json = result
                logger.debug(f"📡 [Queue Popped] Queue: {queue_name} | RawData: {data_json[:200]}...")
                task_data = json.loads(data_json)
                
                # 如果是双重编码，再解一次
                if isinstance(task_data, str):
                    try:
                        task_data = json.loads(task_data)
                    except:
                        pass
                
                if not isinstance(task_data, dict):
                    logger.error(f"❌ 任务格式错误: {task_data}")
                    continue
                
                # 创建异步任务处理，不阻塞主循环
                task = asyncio.create_task(self.process_task(task_data))
                self._tasks.add(task)
                task.add_done_callback(self._tasks.discard)
                
            except Exception as e:
                    logger.error(f"⚠️ 队列监听异常: {e}")
                    await asyncio.sleep(1)
