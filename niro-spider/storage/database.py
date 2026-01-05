from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, scoped_session
from config import settings

# 构建数据库连接 URL
# 格式: postgresql://username:password@host:port/database
DATABASE_URL = f"postgresql://{settings.DB_USERNAME}:{settings.DB_PASSWORD}@{settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}"

# 创建数据库引擎
# echo=False: 生产环境关闭 SQL 语句打印，开发环境可设为 True
engine = create_engine(
    DATABASE_URL,
    pool_size=settings.DB_MAX_CONN,
    max_overflow=2,
    pool_pre_ping=True,
    pool_recycle=3600
)

# 创建声明式基类
Base = declarative_base()

# 创建会话工厂
session_factory = sessionmaker(bind=engine)

# 创建线程安全的会话包装器
Session = scoped_session(session_factory)

def get_session():
    """获取一个新的数据库会话"""
    return Session()
