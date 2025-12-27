import logging
import os
import sys
from logging.handlers import RotatingFileHandler

# 定义日志格式
LOG_FORMAT = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"

def setup_logging(log_dir="logs", log_level=logging.INFO):
    """
    配置全局日志
    :param log_dir: 日志文件存储目录
    :param log_level: 日志级别
    """
    # 确保日志目录存在
    if not os.path.exists(log_dir):
        os.makedirs(log_dir)

    # 获取根日志记录器
    logger = logging.getLogger()
    logger.setLevel(log_level)
    
    # 清除已有的 handler，防止重复打印
    if logger.handlers:
        logger.handlers.clear()

    # 1. 控制台 Handler (输出到屏幕)
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(log_level)
    console_handler.setFormatter(logging.Formatter(LOG_FORMAT, datefmt=DATE_FORMAT))
    logger.addHandler(console_handler)

    # 2. 文件 Handler (输出到文件，按大小轮转)
    # maxBytes=10MB, backupCount=5 (保留5个备份文件)
    log_file_path = os.path.join(log_dir, "niro_spider.log")
    file_handler = RotatingFileHandler(
        log_file_path, maxBytes=10*1024*1024, backupCount=5, encoding="utf-8"
    )
    file_handler.setLevel(log_level)
    file_handler.setFormatter(logging.Formatter(LOG_FORMAT, datefmt=DATE_FORMAT))
    logger.addHandler(file_handler)

    # 抑制第三方库的详细日志 (如 apscheduler)
    logging.getLogger("apscheduler").setLevel(logging.WARNING)

    logging.info(f"日志系统初始化完成，日志文件路径: {os.path.abspath(log_file_path)}")

def get_logger(name):
    """
    获取指定名称的 logger
    """
    return logging.getLogger(name)
