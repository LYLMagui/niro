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

def run_crawl(category_internal: str, sort_by: str = None, profile: Any = None):
    """执行一次抓取并返回去重后的商品 ID 集合和商品名称映射"""
    logger.info(f"🚀 开始抓取 [sort_by={sort_by or 'default'}]...")
    unique_ids = set()
    goods_names = {}
    
    total_pages = 1
    current_page = 1
    
    while current_page <= total_pages:
        params = {
            "game": "csgo",
            "page_num": current_page,
            "tab": "selling",
            "category": category_internal
        }
        if sort_by:
            params["sort_by"] = sort_by
            
        try:
            data = fetch_buff_goods_api(params, profile=profile)
            if current_page == 1:
                total_pages = data.total_page
                
            items = data.items or []
            for item in items:
                unique_ids.add(item.id)
                goods_names[item.id] = item.name
            
            current_page += 1
            if current_page <= total_pages:
                time.sleep(random.uniform(1.5, 3))
        except Exception as e:
            logger.error(f"❌ 抓取失败: {e}")
            break
            
    return unique_ids, goods_names

def test_category_goods_count_consistency(category_internal: str = "weapon_sport_gloves"):
    """对比不同排序下的结果差异"""
    try:
        logger.info(f"🧪 开始对比分类 [{category_internal}] 在不同排序下的差异...")
        profile = BrowserHelper.create_profile(None)
        
        # 1. 默认排序抓取
        default_ids, default_names = run_crawl(category_internal, sort_by=None, profile=profile)
        logger.info(f"✅ 默认排序抓取完成，去重总数: {len(default_ids)}")
        
        # 2. 价格升序抓取
        price_ids, price_names = run_crawl(category_internal, sort_by="price.asc", profile=profile)
        logger.info(f"✅ 价格升序抓取完成，去重总数: {len(price_ids)}")
        
        # 3. 找出差异
        missing_in_default = price_ids - default_ids
        extra_in_default = default_ids - price_ids
        
        if missing_in_default:
            logger.warning(f"🚨 默认排序遗漏了以下 {len(missing_in_default)} 个商品:")
            for gid in missing_in_default:
                logger.warning(f"   - [{price_names[gid]}] (ID: {gid})")
        else:
            logger.info("✨ 默认排序没有遗漏价格升序中的商品")
            
        if extra_in_default:
            logger.info(f"ℹ️ 默认排序多出了以下 {len(extra_in_default)} 个商品 (可能在价格升序的分页外):")
            for gid in extra_in_default:
                logger.info(f"   - [{default_names[gid]}] (ID: {gid})")

    except Exception as e:
        logger.error(f"❌ 测试过程中发生错误: {e}")

if __name__ == "__main__":
    # 测试运动手套 (weapon_sport_gloves)
    test_category_goods_count_consistency("weapon_sport_gloves")
