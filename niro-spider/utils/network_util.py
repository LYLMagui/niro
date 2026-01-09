import requests
import functools

# 注意：此处不导入 logger 避免循环引用

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
    # 如果未指定代理，尝试从配置中自动获取全局代理 (使用局部导入避免循环引用)
    if proxies is None:
        try:
            from config import settings
            enable_proxy = getattr(settings, 'ENABLE_PROXY', False)
            proxy_url = getattr(settings, 'PROXY_URL', None)
            if enable_proxy and proxy_url:
                proxies = {
                    "http": proxy_url,
                    "https": proxy_url
                }
        except:
            pass

    try:
        # 1. 优先使用 HTTPS 检测源，确保能命中 Clash 的加密流量规则
        detect_urls = [
            "https://api64.ipify.org?format=json",
            "https://httpbin.org/ip"
        ]
        
        for url in detect_urls:
            try:
                resp = requests.get(url, proxies=proxies, timeout=5)
                data = resp.json()
                # 处理 httpbin.org 返回多个 IP 的情况 (逗号分隔)
                origin = data.get("ip") or data.get("origin")
                if origin:
                    # 取第一个 IP
                    ip = origin.split(',')[0].strip()
                    return ip
            except:
                continue
                
        return "Unknown"
    except Exception:
        return "Unknown"

def log_request_ip(proxies=None, prefix=""):
    """获取并返回当前请求的IP"""
    return get_current_ip(proxies)
