import redis
import redis.asyncio as aioredis
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

class RedisPool:
    """
    Redis 连接池单例管理
    支持同步和异步模式
    """
    _instance = None
    _redis_client = None
    _async_redis_client = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(RedisPool, cls).__new__(cls)
            cls._instance._init_pool()
        return cls._instance

    def _init_pool(self):
        """初始化连接池"""
        try:
            # 同步客户端
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
            logger.info(f"✅ Redis 同步连接池初始化成功: {settings.REDIS_HOST}:{settings.REDIS_PORT} (DB:{settings.REDIS_DB})")
            
            # 异步客户端
            self._async_redis_client = aioredis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                password=settings.REDIS_PASSWORD,
                db=settings.REDIS_DB,
                socket_timeout=5,
                decode_responses=True
            )
            logger.info(f"✅ Redis 异步连接池初始化成功")
            
        except Exception as e:
            logger.error(f"❌ Redis 连接池初始化失败: {e}")
            self._redis_client = None
            self._async_redis_client = None

    def get_client(self):
        """获取同步 Redis 客户端"""
        return self._redis_client

    def get_async_client(self):
        """获取异步 Redis 客户端"""
        return self._async_redis_client

# 全局共享单例
redis_pool = RedisPool()
# 同步客户端 (兼容旧代码)
redis_client = redis_pool.get_client()
# 异步客户端 (v2.4.0)
redis_async = redis_pool.get_async_client()
