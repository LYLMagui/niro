import requests
import functools
from utils.logger import get_logger

logger = get_logger(__name__)

@functools.lru_cache(maxsize=1)
def get_local_ip():
    """获取本机出口IP (带缓存)"""
    try:
        # 使用 HTTPS 确保与业务请求协议一致
        resp = requests.get("https://api64.ipify.org?format=json", timeout=5)
        return resp.json().get("ip", "Unknown")
    except:
        return "Unknown"

def get_current_ip(proxies=None):
    """
    获取当前请求使用的IP
    :param proxies: 代理配置
    :return: IP地址
    """
    try:
        # 1. 优先使用 HTTPS 检测源，确保能命中 Clash 的加密流量规则
        # 2. 备用源以提高可靠性
        detect_urls = [
            "https://api64.ipify.org?format=json",
            "https://httpbin.org/ip"
        ]
        
        for url in detect_urls:
            try:
                resp = requests.get(url, proxies=proxies, timeout=5)
                # ipify 返回 {'ip': '...'}, httpbin 返回 {'origin': '...'}
                data = resp.json()
                ip = data.get("ip") or data.get("origin")
                if ip:
                    return ip
            except:
                continue
                
        return "Unknown"
    except Exception as e:
        logger.debug(f"获取当前IP失败: {e}")
        return "Unknown"

def log_request_ip(proxies=None, prefix=""):
    """打印当前请求的IP日志"""
    ip = get_current_ip(proxies)
    return ip
