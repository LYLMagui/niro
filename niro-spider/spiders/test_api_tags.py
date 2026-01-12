import os
import sys
import json
import requests

# 修复模块导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from config import settings
from utils.logger import setup_logging, get_logger
from utils.browser_helper import BrowserHelper
from utils.proxy_helper import get_proxies

logger = get_logger(__name__)

def test_buff_goods_tags():
    """
    测试 BUFF 商品列表 API，分析其返回的 tags 结构
    """
    setup_logging()
    
    url = "https://buff.163.com/api/market/goods"
    params = {
        "game": "csgo",
        "page_num": 1,
        "tab": "selling"
    }
    
    # 获取 Profile 和 Headers
    profile = BrowserHelper.create_profile(cookie=settings.BUFF_COOKIE)
    headers = profile.get_headers()
    
    proxies = get_proxies()
    
    logger.info(f"🚀 正在请求 BUFF API: {url}")
    try:
        # 记录原始请求信息
        logger.info("--- 原始请求详情 ---")
        logger.info(f"URL: {url}")
        logger.info(f"Method: GET")
        logger.info(f"Params: {json.dumps(params, indent=4)}")
        # 隐藏敏感信息后的 Headers
        safe_headers = {k: (v if k.lower() != 'cookie' else f"{v[:20]}...{v[-20:]}") for k, v in headers.items()}
        logger.info(f"Headers: {json.dumps(safe_headers, indent=4, ensure_ascii=False)}")
        if proxies:
            logger.info(f"Proxies: {proxies}")
        logger.info("-------------------")

        response = requests.get(url, headers=headers, params=params, proxies=proxies, timeout=15)
        response.encoding = 'utf-8'
        
        if response.status_code != 200:
            logger.error(f"❌ 请求失败，状态码: {response.status_code}")
            return

        data = response.json()
        if data.get("code") != "OK":
            logger.error(f"❌ 业务错误: {data.get('msg')}")
            return
            
        items = data.get("data", {}).get("items", [])
        if not items:
            logger.warning("⚠️ 未获取到商品数据")
            return
            
        logger.info(f"✅ 成功获取 {len(items)} 条商品数据")
        
        # 提取并分析第一个商品的 tags
        first_item = items[0]
        goods_info = first_item.get("goods_info", {})
        info = goods_info.get("info", {})
        tags = info.get("tags", {})
        
        logger.info("📊 第一个商品的 Tags 结构分析:")
        print(json.dumps(tags, indent=4, ensure_ascii=False))
        
        # 遍历所有 key，看看有哪些潜在的分类信息
        all_keys = set()
        for item in items:
            t = item.get("goods_info", {}).get("info", {}).get("tags", {})
            all_keys.update(t.keys())
            
        logger.info(f"🔍 本页所有商品中出现的 Tags Key: {all_keys}")

    except Exception as e:
        logger.error(f"❌ 测试过程中发生异常: {e}")

if __name__ == "__main__":
    test_buff_goods_tags()
