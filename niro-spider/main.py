import sys
import os

# 获取当前脚本所在目录 (niro-spider/src/main/python)
current_dir = os.path.dirname(os.path.abspath(__file__))

# 将 src/main/python 目录添加到 sys.path
# 这样可以直接 import config, spiders 等模块
sys.path.insert(0, current_dir)

from spiders.buff_spider import BuffSpider
from utils.logger import setup_logging, get_logger
from utils.proxy_checker import validate_proxy
from config import settings

# 初始化日志配置
setup_logging()
logger = get_logger(__name__)

def filter_proxies():
    """
    启动时过滤代理池，移除无效代理
    """
    if not hasattr(settings, 'PROXIES') or not settings.PROXIES:
        return

    logger.info(f"正在清洗代理池 (共 {len(settings.PROXIES)} 个)...")
    valid_proxies = []
    
    # 遍历检测
    for proxy in settings.PROXIES:
        if validate_proxy(proxy):
            valid_proxies.append(proxy)
    
    # 更新内存中的配置 (不会修改文件)
    # 如果可用代理太少，发出警告
    settings.PROXIES = valid_proxies
    
    if not valid_proxies:
        logger.error("❌ 警告：没有可用的代理IP！爬虫将无法工作或直接使用本机IP。")
    else:
        logger.info(f"✅ 代理池清洗完成，剩余可用: {len(valid_proxies)} 个")

def main():
    # 1. 启动前清洗代理池
    filter_proxies()
    
    # 如果没有可用代理，可以选择直接退出
    if not settings.PROXIES:
        logger.warning("由于没有可用代理，停止运行 (如需直连请注释此判断)")
        # return 
    
    logger.info("🚀 启动 Buff 爬虫...")
    spider = BuffSpider()
    
    # 这里只是 MVP0 的测试
    test_goods_id = 1116002 
    items = spider.get_goods_list(test_goods_id)
    
    logger.info(f"✅ 爬取完成，共获取 {len(items)} 条数据")
    for item in items[:3]: 
        logger.info(f"- [{item['name']}] 价格: {item['price_buff']}, 磨损: {item['paintwear']}")

if __name__ == "__main__":
    main()
