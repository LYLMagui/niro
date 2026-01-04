import redis
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

class RedisPool:
    """
    Redis 连接池单例管理
    效仿 postgres_pool 的设计模式
    """
    _instance = None
    _redis_client = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(RedisPool, cls).__new__(cls)
            cls._instance._init_pool()
        return cls._instance

    def _init_pool(self):
        """初始化连接池"""
        try:
            # redis.Redis 默认就是使用 ConnectionPool 的
            self._redis_client = redis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                password=settings.REDIS_PASSWORD,
                db=settings.REDIS_DB,
                socket_timeout=5,
                decode_responses=True
            )
            # 测试连接
            self._redis_client.ping()
            logger.info(f"✅ Redis 连接池初始化成功: {settings.REDIS_HOST}:{settings.REDIS_PORT} (DB:{settings.REDIS_DB})")
        except Exception as e:
            logger.error(f"❌ Redis 连接池初始化失败: {e}")
            self._redis_client = None

    def get_client(self):
        """获取 Redis 客户端实例"""
        return self._redis_client

    def zadd(self, key, mapping):
        """有序集合添加"""
        if self._redis_client:
            return self._redis_client.zadd(key, mapping)

    def zrevrange(self, key, start, stop):
        """有序集合倒序获取"""
        if self._redis_client:
            return self._redis_client.zrevrange(key, start, stop)

    def zincrby(self, key, amount, value):
        """有序集合增量"""
        if self._redis_client:
            return self._redis_client.zincrby(key, amount, value)

    def zrem(self, key, *values):
        """有序集合移除"""
        if self._redis_client:
            return self._redis_client.zrem(key, *values)

    def zcard(self, key):
        """有序集合成员数"""
        if self._redis_client:
            return self._redis_client.zcard(key)

# 全局共享单例
redis_pool = RedisPool()
# 直接暴露 client 方便使用，兼容旧代码
redis_client = redis_pool.get_client()
