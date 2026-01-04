import random
import sys
import os
import requests

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.insert(0, parent_dir)

from storage.redis_pool import redis_pool
from utils.logger import get_logger

logger = get_logger(__name__)

class RedisStorage:
    """
    通用的 Redis 数据存储/业务逻辑类
    原名 RedisProxyPool，现重构为通用存储管理
    """
    def __init__(self):
        self.FREE_PROXIES_HOST = "https://proxy.scdn.io/"
        self.redis = redis_pool

    def add_proxy(self, proxy_ip, score=100):
        """添加代理到 Redis"""
        try:
            self.redis.zadd("proxies", {proxy_ip: score})
            logger.debug(f"代理已添加/更新: {proxy_ip}")
        except Exception as e:
            logger.error(f"添加代理到 Redis 失败: {e}")
        
    def get_proxy(self):
        """获取一个高质量代理"""
        try:
            proxies = self.redis.zrevrange("proxies", 0, 4)
            if proxies:
                return random.choice(proxies)
            return None
        except Exception as e:
            logger.error(f"获取代理失败: {e}")
            return None

    def decrease_score(self, proxy_ip):
        """代理不可用时扣分"""
        try:
            score = self.redis.zincrby("proxies", -10, proxy_ip)
            if score <= 0:
                logger.info(f"代理分数过低，移除: {proxy_ip}")
                self.redis.zrem("proxies", proxy_ip)
        except Exception as e:
            logger.error(f"扣分失败: {e}")

    def get_count(self):
        """获取代理池当前数量"""
        try:
            return self.redis.zcard("proxies")
        except Exception as e:
            logger.error(f"获取代理池数量失败: {e}")
            return 0

    def get_free_proxies(self):
        """获取免费代理ip (逻辑保留，当前可能未使用)"""
        url = "/api/get_proxy.php"
        params = {
            "protocol": "https",
            "count": 20
        }    
        try:
            response = requests.get(
                self.FREE_PROXIES_HOST + url,
                params=params,
                timeout=10
            )
            response.raise_for_status()
            data = response.json()
            if data and data.get('code') == 200:
                logger.info(f"获取代理ip成功")
                return data['data'].get("proxies", [])
            return []
        except Exception as e:
            logger.error(f"获取代理ip失败: {e}")
            return []

if __name__ == "__main__":
    from utils.logger import setup_logging
    setup_logging()
    
    storage = RedisStorage()
    # 示例调用
    storage.get_free_proxies()
