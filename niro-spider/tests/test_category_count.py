import sys
import os
import json
import time
import random
from typing import Dict, Any, List

# 将项目根目录添加到 python 路径
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if project_root not in sys.path:
    sys.path.append(project_root)

from spiders.get_buff_goods_category import fetch_buff_goods_api
from utils.logger import logger
from utils.browser_helper import BrowserHelper

def test_category_goods_count_consistency(category_internal: str = "weapon_sport_gloves"):
    """
    测试指定二级分类下的商品数量一致性
    逻辑：
    1. 请求第一页，获取接口返回的 total_count
    2. 遍历所有页数，累加实际返回的商品数量
    3. 对比 实际累加值 与 接口返回的 total_count 是否一致
    """
    logger.info(f"🧪 开始测试分类 [{category_internal}] 的商品数量一致性...")
    
    profile = BrowserHelper.create_profile(None)
    actual_total = 0
    total_count_from_api = 0
    total_pages = 1
    current_page = 1
    
    try:
        while current_page <= total_pages:
            params = {
                "game": "csgo",
                "page_num": current_page,
                "tab": "selling",
                "category": category_internal
            }
            
            logger.info(f"  -> 正在抓取第 {current_page}/{total_pages} 页...")
            data = fetch_buff_goods_api(params, profile=profile)
            
            if current_page == 1:
                total_pages = data.total_page
                total_count_from_api = data.total_count
                logger.info(f"📊 接口报告总页数: {total_pages}, 总商品数: {total_count_from_api}")

            items = data.items or []
            page_item_count = len(items)
            
            # 累加实际商品数量
            actual_total += page_item_count
            
            logger.info(f"  ✅ 第 {current_page} 页返回商品数: {page_item_count}")
            
            current_page += 1
            if current_page <= total_pages:
                # 适当延迟避免风控
                time.sleep(random.uniform(2, 4))

        logger.info(f"📈 实际累计商品总数: {actual_total}")
        logger.info(f"📉 接口报告总商品数: {total_count_from_api}")
        
        if actual_total == total_count_from_api:
            logger.info("✨ 验证通过：实际抓取总数与接口返回总数完全一致！")
        else:
            logger.error(f"❌ 验证失败：数量不一致！差异值: {actual_total - total_count_from_api}")

    except Exception as e:
        logger.error(f"❌ 测试过程中发生错误: {e}")

if __name__ == "__main__":
    # 测试运动手套 (weapon_sport_gloves)
    test_category_goods_count_consistency("weapon_sport_gloves")
