import os
import sys
import json
import pendulum
from loguru import logger

# 定义日志目录和文件
LOG_DIR = "logs"
LOG_FILE = os.path.join(LOG_DIR, "niro_spider.log")
# 为 ELK 准备的结构化 JSON 日志文件
LOG_FILE_JSON = os.path.join(LOG_DIR, "niro_spider.json")

def shanghai_time(*args):
    """
    返回上海时区的当前时间 (GMT+8)
    """
    return pendulum.now('Asia/Shanghai').to_datetime_string()

def setup_logging(log_dir=LOG_DIR, log_level="INFO"):
    """
    配置全局日志 (使用 Loguru)，支持普通文本和结构化 JSON 两种输出
    """
    global logger
    # 确保日志目录存在
    if not os.path.exists(log_dir):
        os.makedirs(log_dir)

    # 移除默认的 handler
    logger.remove()

    # 1. 控制台输出 (开发友好，带颜色文本)
    logger.add(
        sys.stdout,
        level=log_level,
        format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>",
    )
    # 更新控制台输出使用上海时间 (通过 patch 实现更优雅)
    # 注意：Loguru 的 format 中的 {time} 默认使用 local time
    # 为了强制使用上海时间且不改变 format 字符串，我们可以使用 patch

    # 2. 文件输出 (普通文本，适合人工快速查阅)
    logger.add(
        LOG_FILE,
        rotation="10 MB",
        retention="5 days",
        compression="zip",
        level=log_level,
        encoding="utf-8",
        enqueue=True,  # 异步写入，线程安全
        format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name}:{function}:{line} - {message}",
    )

    # 3. 结构化 JSON 输出 (专为 ELK/Filebeat 设计)
    # 每一行都是一个完整的 JSON 对象，包含所有 context 信息
    logger.add(
        LOG_FILE_JSON,
        rotation="50 MB",  # JSON 日志通常较大，给更大的空间
        retention="7 days",
        compression="zip",
        level=log_level,
        encoding="utf-8",
        enqueue=True,
        serialize=True,    # 关键属性：将日志记录序列化为 JSON 字符串
    )

    # 使用 patch 强制所有输出使用上海时间
    logger = logger.patch(lambda record: record.update(time=pendulum.now('Asia/Shanghai')))

    # 绑定全局基础信息，方便 ELK 检索
    logger.configure(extra={"service": "niro-spider", "env": os.getenv("APP_ENV", "dev")})

    logger.info(f"🚀 Loguru 日志系统初始化完成 (时区: Asia/Shanghai)")
    logger.info(f"📝 文本日志: {os.path.abspath(LOG_FILE)}")
    logger.info(f"📊 JSON日志 (ELK 预备): {os.path.abspath(LOG_FILE_JSON)}")

def get_logger(name):
    """
    获取带名称标识的 logger
    """
    return logger.bind(name=name)
