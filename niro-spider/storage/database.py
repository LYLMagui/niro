from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, scoped_session
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from config import settings

# 构建同步数据库连接 URL
DATABASE_URL = f"postgresql://{settings.DB_USERNAME}:{settings.DB_PASSWORD}@{settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}"

# 构建异步数据库连接 URL (使用 asyncpg)
ASYNC_DATABASE_URL = f"postgresql+asyncpg://{settings.DB_USERNAME}:{settings.DB_PASSWORD}@{settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}"

# 创建同步数据库引擎
engine = create_engine(
    DATABASE_URL,
    pool_size=settings.DB_MAX_CONN,
    max_overflow=2,
    pool_pre_ping=True,
    pool_recycle=3600
)

# 创建异步数据库引擎
async_engine = create_async_engine(
    ASYNC_DATABASE_URL,
    pool_size=settings.DB_MAX_CONN,
    max_overflow=2,
    pool_pre_ping=True,
    pool_recycle=3600
)

# 创建声明式基类
Base = declarative_base()

# 创建同步会话工厂
session_factory = sessionmaker(bind=engine)

# 创建线程安全的同步会话包装器
Session = scoped_session(session_factory)

# 创建异步会话工厂
async_session_factory = async_sessionmaker(
    bind=async_engine,
    class_=AsyncSession,
    expire_on_commit=False
)

def get_session():
    """获取一个新的同步数据库会话"""
    return Session()

async def get_async_session():
    """获取一个新的异步数据库会话"""
    async with async_session_factory() as session:
        yield session
