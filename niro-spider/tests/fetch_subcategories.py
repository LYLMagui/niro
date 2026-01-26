import os
import sys
import time
import random
import requests
import json
from loguru import logger

# Add project root to sys.path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import settings

# ==========================================
# 配置区域
# ==========================================
# 优先从环境变量获取 Cookie，如果没有则使用默认值（请确保 Cookie 有效）
COOKIE = os.getenv("BUFF_COOKIE", "")
if not COOKIE:
    # 这里可以填入默认的测试 Cookie
    COOKIE = "Device-Id=0Bls5gj6EVfpOkJ4UWNs; Locale-Supported=zh-Hans; game=csgo; qr_code_verify_ticket=c15tJfSa3a08689b310f3be85ed2d09366c8; remember_me=U1090466660|hhY6kmxZrrU3tStOp91xZfiM0BBQ3JSq; session=1-wVdFdrwCYZLJIjrppXv5falkO8fKpdco5V2i1JYqfiEO2016521276; csrf_token=ImRkOGZmMjkzNDgzOGMwYzVlYjBjNjFhODg5MDViYmE4ZTQwY2FkOGUi.aW-H_g.yg27neDEJxn97cpkdfX6isNelbA" 

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://buff.163.com/market/",
}

def get_subcategories_by_group(category_group="rifle", max_pages=20):
    """
    抓取指定一级分类下的所有二级分类
    :param category_group: 一级分类标识，如 rifle, pistol, knife, gloves 等
    :param max_pages: 最大抓取页数
    """
    url = "https://buff.163.com/api/market/goods"
    
    # 存储发现的二级分类: {internal_name: {name: "显示名称", count: 0}}
    found_categories = {}
    
    logger.info(f"🚀 开始抓取一级分类 [{category_group}] 下的数据，最大页数: {max_pages}")
    
    headers = HEADERS.copy()
    headers["Cookie"] = COOKIE
    
    for page in range(1, max_pages + 1):
        logger.info(f"📄 正在抓取第 {page}/{max_pages} 页...")
        
        params = {
            "game": "csgo",
            "page_num": page,
            "category_group": category_group,
            "use_suggestion": 0,
            "_": int(time.time() * 1000)
        }
        
        try:
            response = requests.get(url, params=params, headers=headers, timeout=15)
            response.raise_for_status()
            
            # 检查登录状态
            if "Login Required" in response.text:
                logger.error("🚨 Cookie 已失效，请更新 Cookie！")
                return
            
            data = response.json()
            if data.get("code") != "OK":
                logger.warning(f"⚠️ API 返回错误: {data.get('msg')}")
                break

            # 在第一页时打印总数信息
            if page == 1:
                total_page = data.get("data", {}).get("total_page", 0)
                total_count = data.get("data", {}).get("total_count", 0)
                logger.info(f"📊 [概览] 总页数: {total_page} | 总商品数: {total_count}")
                
            items = data.get("data", {}).get("items", [])
            if not items:
                logger.info("⚠️ 当前页无数据，停止抓取。")
                break
            
            # 解析数据
            for item in items:
                goods_info = item.get("goods_info", {})
                info = goods_info.get("info", {})
                tags = info.get("tags", {})
                
                # 尝试提取二级分类
                # 优先取 weapon (武器类型)，其次取 type (其他类型)，再次取 category (通用分类)
                category_tag = tags.get("weapon") or tags.get("type") or tags.get("category")
                
                if category_tag:
                    internal_name = category_tag.get("internal_name")
                    localized_name = category_tag.get("localized_name")
                    
                    if internal_name:
                        if internal_name not in found_categories:
                            found_categories[internal_name] = {
                                "name": localized_name,
                                "count": 0,
                                "sample_goods": item.get("name") # 记录一个样例商品名
                            }
                        found_categories[internal_name]["count"] += 1
            
            # 随机延迟，避免封控
            if page < max_pages:
                delay = random.uniform(settings.CRAWL_INTERVAL_MIN, settings.CRAWL_INTERVAL_MAX)
                logger.info(f"⏳ 等待 {delay:.2f} 秒...")
                time.sleep(delay)
                
        except Exception as e:
            logger.error(f"❌ 请求失败: {e}")
            break

    # 打印结果
    logger.info("\n" + "="*50)
    logger.info(f"📊 抓取完成！在 [{category_group}] 下共发现 {len(found_categories)} 个二级分类：")
    
    # 按发现数量排序
    sorted_cats = sorted(found_categories.items(), key=lambda x: x[1]['count'], reverse=True)
    
    for internal_name, info in sorted_cats:
        print(f" - [{info['name']}] ({internal_name}) | 出现次数: {info['count']} | 样例: {info['sample_goods']}")
        
    logger.info("="*50)

if __name__ == "__main__":
    # 配置参数 (直接修改此处)
    CATEGORY_GROUP = "other"  # 一级分类: rifle, pistol, knife, gloves, etc.
    MAX_PAGES = 20           # 抓取页数
    
    get_subcategories_by_group(CATEGORY_GROUP, MAX_PAGES)
