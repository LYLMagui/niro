import sys
import os

# 获取当前脚本所在目录 (niro-spider/src/main/python)
current_dir = os.path.dirname(os.path.abspath(__file__))

# 将 src/main/python 目录添加到 sys.path
sys.path.insert(0, current_dir)

from spiders.task_scanner import TaskScanner
from utils.logger import setup_logging, get_logger
from config import settings

# 初始化日志配置
setup_logging(log_dir=settings.LOG_DIR)
logger = get_logger(__name__)

def main():
    logger.info("🔧 初始化环境...")
    
    # 简单的环境检查
    if not settings.BUFF_COOKIE:
        logger.warning("未配置 BUFF_COOKIE，可能无法获取数据！")

    # 启动扫描器
    scanner = TaskScanner()
    try:
        scanner.run()
    except KeyboardInterrupt:
        logger.info("🛑 用户手动停止扫描器")
    except Exception as e:
        logger.critical(f"❌ 扫描器发生致命错误: {e}", exc_info=True)

if __name__ == "__main__":
    main()
