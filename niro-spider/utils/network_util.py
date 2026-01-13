import requests
import functools
import time
import random
import numpy as np
from typing import Optional

# 注意：此处不导入 logger 避免循环引用

def smart_sleep(mu: float = 2.0, sigma: float = 0.5, min_wait: float = 1.0):
    """
    基于正态分布的智能延迟 (高斯分布)
    模拟人类行为：延迟时间集中在均值附近，极少数情况出现长/短延迟。
    
    :param mu: 均值 (期望等待时间)
    :param sigma: 标准差 (波动程度)
    :param min_wait: 最小等待时间
    """
    wait_time = np.random.normal(mu, sigma)
    wait_time = max(wait_time, min_wait)
    time.sleep(wait_time)

def coffee_break(page_count: int, interval: int = 100, min_minutes: int = 5, max_minutes: int = 10):
    """
    长时休眠 (喝咖啡模式)
    
    :param page_count: 当前已采集的页数
    :param interval: 每隔多少页触发一次
    :param min_minutes: 最小休眠分钟数
    :param max_minutes: 最大休眠分钟数
    """
    if page_count > 0 and page_count % interval == 0:
        sleep_minutes = random.randint(min_minutes, max_minutes)
        # 此处如果需要打印日志，建议由调用方根据返回值处理，或者使用回调
        return sleep_minutes
    return 0

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
                # 兼容不带协议头的 proxy_url
                _proxy_url = proxy_url if "://" in proxy_url else f"http://{proxy_url}"
                proxies = {
                    "http": _proxy_url,
                    "https": _proxy_url
                }
                # logger.debug(f"🔍 检测 IP 使用代理: {proxies}")
        except Exception as e:
            # logger.error(f"❌ 获取代理配置失败: {e}")
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
