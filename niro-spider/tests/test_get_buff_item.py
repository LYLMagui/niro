import sys
import os
import argparse

# 将项目根目录添加到 python 路径
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if project_root not in sys.path:
    sys.path.append(project_root)

from spiders.buff_spider import BuffSpider
from utils.logger import get_logger

logger = get_logger(__name__)

def test_get_buff_item(goods_id: int, min_wear: float = None, max_wear: float = None, max_price: float = None, do_buy: bool = False, pay_method: int = 44):
    """
    测试获取指定商品的 Buff 挂单列表
    :param goods_id: Buff 商品 ID
    :param min_wear: 最小磨损
    :param max_wear: 最大磨损
    :param max_price: 最大价格
    :param do_buy: 是否执行下单测试
    :param pay_method: 支付方式 (44: 余额/网易支付, 3: 支付宝)
    """
    filter_info = []
    if min_wear is not None: filter_info.append(f"min_wear={min_wear}")
    if max_wear is not None: filter_info.append(f"max_wear={max_wear}")
    if max_price is not None: filter_info.append(f"max_price={max_price}")
    
    logger.info(f"🧪 开始测试获取商品 ID 为 [{goods_id}] 的 Buff 数据... 过滤条件: {', '.join(filter_info) if filter_info else '无'}")
    
    # 1. 初始化 Spider
    spider = BuffSpider()
    
    try:
        # 2. 调用获取商品列表接口
        # 默认使用 price.asc 排序
        items = spider.get_goods_list(
            goods_id=goods_id, 
            page_num=1,
            min_paintwear=min_wear,
            max_paintwear=max_wear,
            max_price=max_price,
            sort_by="price.asc"
        )
        
        if not items:
            logger.warning(f"⚠️ 未能获取到符合条件的商品数据 (goods_id: {goods_id})")
            return

        logger.info(f"✅ 成功获取到 {len(items)} 条挂单数据 (已按价格正序排列)")

        # 3. 打印前 5 条数据进行验证
        for i, item in enumerate(items[:5]):
            logger.info(f"--- 挂单 {i+1} ---")
            logger.info(f"ID: {item.get('id')}")
            logger.info(f"名称: {item.get('name')}")
            logger.info(f"价格 (BUFF): {item.get('price_buff')} 元")
            logger.info(f"磨损: {item.get('paintwear')}")
            logger.info(f"卖家: {item.get('user_nickname')}")
            logger.info(f"支持支付方式: {item.get('supported_pay_methods')} (44:余额, 3:支付宝, 1:银行卡, 6:微信, 7:银联)")

        # 4. 执行下单测试
        if do_buy:
            # 过滤不支持当前支付方式的商品
            valid_items = [i for i in items if pay_method in i.get('supported_pay_methods', [])]
            
            if not valid_items:
                logger.warning(f"⚠️ 过滤后无可下单商品 (当前支付方式 {pay_method} 不被任何挂单支持)")
                return
            
            target_item = valid_items[0]
            item_id = target_item.get('id')
            price = target_item.get('price_buff')
            item_name = target_item.get('name')
            
            logger.info(f"🎯 [已过滤] 找到符合支付方式的最低价商品: {item_name} | 价格: {price}")
            
            # 模拟真人下单：随机休眠 1.5 - 3.5 秒
            import random
            sleep_time = random.uniform(1.5, 3.5)
            logger.info(f"⏳ 模拟真人行为，随机休眠 {sleep_time:.2f} 秒...")
            import time
            time.sleep(sleep_time)
            
            logger.info(f"🛒 [发起下单] ID: {item_id} | 支付方式: {pay_method}")
            
            result = spider.buy(
                goods_id=goods_id,
                item_id=item_id,
                price_str=str(price),
                pay_method=pay_method
            )
            
            if result and isinstance(result, dict) and (result.get('id') or result.get('code') == 'OK'):
                logger.info(f"✨ 下单请求成功! 订单详情: {result}")
            else:
                logger.error(f"❌ 下单请求失败")
        
        if items:
            logger.info("✅ 测试流程执行完毕。")

    except Exception as e:
        logger.error(f"❌ 测试过程中发生错误: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="测试获取 Buff 商品列表及下单功能")
    parser.add_argument("goods_id", type=int, nargs="?", default=1119940, help="Buff 商品 ID (默认: 1119940)")
    parser.add_argument("--min-wear", type=float, help="最小磨损")
    parser.add_argument("--max-wear", type=float, help="最大磨损")
    parser.add_argument("--max-price", type=float, default=1.98, help="最大价格 (元)")
    parser.add_argument("--buy", action="store_true", help="是否执行真实下单测试")
    parser.add_argument("--pay-method", type=int, default=44, help="支付方式: 44-余额/网易支付, 3-支付宝 (默认: 44)")
    
    args = parser.parse_args()
            
    test_get_buff_item(args.goods_id, args.min_wear, args.max_wear, args.max_price, args.buy, args.pay_method)
