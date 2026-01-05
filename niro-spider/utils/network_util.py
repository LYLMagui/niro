import requests
import functools
from utils.logger import get_logger

logger = get_logger(__name__)

@functools.lru_cache(maxsize=1)
def get_local_ip():
    """获取本机出口IP (带缓存)"""
    try:
        resp = requests.get("http://httpbin.org/ip", timeout=5)
        return resp.json().get("origin", "Unknown")
    except:
        return "Unknown"

def get_current_ip(proxies=None):
    """
    获取当前请求使用的IP
    :param proxies: 代理配置
    :return: IP地址
    """
    try:
        # 每次都实时获取最新的出口IP，以确保准确性
        # 如果追求性能，可以增加短时间的缓存
        resp = requests.get("http://httpbin.org/ip", proxies=proxies, timeout=5)
        return resp.json().get("origin", "Unknown")
    except Exception as e:
        logger.debug(f"获取当前IP失败: {e}")
        return "Unknown"

def log_request_ip(proxies=None, prefix=""):
    """打印当前请求的IP日志"""
    ip = get_current_ip(proxies)
    logger.info(f"🌐 {prefix}当前发起请求的IP: {ip}")
    return ip
