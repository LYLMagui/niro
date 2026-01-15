import sys
import os
import argparse

# 将项目根目录添加到 python 路径
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if project_root not in sys.path:
    sys.path.append(project_root)

from spiders.buff_spider import BuffSpider
from utils.logger import get_logger
from enums.buff_enums import BuffPaymentMethod, BuffSortStrategy

logger = get_logger(__name__)

def test_get_buff_item(goods_id: int, min_wear: float = None, max_wear: float = None, max_price: float = None, do_buy: bool = False, pay_method: int = BuffPaymentMethod.BALANCE, count: int = 1):
    """
    测试获取指定商品的 Buff 挂单列表
    :param goods_id: Buff 商品 ID
    :param min_wear: 最小磨损
    :param max_wear: 最大磨损
    :param max_price: 最大价格
    :param do_buy: 是否执行下单测试
    :param pay_method: 支付方式
    :param count: 购买数量
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
            # max_price=max_price,  # 不传入价格限制，获取完整挂单列表
            sort_by=BuffSortStrategy.PRICE_ASC.value
        )
        
        if not items:
            logger.warning(f"⚠️ 未能获取到符合条件的商品数据 (goods_id: {goods_id})")
            return

        logger.info(f"✅ 成功获取到 {len(items)} 条挂单数据 (已按价格正序排列)")

        # 3. 打印目前挂单前五的信息
        logger.info(f"📊 [市场行情] 目前挂单前 5 的商品信息:")
        for i, item in enumerate(items[:5]):
            logger.info(f"   [{i+1}] {item.get('name')} | 价格: {item.get('price_buff')} | 磨损: {item.get('paintwear')} | ID: {item.get('id')}")

        # 4. 过滤并打印 (仅用于下单逻辑)
        if max_price:
            items = [i for i in items if float(i.get('price_buff', 0)) <= max_price]
            logger.info(f"🔍 [价格过滤] 设定最大价格: {max_price} | 过滤后剩余: {len(items)} 件")
        
        # 5. 执行下单测试
        if do_buy:
            # 过滤不支持当前支付方式的商品
            valid_items = [i for i in items if int(pay_method) in i.get('supported_pay_methods', [])]
            
            if not valid_items:
                logger.warning(f"⚠️ 过滤后无可下单商品 (当前支付方式 {BuffPaymentMethod.get_label(pay_method)} 不被任何挂单支持)")
                return
            
            # 获取前 count 个商品
            targets = valid_items[:count]
            item_ids = [t.get('id') for t in targets]
            total_price = sum([float(t.get('price_buff')) for t in targets])
            
            logger.info(f"🎯 [已过滤] 找到符合支付方式的商品共 {len(targets)} 件 | 总价预估: {total_price:.2f}")
            for t in targets:
                logger.info(f"   - {t.get('name')} | 价格: {t.get('price_buff')} | ID: {t.get('id')}")
            
            # 模拟真人下单：随机休眠 1.5 - 3.5 秒
            import random
            sleep_time = random.uniform(1.5, 3.5)
            logger.info(f"⏳ 模拟真人行为，随机休眠 {sleep_time:.2f} 秒...")
            import time
            time.sleep(sleep_time)
            
            logger.info(f"🛒 [发起下单] 数量: {len(item_ids)} | 支付方式: {BuffPaymentMethod.get_label(pay_method)} | 模式: BatchBuy(V3)")
            
            # 使用更安全的 buy_v3 批量下单接口
            result = spider.buy_v3(
                goods_id=goods_id,
                sell_order_ids=item_ids,
                pay_method=pay_method
            )
            
            # result 为字典，如果成功通常包含 id 或 code=='OK'
            if result and (result.get('code') == 'OK' or 'id' in result):
                order_id = result.get('id')
                state = result.get('state_text') or result.get('state')
                logger.info(f"✨ 下单成功! 最终订单号: {order_id} | 状态: {state}")
            elif result and 'pay_url' in str(result):
                # 如果返回的结果中包含支付链接（虽然 buy_v3 内部会打印，这里做双重保证）
                logger.info(f"⚠️ 请点击以下链接完成支付: {result.get('pay_url')}")
            else:
                logger.error(f"❌ 下单失败: {result}")
        
        if items:
            logger.info("✅ 测试流程执行完毕。")

    except Exception as e:
        logger.error(f"❌ 测试过程中发生错误: {e}")

if __name__ == "__main__":
    # 配置测试参数 (可以直接修改这里的默认值)
    TEST_GOODS_ID = 1119940  # M4A4 | 创世终端机
    TEST_MAX_PRICE = 1.9
    TEST_COUNT = 2
    TEST_DO_BUY = True      # 设置为 True 执行下单测试
    
    # 也可以通过命令行覆盖默认参数
    parser = argparse.ArgumentParser(description="测试获取 Buff 商品列表及下单功能")
    parser.add_argument("goods_id", type=int, nargs="?", default=TEST_GOODS_ID, help=f"Buff 商品 ID (默认: {TEST_GOODS_ID})")
    parser.add_argument("--max-price", type=float, default=TEST_MAX_PRICE, help=f"最大价格 (默认: {TEST_MAX_PRICE})")
    parser.add_argument("--count", type=int, default=TEST_COUNT, help=f"最大购买数量 (默认: {TEST_COUNT})")
    parser.add_argument("--buy", action="store_true", default=TEST_DO_BUY, help="是否执行真实下单测试")
    parser.add_argument("--pay-method", type=int, default=BuffPaymentMethod.BALANCE, help=f"支付方式: {BuffPaymentMethod.BALANCE}-余额, {BuffPaymentMethod.ALIPAY}-支付宝")
    
    args = parser.parse_args()
            
    test_get_buff_item(
        goods_id=args.goods_id, 
        max_price=args.max_price, 
        do_buy=args.buy, 
        pay_method=args.pay_method,
        count=args.count
    )
