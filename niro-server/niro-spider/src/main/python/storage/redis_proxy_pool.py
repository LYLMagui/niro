import random
import redis
import sys
import os
import requests

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.insert(0, parent_dir)

from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

class RedisProxyPool:
    def __init__(self):
        """
        初始化 Redis 连接
        1. 读取 settings 中的 Redis 配置 (HOST, PORT, DB, PASSWORD)
        2. 创建 redis.Redis 连接池
        3. 测试连接是否成功 (ping)
        """
        self.FREE_PROXIES_HOST = "https://proxy.scdn.io/"
        # 创建连接池
        try:
            self.redis_client = redis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                password=settings.REDIS_PASSWORD,
                db=settings.REDIS_DB,
                socket_timeout=5
            )
            # 测试连接
            self.redis_client.ping()
            logger.info(f"Redis 连接成功: {settings.REDIS_HOST}:{settings.REDIS_PORT}")
        except Exception as e:
            logger.error(f"Redis 连接失败: {e}")
            self.redis_client = None

    def add_proxy(self, proxy_ip, score=100):
        """
        添加代理到 Redis ZSet (有序集合)
        :param proxy_ip: 代理IP字符串 (如 "1.2.3.4:8080")
        :param score: 初始分数 (默认100)
        Redis 命令参考: ZADD proxies {score} {proxy_ip}
        """
        if not self.redis_client:
            return
        
        try:
            self.redis_client.zadd("proxies",{proxy_ip:score})
            logger.debug(f"代理已添加/更新: {proxy_ip}")
        except Exception as e:
            logger.error(f"添加代理到 Redis 失败: {e}")
            return
        
    def get_proxy(self):
        """
        获取一个高质量代理
        1. 使用 ZREVRANGE 获取分数最高的一个代理
        2. 如果没有代理，返回 None
        Redis 命令参考: ZREVRANGE proxies 0 0
        """
        if not self.redis_client:
            return None

        try:
            # zrevrange: 按分数从大到小排序
            # 取前5名 (0, 4)
            proxies = self.redis_client.zrevrange("proxies",0,4)

            if proxies:
                return random.choice(proxies)
            return None
        except Exception as e:
            logger.error(f"获取代理失败: {e}")
            return None

    def decrease_score(self, proxy_ip):
        """
        代理不可用时，扣分
        1. 使用 ZINCRBY 扣除分数 (例如 -10)
        2. 如果分数低于阈值 (例如 0)，则从池中移除
        Redis 命令参考: ZINCRBY, ZREM
        """
        if not self.redis_client:
            return
        
        try:
            # zincrby: 增加分数（负数即扣分）
            score = self.redis_client.zincrby("proxies",-10,proxy_ip)

            # 如果分数太低（比如低于0），直接移除
            if score <= 0:
                logger.info(f"代理分数过低，移除: {proxy_ip}")
                self.redis_client.zrem("proxies",proxy_ip)
        except Exception as e:
            logger.error(f"扣分失败: {e}")
 
    
    def get_count(self):
        """
        获取代理池当前数量
        Redis 命令参考: ZCARD
        """
        if not self.redis_client:
            return 0

        try:
            return self.redis_client.zcard("proxies")
        except Exception as e:
            logger.error(f"获取代理池数量失败: {e}")
            return 0
    
    def get_free_proxies(self):
        """
        获取免费代理ip
        """
        url = "/api/get_proxy.php"
        params = {
            "protocol":"https",
            "count":20
        }    
        try:
            response = requests.get(
            self.FREE_PROXIES_HOST + url,
            params=params,
            timeout=10
            )
            response.raise_for_status()
            data = response.json()
            if data or data['code'] == 200:
                logger.info(f"获取代理ip成功: {data}")
            proxies = data['data'].get("proxies",[])
            
        except Exception as e:
            logger.error(f"获取代理ip失败: {e}")


    

if __name__ == "__main__":
    from utils.logger import setup_logging
    
    # 1. 初始化日志（非常重要！否则看不到 INFO 日志）
    setup_logging()
    
    pool = RedisProxyPool()
    pool.get_free_proxies()
    