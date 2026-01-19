import asyncio
import os
import sys
import signal
from loguru import logger

# 获取当前脚本所在目录并加入搜索路径
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

from utils.logger import setup_logging
from engine.task_consumer import TaskConsumer

async def shutdown(consumer: TaskConsumer):
    """优雅关闭异步引擎"""
    logger.info("🛑 接收到停止信号，正在关闭引擎...")
    await consumer.stop()
    tasks = [t for t in asyncio.all_tasks() if t is not asyncio.current_task()]
    [t.cancel() for t in tasks]
    await asyncio.gather(*tasks, return_exceptions=True)
    asyncio.get_event_loop().stop()

def main():
    """
    Niro Spider v2.4.0 异步消息驱动架构启动入口
    废除旧的 DB 轮询模式，采用 Redis 阻塞监听 (BLPOP) 模式
    """
    setup_logging()
    
    logger.info("==================================================")
    logger.info("🚀 Niro Spider v2.4.0 (Message-Driven) Starting...")
    logger.info("   Mode: Async / Redis-Blocked / Sharded")
    logger.info("==================================================")
    
    consumer = TaskConsumer()
    loop = asyncio.get_event_loop()

    # 注册信号处理 (兼容 Linux/Unix)
    if os.name != 'nt':
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, lambda: asyncio.create_task(shutdown(consumer)))
    
    try:
        loop.run_until_complete(consumer.start())
    except KeyboardInterrupt:
        # Windows 下通常捕获此异常
        logger.info("🛑 用户通过键盘停止程序")
        loop.run_until_complete(consumer.stop())
    except Exception as e:
        logger.critical(f"❌ 程序发生致命错误: {e}", exc_info=True)
    finally:
        loop.close()
        logger.info("🏁 Niro Spider 已退出")

if __name__ == "__main__":
    main()
