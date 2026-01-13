import sys
import os
import json
from typing import Dict, Any

# 将项目根目录添加到 python 路径
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if project_root not in sys.path:
    sys.path.append(project_root)

from spiders.get_buff_goods_category import fetch_buff_goods_api, normalize_match_name
from utils.logger import logger
from utils.browser_helper import BrowserHelper

def test_category_logic(cat_group: str = "other"):
    """
    专门测试分类组的解析逻辑
    """
    logger.info(f"🧪 开始测试 [{cat_group}] 分类解析逻辑...")
    
    # 1. 模拟环境
    type_internal = cat_group 
    profile = BrowserHelper.create_profile(None)
    
    params = {
        "game": "csgo",
        "page_num": "1",
        "tab": "selling",
        "category_group": type_internal
    }

    try:
        # 2. 调用 API 获取数据
        data = fetch_buff_goods_api(params, profile=profile)
        if not data or not data.items:
            logger.warning("⚠️ 接口未返回数据，请检查 Cookie 或网络")
            return

        logger.info(f"✅ 成功获取数据，共 {len(data.items)} 个商品，开始解析...")

        extracted_results = []
        processed_in_type = set()

        # 3. 执行解析逻辑
        for item in data.items:
            tags = item.tags or {}
            
            # --- 模拟逻辑开始 ---
            if type_internal in ["other", "sticker"]:
                real_parent_internal = type_internal
            else:
                parent_tag = tags.get("type")
                if not parent_tag: continue
                real_parent_internal = parent_tag.get("internal_name")
                if normalize_match_name(real_parent_internal) != normalize_match_name(type_internal):
                    continue
            
            # 确定二级分类
            sub_tag = tags.get("category") or tags.get("weapon")
            if not sub_tag: continue
            
            sub_internal = sub_tag.get("internal_name")
            if not sub_internal or sub_internal == real_parent_internal or sub_internal in processed_in_type: 
                continue
            
            sub_name = sub_tag.get("localized_name") or sub_tag.get("name")
            
            res = {
                "name": sub_name,
                "internal_name": sub_internal,
                "parent_internal_name": real_parent_internal
            }
            # --- 模拟逻辑结束 ---
            
            extracted_results.append(res)
            processed_in_type.add(sub_internal)

        # 4. 输出结果
        if extracted_results:
            logger.info(f"🎊 解析成功！共提取到 {len(extracted_results)} 个二级分类:")
            for r in extracted_results:
                print(f"   - [{r['name']}] (internal: {r['internal_name']}) -> 父级: {r['parent_internal_name']}")
        else:
            logger.warning("❌ 未能提取到任何分类，请检查 API 返回的 tags 结构")

    except Exception as e:
        logger.error(f"❌ 测试过程中发生错误: {e}")

if __name__ == "__main__":
    # test_category_logic("other")
    test_category_logic("sticker")
