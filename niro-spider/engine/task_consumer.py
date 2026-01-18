import asyncio
import json
import uuid
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

# 队列优先级定义 (从高到低)
QUEUES = [
    "niro:task:queue:system",
    "niro:task:queue:sniping",
    "niro:task:queue:flipping",
    "niro:task:queue:default"
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
        logger.info(f"🚀 已连接 Redis: {settings.REDIS_HOST}:{settings.REDIS_PORT}")

    async def stop(self):
        self.running = False
        if self._tasks:
            logger.info(f"正在等待 {len(self._tasks)} 个任务完成...")
            await asyncio.gather(*self._tasks, return_exceptions=True)
        if self.spider:
            await self.spider.close()
        if self.redis:
            await self.redis.close()
        logger.info("👋 消费者已停止")

    async def process_task(self, task_data: Dict[str, Any]):
        """处理单个任务"""
        trace_id = str(uuid.uuid4()).replace("-", "")
        task_id = task_data.get("taskId")
        
        # 为当前协程设置上下文
        # 注意：这里只设置了任务级上下文，具体的账号上下文在执行子任务时再动态切换
        set_context(trace_id=trace_id, task_id=task_id)
        
        logger.info(f"📥 收到新任务: [{task_data.get('name')}] (ID: {task_id}, 类型: {task_data.get('taskType')})")
        
        # 启动心跳更新任务
        heartbeat_task = asyncio.create_task(self._update_heartbeat(task_id))
        
        try:
            # 执行异步扫描任务
            await self.spider.scan_task(task_data)
        except Exception as e:
            logger.exception(f"❌ 任务处理失败: {task_id}, 错误: {e}")
        finally:
            # 任务结束，停止心跳
            heartbeat_task.cancel()
            try:
                await heartbeat_task
            except asyncio.CancelledError:
                pass

    async def _update_heartbeat(self, task_id: int):
        """定期更新任务心跳"""
        try:
            while True:
                # 使用与 Java 端一致的 Hash Key: niro:task:heartbeat
                await self.redis.hset("niro:task:heartbeat", str(task_id), int(time.time() * 1000))
                await asyncio.sleep(30)  # 每 30 秒更新一次
        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.error(f"💓 心跳更新失败: {task_id}, 错误: {e}")

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

async def run_engine():
    setup_logging()
    consumer = TaskConsumer()
    
    # 注册信号处理 (Windows 下 asyncio 不支持 add_signal_handler)
    if os.name != 'nt':
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, lambda: asyncio.create_task(consumer.stop()))
    
    try:
        await consumer.start()
    except (asyncio.CancelledError, KeyboardInterrupt):
        await consumer.stop()

if __name__ == "__main__":
    asyncio.run(run_engine())
