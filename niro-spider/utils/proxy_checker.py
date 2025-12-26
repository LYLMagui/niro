import requests
import sys
import os

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.insert(0, parent_dir)

from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

def validate_proxy(proxy_ip):
    """
    验证单个代理IP是否有效
    :param proxy_ip: 代理IP字符串 (如 "1.2.3.4:8080")
    :return: True/False
    """
    proxies = {
        "http": f"http://{proxy_ip}",
        "https": f"http://{proxy_ip}"
    }
    
    try:
        # 访问一个稳定的IP检测服务
        # httpbin.org 是一个开源的 HTTP 测试服务
        # ip.sb, ifconfig.me 也可以
        test_url = "http://httpbin.org/ip" 
        
        response = requests.get(test_url, proxies=proxies, timeout=5)
        
        if response.status_code == 200:
            result = response.json()
            origin_ip = result.get("origin", "")
            
            # 如果返回的 IP 包含我们的代理 IP，说明代理生效了
            # 注意：httpbin 返回的 origin 可能是 "代理IP, 真实IP" 的格式
            if proxy_ip.split(":")[0] in origin_ip:
                logger.info(f"✅ 代理有效: {proxy_ip} (返回IP: {origin_ip})")
                return True
            else:
                logger.warning(f"⚠️ 代理响应但IP未隐藏: {proxy_ip} (返回IP: {origin_ip})")
                return True # 虽然没隐藏，但至少能通
        else:
            logger.warning(f"❌ 代理返回非200状态: {proxy_ip} (Status: {response.status_code})")
            return False
            
    except Exception as e:
        logger.warning(f"❌ 代理连接失败: {proxy_ip} - {e}")
        return False

def check_all_proxies():
    """
    检测配置中所有代理的连通性
    """
    if not hasattr(settings, 'PROXIES') or not settings.PROXIES:
        logger.warning("配置中没有代理列表")
        return

    logger.info(f"开始检测 {len(settings.PROXIES)} 个代理...")
    valid_count = 0
    
    for proxy in settings.PROXIES:
        if validate_proxy(proxy):
            valid_count += 1
            
    logger.info(f"检测完成，可用代理: {valid_count}/{len(settings.PROXIES)}")

if __name__ == "__main__":
    # 单独运行此脚本进行测试
    # 记得先在 main.py 或这里初始化日志
    from utils.logger import setup_logging
    setup_logging()
    check_all_proxies()
