import os
import sys
import time
import random
import requests
import psycopg2
from loguru import logger

# Add project root to sys.path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import settings

# ==========================================
# 配置区域
# ==========================================
# 请在此处填入您的 Buff Cookie
# 或者设置环境变量 BUFF_COOKIE
COOKIE = os.getenv("BUFF_COOKIE", "")

# 如果环境变量没设置，您可以在这里手动填入
if not COOKIE:
    COOKIE = "Device-Id=0Bls5gj6EVfpOkJ4UWNs; Locale-Supported=zh-Hans; game=csgo; qr_code_verify_ticket=c15tJfSa3a08689b310f3be85ed2d09366c8; remember_me=U1090466660|hhY6kmxZrrU3tStOp91xZfiM0BBQ3JSq; session=1-wVdFdrwCYZLJIjrppXv5falkO8fKpdco5V2i1JYqfiEO2016521276; csrf_token=ImRkOGZmMjkzNDgzOGMwYzVlYjBjNjFhODg5MDViYmE4ZTQwY2FkOGUi.aW-H_g.yg27neDEJxn97cpkdfX6isNelbA" 

# ==========================================

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://buff.163.com/market/",
}

def get_db_connection():
    """获取数据库连接"""
    return psycopg2.connect(
        host=settings.DB_HOST,
        port=settings.DB_PORT,
        dbname=settings.DB_NAME,
        user=settings.DB_USERNAME,
        password=settings.DB_PASSWORD
    )

def get_secondary_categories(conn):
    """获取所有二级分类"""
    with conn.cursor() as cur:
        # 获取 parent_id != 0 的分类，视为二级分类
        # 也可以更严谨地查: parent_id IN (SELECT id FROM buff_goods_categories WHERE parent_id = 0)
        query = """
            SELECT name, internal_name 
            FROM buff_goods_categories 
            WHERE parent_id != 0
        """
        cur.execute(query)
        return cur.fetchall()

def check_category_validity(category_name, internal_name):
    """
    检查分类是否有效
    原理: 请求第一页，如果 total_page > 1000，说明返回了全量数据，该分类参数无效
    """
    url = "https://buff.163.com/api/market/goods"
    
    # 构造请求头
    headers = HEADERS.copy()
    headers["Cookie"] = COOKIE
    
    # 构造参数
    # 注意: Buff API 对不同类型的分类可能需要不同的参数处理
    # 大部分二级分类使用 category 参数
    params = {
        "game": "csgo",
        "page_num": 1,
        "category": internal_name,
        "use_suggestion": 0,
        "_": int(time.time() * 1000)
    }
    
    try:
        response = requests.get(url, params=params, headers=headers, timeout=15)
        response.raise_for_status()
        
        # 检查是否需要登录
        if "Login Required" in response.text:
            logger.error("🚨 Cookie 已失效，请更新 Cookie！")
            sys.exit(1)
            
        data = response.json()
        
        if data.get("code") != "OK":
            logger.warning(f"⚠️ [{category_name}] API 返回错误: {data.get('msg')}")
            return False, 0
            
        # 获取总页数
        data_body = data.get("data", {})
        total_page = data_body.get("total_page", 0)
        total_count = data_body.get("total_count", 0)
        
        return True, total_page, total_count
        
    except Exception as e:
        logger.error(f"❌ [{category_name}] 请求失败: {e}")
        return False, 0, 0

def main():
    if not COOKIE:
        logger.error("❌ 未设置 Cookie！请编辑脚本填入 Cookie 或设置 BUFF_COOKIE 环境变量。")
        return

    logger.info("🔌 连接数据库...")
    try:
        conn = get_db_connection()
    except Exception as e:
        logger.error(f"无法连接数据库: {e}")
        return

    try:
        categories = get_secondary_categories(conn)
        logger.info(f"📋 从数据库获取到 {len(categories)} 个二级分类")
    finally:
        conn.close()

    if not categories:
        logger.warning("⚠️ 没有找到任何二级分类，请检查数据库。")
        return

    invalid_categories = []
    valid_count = 0
    
    logger.info(f"🚀 开始检测，请求间隔: {settings.CRAWL_INTERVAL_MIN} - {settings.CRAWL_INTERVAL_MAX} 秒")
    
    for i, (name, internal_name) in enumerate(categories, 1):
        logger.info(f"[{i}/{len(categories)}] 正在检查: {name} ({internal_name}) ...")
        
        success, total_page, total_count = check_category_validity(name, internal_name)
        
        if success:
            logger.info(f"   ✅ 结果: {total_page} 页, 共 {total_count} 个商品")
            
            # 判定标准: 如果页数超过 1000 (Buff通常最大显示页数有限，或者全量数据非常大)
            # 或者 total_count 特别巨大 (比如几十万，而单个分类通常几千几万)
            if total_page >= 1000:
                logger.error(f"   🚨 判定为无效分类! (页数过大，疑似返回全量数据)")
                invalid_categories.append({
                    "name": name, 
                    "internal_name": internal_name, 
                    "pages": total_page,
                    "count": total_count
                })
            else:
                valid_count += 1
        
        # 随机等待
        if i < len(categories):
            delay = random.uniform(settings.CRAWL_INTERVAL_MIN, settings.CRAWL_INTERVAL_MAX)
            logger.info(f"   ⏳ 等待 {delay:.2f} 秒...")
            time.sleep(delay)

    logger.info("\n" + "="*50)
    logger.info("📊 检测完成！结果报告：")
    logger.info(f"✅ 有效分类: {valid_count} 个")
    logger.info(f"❌ 无效分类: {len(invalid_categories)} 个")
    
    if invalid_categories:
        print("\n🚨 以下分类接口返回了全部商品 (无效分类):")
        for item in invalid_categories:
            print(f" - {item['name']} (internal_name: {item['internal_name']}) | 页数: {item['pages']} | 商品数: {item['count']}")
            
    logger.info("="*50)

if __name__ == "__main__":
    main()
