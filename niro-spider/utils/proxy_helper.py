import random
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

def get_proxies():
    """
    获取代理配置
    根据 ENABLE_PROXY 开关决定是否启用代理
    优先使用 PROXY_URL (v2rayA 等单点代理)
    其次使用 PROXIES (代理池列表)
    :return: 代理字典 { 'http': '...', 'https': '...' } 或 None
    """
    # 0. 检查代理开关
    enable_proxy = getattr(settings, 'ENABLE_PROXY', False)
    if not enable_proxy:
        return None

    # 1. 优先使用全局单点代理
    proxy_url = getattr(settings, 'PROXY_URL', None)
    if proxy_url:
        proxies = {
            "http": proxy_url,
            "https": proxy_url
        }
        # logger.debug(f"🌐 使用全局代理: {proxy_url}")
        return proxies

    # 2. 备选使用代理池
    if hasattr(settings, 'PROXIES') and settings.PROXIES:
        proxy_ip = random.choice(settings.PROXIES)
        proxies = {
            "http": f"http://{proxy_ip}",
            "https": f"http://{proxy_ip}"
        }
        # logger.debug(f"🌐 使用代理池: {proxy_ip}")
        return proxies
    
    return None

def get_random_proxy():
    """兼容旧函数名"""
    return get_proxies()
