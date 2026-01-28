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
    
    from config import settings
    from utils.network_util import get_current_ip
    
    # 获取网络信息
    current_ip = get_current_ip()
    proxy_status = "Enabled" if settings.ENABLE_PROXY else "Disabled"
    proxy_url = settings.PROXY_URL if settings.ENABLE_PROXY else "N/A"

    logger.info("==================================================")
    logger.info("🚀 Niro Spider v2.4.0 (Message-Driven) Starting...")
    logger.info("   Mode: Async / Redis-Blocked / Sharded")
    logger.info(f"   Network: Proxy {proxy_status} ({proxy_url})")
    logger.info(f"   Exit IP: {current_ip}")
    logger.info("==================================================")
    
    consumer = TaskConsumer()
    
    try:
        asyncio.run(consumer.start())
    except KeyboardInterrupt:
        # Windows 下通常捕获此异常
        logger.info("🛑 用户通过键盘停止程序")
    except Exception as e:
        logger.critical(f"❌ 程序发生致命错误: {e}", exc_info=True)
    finally:
        logger.info("🏁 Niro Spider 已退出")

if __name__ == "__main__":
    main()
