from sqlalchemy import Column, BigInteger, String, Text, DateTime, Numeric, Integer, SmallInteger
from sqlalchemy.sql import func
from .database import Base
import datetime

class BuffGoodsCategory(Base):
    """BUFF商品分类模型"""
    __tablename__ = 'buff_goods_categories'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    name = Column(String(255), nullable=False, comment='分类名称')
    parent_id = Column(BigInteger, nullable=False, comment='父分类ID')
    internal_name = Column(String(255), nullable=False, comment='内部标识名称')
    full_internal_name = Column(String(255), nullable=False, comment='完整路径标识名称')
    create_time = Column(DateTime, server_default=func.now(), comment='创建时间')
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now(), comment='修改时间')

class BuffSticker(Base):
    """BUFF印花元数据及价值模型"""
    __tablename__ = 'buff_stickers'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    sticker_id = Column(BigInteger, unique=True, nullable=False, default=0, comment='BUFF平台印花唯一标识ID')
    name = Column(String(255), nullable=False, default="", comment='印花中文名称')
    image_url = Column(Text, nullable=False, default="", comment='印花图片预览链接')
    price = Column(Numeric(10, 2), nullable=False, default=0.00, comment='印花本体市场底价')
    sell_num = Column(Integer, nullable=False, default=0, comment='在售数量')
    create_time = Column(DateTime, nullable=False, server_default=func.now(), comment='创建时间')
    update_time = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now(), comment='最后更新时间')

class BuffGoodsStats(Base):
    """饰品市场行情统计雷达模型"""
    __tablename__ = 'buff_goods_stats'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    goods_id = Column(BigInteger, unique=True, nullable=False, default=0, comment='BUFF商品唯一标识ID')
    avg_price_7d = Column(Numeric(10, 2), nullable=False, default=0.00, comment='过去7天成交均价')
    avg_price_24h = Column(Numeric(10, 2), nullable=False, default=0.00, comment='过去24小时成交均价')
    buy_max_price = Column(Numeric(10, 2), nullable=False, default=0.00, comment='当前最高求购价格')
    sell_num = Column(Integer, nullable=False, default=0, comment='当前在售数量')
    liquidity_score = Column(Integer, nullable=False, default=0, comment='流动性评分(0-100)')
    update_time = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now(), comment='最后统计时间')

class BuffLeakAlert(Base):
    """捡漏预警触发日志模型"""
    __tablename__ = 'buff_leak_alerts'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    task_id = Column(BigInteger, nullable=False, default=0, comment='触发该预警的扫描任务ID')
    goods_id = Column(BigInteger, nullable=False, default=0, comment='商品ID')
    sell_id = Column(String(100), nullable=False, default="", comment='BUFF平台该笔上架单的唯一标识')
    price = Column(Numeric(10, 2), nullable=False, default=0.00, comment='触发预警时的挂牌价格')
    expected_profit = Column(Numeric(10, 2), nullable=False, default=0.00, comment='预估理论利润额')
    reason = Column(Text, nullable=False, default="", comment='触发逻辑简述')
    is_bought = Column(SmallInteger, nullable=False, default=0, comment='是否已购买(0:未购, 1:已购)')
    create_time = Column(DateTime, nullable=False, server_default=func.now(), comment='触发预警的时间')

class BuffGoods(Base):
    """BUFF商品模型"""
    __tablename__ = 'buff_goods'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    goods_id = Column(BigInteger, unique=True, nullable=False, comment='BUFF商品唯一标识ID')
    name = Column(String(255), nullable=False, comment='商品名称')
    short_name = Column(String(255), nullable=False, default="", comment='商品简称')
    internal_name = Column(String(255), nullable=False, default="", comment='内部标识名称')
    category_id = Column(BigInteger, nullable=False, default=0, comment='分类ID')
    rarity = Column(String(100), nullable=False, default="", comment='稀有度')
    exterior = Column(String(100), nullable=False, default="", comment='外观/磨损程度')
    market_hash_name = Column(String(255), nullable=False, default="", comment='Steam市场Hash名称')
    icon_url = Column(Text, nullable=False, default="", comment='图标URL')
    original_icon_url = Column(Text, nullable=False, default="", comment='原始图标URL')
    tags = Column(Text, comment='标签JSON数据')
    create_time = Column(DateTime, nullable=False, server_default=func.now(), comment='创建时间')
    update_time = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now(), comment='修改时间')

class BuffPriceHistory(Base):
    """价格历史记录模型"""
    __tablename__ = 'buff_price_history'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    goods_id = Column(BigInteger, nullable=False, comment='商品ID')
    price = Column(Numeric(10, 2), comment='价格')
    buy_max_price = Column(Numeric(10, 2), comment='最高求购价')
    sell_num = Column(Integer, comment='销售数量')
    record_time = Column(DateTime, server_default=func.now(), comment='记录时间')
    create_time = Column(DateTime, server_default=func.now(), comment='创建时间')

class BuffScanTask(Base):
    """扫描任务模型"""
    __tablename__ = 'buff_scan_task'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    name = Column(String(255), comment='任务名称')
    user_id = Column(BigInteger, nullable=False, comment='所属用户ID')
    goods_id = Column(BigInteger, comment='商品ID')
    max_price = Column(Numeric(10, 2), comment='最高接受价格')
    min_paintwear = Column(Numeric(10, 8), comment='最小磨损值')
    max_paintwear = Column(Numeric(10, 8), comment='最大磨损值')
    buy_count = Column(Integer, default=0, comment='计划购买数量')
    success_count = Column(Integer, default=0, comment='已成功购买数量')
    status = Column(Integer, default=0, comment='任务状态')
    create_time = Column(DateTime, server_default=func.now(), comment='创建时间')
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now(), comment='更新时间')
    cron_expression = Column(String(100), comment='Cron表达式')
    duration_minutes = Column(Integer, comment='持续时间(分钟)')
    scan_interval = Column(Integer, comment='扫描间隔(秒)')
    task_type = Column(Integer, comment='任务类型')
    min_profit = Column(Numeric(10, 2), comment='最小期望利润')

class User(Base):
    """系统用户模型"""
    __tablename__ = 'sys_user'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    username = Column(String(100), nullable=False, comment='用户名')
    password = Column(String(100), nullable=False, comment='密码')
    nickname = Column(String(100), comment='昵称')
    email = Column(String(100), comment='邮箱')
    avatar = Column(String(255), comment='头像')
    status = Column(SmallInteger, comment='用户状态')
    create_time = Column(DateTime, server_default=func.now(), comment='创建时间')
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now(), comment='修改时间')
    is_delete = Column(SmallInteger, default=0, comment='是否删除(0:否, 1:是)')

class UserBuffSettings(Base):
    """用户BUFF配置模型"""
    __tablename__ = 'user_buff_settings'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True, comment='主键ID')
    user_id = Column(BigInteger, nullable=False, comment='所属用户ID')
    buff_cookie = Column(Text, comment='BUFF Cookie内容')
    payment_method = Column(String(100), comment='支付方式')
    wecom_corpid = Column(String(255), comment='企微企业ID')
    wecom_corpsecret = Column(String(255), comment='企微应用密钥')
    wecom_agentid = Column(String(255), comment='企微代理ID')
    wecom_touser = Column(String(255), comment='企微通知接收者')
    create_time = Column(DateTime, server_default=func.now(), comment='创建时间')
    update_time = Column(DateTime, server_default=func.now(), onupdate=func.now(), comment='修改时间')
