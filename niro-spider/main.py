import argparse
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


async def run_dual_services(args):
    """同时运行任务消费者和 OCR 服务"""
    from spiders.ocr_service import OCRService

    consumer = TaskConsumer()
    ocr_service = OCRService()

    # 初始化两个服务
    await consumer.init_redis()
    await ocr_service.init_resources()

    logger.info("==================================================")
    logger.info("🚀 Niro Spider v2.5.0 (Dual Mode) Starting...")
    logger.info("   Mode: Task Consumer + OCR Service")
    logger.info("   OCR Queue: niro:tasks:ocr")
    logger.info("==================================================")

    # 创建并发任务
    consumer_task = asyncio.create_task(consumer.start())
    ocr_task = asyncio.create_task(ocr_service.start())

    try:
        # 等待任意一个服务结束
        done, pending = await asyncio.wait(
            [consumer_task, ocr_task],
            return_when=asyncio.FIRST_COMPLETED
        )
        # 取消另一个服务
        for task in pending:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
    except KeyboardInterrupt:
        logger.info("🛑 用户通过键盘停止程序")
    except Exception as e:
        logger.critical(f"❌ 程序发生致命错误: {e}", exc_info=True)
    finally:
        await consumer.stop()
        await ocr_service.close()
        logger.info("🏁 Niro Spider 已退出")


async def run_consumer_only():
    """只运行任务消费者"""
    from config import settings
    from utils.network_util import get_current_ip

    current_ip = get_current_ip()
    proxy_status = "Enabled" if settings.ENABLE_PROXY else "Disabled"
    proxy_url = settings.PROXY_URL if settings.ENABLE_PROXY else "N/A"

    logger.info("==================================================")
    logger.info("🚀 Niro Spider v2.5.0 (Consumer Only) Starting...")
    logger.info("   Mode: Async / Redis-Blocked / Sharded")
    logger.info(f"   Network: Proxy {proxy_status} ({proxy_url})")
    logger.info(f"   Exit IP: {current_ip}")
    logger.info("==================================================")

    consumer = TaskConsumer()
    try:
        await consumer.start()
    except KeyboardInterrupt:
        logger.info("🛑 用户通过键盘停止程序")
    except Exception as e:
        logger.critical(f"❌ 程序发生致命错误: {e}", exc_info=True)
    finally:
        await consumer.stop()
        logger.info("🏁 Niro Spider 已退出")


async def run_ocr_only():
    """只运行 OCR 服务"""
    from spiders.ocr_service import OCRService

    logger.info("==================================================")
    logger.info("🚀 Niro Spider v2.5.0 (OCR Service Only) Starting...")
    logger.info("   Mode: OCR Image Recognition")
    logger.info("   Images Dir: images/")
    logger.info("   Queue: niro:tasks:ocr")
    logger.info("==================================================")

    ocr_service = OCRService()
    try:
        await ocr_service.start()
    except KeyboardInterrupt:
        logger.info("🛑 用户通过键盘停止程序")
    except Exception as e:
        logger.critical(f"❌ OCR 服务发生致命错误: {e}", exc_info=True)
    finally:
        await ocr_service.close()
        logger.info("🏁 OCR Service 已退出")


def main():
    """
    Niro Spider v2.5.0 异步消息驱动架构启动入口
    支持多种运行模式：
    - 默认: 任务消费者 + OCR 服务双运行
    - --consumer-only: 只运行任务消费者
    - --ocr-only: 只运行 OCR 服务
    """
    parser = argparse.ArgumentParser(description="Niro Spider")
    parser.add_argument(
        "--mode",
        choices=["dual", "consumer", "ocr"],
        default="dual",
        help="运行模式: dual(默认) | consumer(仅任务消费者) | ocr(仅OCR服务)"
    )
    args = parser.parse_args()

    setup_logging()

    if args.mode == "consumer":
        asyncio.run(run_consumer_only())
    elif args.mode == "ocr":
        asyncio.run(run_ocr_only())
    else:
        asyncio.run(run_dual_services(args))


if __name__ == "__main__":
    main()
