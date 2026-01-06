from sqlalchemy import Column, BigInteger, String, Text, DateTime, Numeric, Integer, SmallInteger
from sqlalchemy.sql import func
from .database import Base
import datetime

class BuffGoodsCategory(Base):
    __tablename__ = 'buff_goods_categories'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    name = Column(String(255), nullable=False)
    parent_id = Column(BigInteger, nullable=False)
    internal_name = Column(String(255), nullable=False)
    full_internal_name = Column(String(255), nullable=False)
    create_time = Column(DateTime, server_default=func.now())
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now())

class BuffGoods(Base):
    __tablename__ = 'buff_goods'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    goods_id = Column(BigInteger, unique=True, nullable=False)
    name = Column(String(255), nullable=False)
    short_name = Column(String(255), nullable=False, default="")
    internal_name = Column(String(255), nullable=False, default="")
    category_id = Column(BigInteger, nullable=False, default=0)
    rarity = Column(String(100), nullable=False, default="")
    exterior = Column(String(100), nullable=False, default="")
    market_hash_name = Column(String(255), nullable=False, default="")
    icon_url = Column(Text, nullable=False, default="")
    original_icon_url = Column(Text, nullable=False, default="")
    tags = Column(Text)  # 存储 JSON 字符串
    create_time = Column(DateTime, nullable=False, server_default=func.now())
    update_time = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())

class BuffPriceHistory(Base):
    __tablename__ = 'buff_price_history'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    goods_id = Column(BigInteger, nullable=False)
    price = Column(Numeric(10, 2))
    buy_max_price = Column(Numeric(10, 2))
    sell_num = Column(Integer)
    record_time = Column(DateTime, server_default=func.now())
    create_time = Column(DateTime, server_default=func.now())

class BuffScanTask(Base):
    __tablename__ = 'buff_scan_task'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    name = Column(String(255))
    user_id = Column(BigInteger, nullable=False)
    goods_id = Column(BigInteger)
    max_price = Column(Numeric(10, 2))
    min_paintwear = Column(Numeric(10, 8))
    max_paintwear = Column(Numeric(10, 8))
    buy_count = Column(Integer, default=0)
    success_count = Column(Integer, default=0)
    status = Column(Integer, default=0)
    create_time = Column(DateTime, server_default=func.now())
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now())
    cron_expression = Column(String(100))
    duration_minutes = Column(Integer)
    scan_interval = Column(Integer)
    task_type = Column(Integer)
    min_profit = Column(Numeric(10, 2))

class User(Base):
    __tablename__ = 'sys_user'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    username = Column(String(100), nullable=False)
    password = Column(String(100), nullable=False)
    nickname = Column(String(100))
    email = Column(String(100))
    avatar = Column(String(255))
    status = Column(SmallInteger)
    create_time = Column(DateTime, server_default=func.now())
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now())
    is_delete = Column(SmallInteger, default=0)

class UserBuffSettings(Base):
    __tablename__ = 'user_buff_settings'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger, nullable=False)
    buff_cookie = Column(Text)
    payment_method = Column(String(100))
    wecom_corpid = Column(String(255))
    wecom_corpsecret = Column(String(255))
    wecom_agentid = Column(String(255))
    wecom_touser = Column(String(255))
    create_time = Column(DateTime, server_default=func.now())
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now())
