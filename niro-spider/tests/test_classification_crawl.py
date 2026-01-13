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

def test_other_category_logic():
    """
    专门测试 'other' 分类组的解析逻辑
    验证当请求参数为 'other' 时，二级分类是否能正确关联到 'other' 父级
    """
    logger.info("🧪 开始测试 'other' 分类解析逻辑...")
    
    # 1. 模拟环境
    type_internal = "other" # 模拟当前正在抓取 'other' 分类组
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

        # 3. 执行 get_buff_goods_category.py 中的核心解析逻辑 (L216-L242)
        for item in data.items:
            tags = item.tags or {}
            
            # --- 核心逻辑开始 ---
            if type_internal == "other":
                # 特殊处理：当请求的是 'other' 分类组时，强制将父级归类为 'other'
                real_parent_internal = "other"
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
            
            # 构造结果
            res = {
                "name": sub_name,
                "internal_name": sub_internal,
                "parent_internal_name": real_parent_internal
            }
            # --- 核心逻辑结束 ---
            
            extracted_results.append(res)
            processed_in_type.add(sub_internal)

        # 4. 输出结果
        if extracted_results:
            logger.info(f"🎊 解析成功！共提取到 {len(extracted_results)} 个二级分类:")
            for r in extracted_results:
                print(f"   - [{r['name']}] (internal: {r['internal_name']}) -> 父级: {r['parent_internal_name']}")
            
            # 特别检查是否包含音乐盒等杂项
            has_music = any("music" in r['internal_name'].lower() for r in extracted_results)
            if has_music:
                logger.info("✨ 验证通过：已成功捕获并正确关联音乐盒等杂项分类！")
        else:
            logger.warning("❌ 未能提取到任何分类，请检查 API 返回的 tags 结构")

    except Exception as e:
        logger.error(f"❌ 测试过程中发生错误: {e}")

if __name__ == "__main__":
    test_other_category_logic()
