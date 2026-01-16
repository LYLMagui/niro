import os
import sys
import json
import pendulum
from loguru import logger
from contextvars import ContextVar

# 定义全链路追踪 ContextVar (类似 Java MDC)
trace_id_var = ContextVar("trace_id", default="")

# 定义日志目录和文件
LOG_DIR = os.getenv("LOG_DIR", "logs")
LOG_FILE = os.path.join(LOG_DIR, "niro_spider.log")
# 为 ELK 准备的结构化 JSON 日志文件
LOG_FILE_JSON = os.path.join(LOG_DIR, "niro_spider.json")

def shanghai_time(*args):
    """
    返回上海时区的当前时间 (GMT+8)
    """
    return pendulum.now('Asia/Shanghai').to_datetime_string()

def get_current_ip_cached(force_refresh=False):
    """
    获取当前IP地址 (带缓存)
    :param force_refresh: 是否强制刷新缓存
    """
    if not hasattr(get_current_ip_cached, '_cached_ip') or force_refresh:
        from utils.network_util import get_current_ip
        # 此时 get_current_ip 会尝试自动获取全局代理
        get_current_ip_cached._cached_ip = get_current_ip()
    return get_current_ip_cached._cached_ip

def setup_logging(log_dir=LOG_DIR, log_level="INFO"):
    """
    配置全局日志 (使用 Loguru)，支持普通文本和结构化 JSON 两种输出
    """
    global logger
    
    # 强制进程级别时区设置为上海 (双重保障)
    os.environ['TZ'] = 'Asia/Shanghai'
    import time
    if hasattr(time, 'tzset'):
        time.tzset()

    # 确保日志目录存在
    if not os.path.exists(log_dir):
        os.makedirs(log_dir)

    # 移除默认的 handler
    logger.remove()

    # 关键：先配置 patcher，每次记录日志时动态获取IP和TraceId
    # 这样所有后续添加的 handler 都会自动应用这个 patch
    logger.configure(
        patcher=lambda record: record.update(
            time=pendulum.now('Asia/Shanghai'),
            extra={
                **record["extra"],
                "service": "niro-spider", 
                "env": os.getenv("APP_ENV", "dev"),
                "ip": get_current_ip_cached(),
                "traceId": trace_id_var.get() or os.getenv("TRACE_ID", "")
            }
        ),
        extra={
            "service": "niro-spider", 
            "env": os.getenv("APP_ENV", "dev"), 
            "ip": get_current_ip_cached(),
            "traceId": ""
        }
    )

    # 1. 控制台输出 (开发友好，带颜色文本)
    logger.add(
        sys.stdout,
        level=log_level,
        format="<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - traceId: {extra[traceId]} | ip：{extra[ip]} | <level>{message}</level>",
    )

    # 2. 文件输出 (普通文本，适合人工快速查阅)
    logger.add(
        LOG_FILE,
        rotation="10 MB",
        retention="5 days",
        compression="zip",
        level=log_level,
        encoding="utf-8",
        enqueue=True,  # 异步写入，线程安全
        format="{time:YYYY-MM-DD HH:mm:ss.SSS} | {level: <8} | {name}:{function}:{line} - traceId: {extra[traceId]} | ip：{extra[ip]} | {message}",
    )

    # 3. 结构化 JSON 输出 (专为 ELK/Filebeat 设计)
    logger.add(
        LOG_FILE_JSON,
        rotation="50 MB",
        retention="7 days",
        compression="zip",
        level=log_level,
        encoding="utf-8",
        enqueue=True,
        serialize=True,    # 关键属性：将日志记录序列化为 JSON 字符串
    )

    logger.info(f"🚀 Loguru 日志系统初始化完成 (强制时区: Asia/Shanghai, 系统时间: {pendulum.now().to_datetime_string()})")
    logger.info(f"📝 文本日志: {os.path.abspath(LOG_FILE)}")
    logger.info(f"📊 JSON日志 (ELK 预备): {os.path.abspath(LOG_FILE_JSON)}")

def get_logger(name=None):
    """
    获取一个命名的 logger 实例
    """
    if name:
        return logger.bind(name=name)
    return logger
