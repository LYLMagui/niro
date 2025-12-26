import random
from config import settings

def get_random_proxy():
    """
    从配置的代理池中随机获取一个代理
    :return: 代理字典 { 'http': '...', 'https': '...' } 或 None
    """
    if not hasattr(settings, 'PROXIES') or not settings.PROXIES:
        return None
    
    proxy_ip = random.choice(settings.PROXIES)
    return {
        "http": f"http://{proxy_ip}",
        "https": f"http://{proxy_ip}"
    }
