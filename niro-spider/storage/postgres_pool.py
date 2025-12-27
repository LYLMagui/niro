import sys
import os

# 将项目根目录添加到 sys.path，解决 ModuleNotFoundError
# 假设当前文件位于 src/main/python/storage/postgres_pool.py
# 需要将 src/main/python 添加到 sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

import psycopg2
from psycopg2 import pool
from psycopg2.extras import RealDictCursor
from contextlib import contextmanager
from config import settings
from utils.logger import get_logger

logger = get_logger(__name__)

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.insert(0, parent_dir)

class PostgresPool:
    """
    PostgreSQL 连接池封装
    使用单例模式确保全局只有一个连接池实例
    """
    _instance = None
    _pool = None

    def __new__(cls, *args, **kwargs):
        if not cls._instance:
            cls._instance = super(PostgresPool, cls).__new__(cls, *args, **kwargs)
        return cls._instance

    def __init__(self):
        """
        初始化连接池
        注意：由于 __new__ 单例机制，__init__ 可能会被调用多次
        需要确保连接池只初始化一次
        """
        if self._pool is not None:
            return

        try:
            self._pool = psycopg2.pool.ThreadedConnectionPool(
                minconn=settings.DB_MIN_CONN,
                maxconn=settings.DB_MAX_CONN,
                host=settings.DB_HOST,
                port=settings.DB_PORT,
                database=settings.DB_NAME,
                user=settings.DB_USERNAME,
                password=settings.DB_PASSWORD,
                # 设置搜索路径，方便直接使用表名
                options=f"-c search_path={settings.DB_SCHEMA}"
            )
            logger.info(f"PostgreSQL 连接池初始化成功 (Host: {settings.DB_HOST}, DB: {settings.DB_NAME})")
        except Exception as e:
            logger.error(f"PostgreSQL 连接池初始化失败: {e}")
            raise e

    @contextmanager
    def get_conn(self):
        """
        获取数据库连接的上下文管理器
        
        Usage:
            with pg_pool.get_conn() as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT 1")
        """
        conn = None
        try:
            conn = self._pool.getconn()
            yield conn
        except Exception as e:
            logger.error(f"获取数据库连接异常: {e}")
            raise e
        finally:
            if conn:
                self._pool.putconn(conn)

    @contextmanager
    def get_cursor(self, cursor_factory=RealDictCursor):
        """
        直接获取游标的上下文管理器 (自动处理连接获取和归还)
        
        Usage:
            with pg_pool.get_cursor() as cur:
                cur.execute("SELECT * FROM users")
                rows = cur.fetchall()
        
        :param cursor_factory: 游标工厂类，默认为 RealDictCursor (返回字典格式数据)
        """
        conn = None
        try:
            conn = self._pool.getconn()
            # 自动提交事务，如果需要手动事务控制，请使用 get_conn()
            conn.autocommit = False 
            with conn.cursor(cursor_factory=cursor_factory) as cur:
                yield cur
                conn.commit()
        except Exception as e:
            if conn:
                conn.rollback()
            logger.error(f"数据库操作异常: {e}")
            raise e
        finally:
            if conn:
                self._pool.putconn(conn)

    def close_pool(self):
        """关闭连接池中的所有连接"""
        if self._pool:
            self._pool.closeall()
            logger.info("PostgreSQL 连接池已关闭")
            self._pool = None

    def execute(self, sql, params=None):
        """执行 SQL (INSERT/UPDATE/DELETE)"""
        with self.get_cursor() as cur:
            cur.execute(sql, params)

    def fetch_one(self, sql, params=None):
        """查询单行数据"""
        with self.get_cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchone()

    def fetch_all(self, sql, params=None):
        """查询多行数据"""
        with self.get_cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchall()

# 全局单例实例
pg_pool = PostgresPool()