import time
from spiders.buff_spider import BuffSpider
from storage.postgres_pool import PostgresPool
from utils.logger import get_logger
from utils.notifier import notifier

logger = get_logger(__name__)

class TaskScanner:
    def __init__(self):
        self.spider = BuffSpider()
        self.pg_pool = PostgresPool()
        # 记录购买失败的 ItemID
        self.failed_items = {}
        self.failed_expire = 120 
        # 待支付保护锁：如果发现有未支付订单，暂时停止扫描
        self.has_pending_order = False
        self.last_pending_check = 0

    def clean_failed_items(self):
        """清理过期的失败记录"""
        now = time.time()
        expired = [k for k, v in self.failed_items.items() if now - v > self.failed_expire]
        for k in expired:
            del self.failed_items[k]

    def run(self):
        """
        主运行循环
        """
        logger.info("🚀 启动扫货任务扫描器...")
        while True:
            try:
                # 如果有待支付订单，每分钟检查一次是否已支付（或者等待用户手动支付）
                if self.has_pending_order:
                    logger.info("⏳ 检测到有未支付订单，扫描器进入等待模式（请尽快支付）...")
                    time.sleep(30) # 每 30 秒查一次，或者你可以去支付
                    # 这里简单处理，30秒后尝试恢复，如果还报错会再次进入
                    self.has_pending_order = False 
                    continue

                self.clean_failed_items()
                # 每次大循环开始前刷新一次 Cookie
                self.spider.refresh_cookie()
                
                # 1. 获取所有运行中的任务
                tasks = self.get_active_tasks()
                if not tasks:
                    logger.info("当前无运行中任务，休眠 5 秒...")
                    time.sleep(5)
                    continue

                logger.info(f"发现 {len(tasks)} 个运行中任务，开始扫描...")
                
                # 2. 遍历任务执行扫描
                for task in tasks:
                    self.process_task(task)
                    # 任务间稍微停顿，避免请求过快
                    time.sleep(1)

            except Exception as e:
                logger.error(f"❌ 扫描循环发生异常: {e}", exc_info=True)
                time.sleep(10)

    def get_active_tasks(self):
        """从数据库获取状态为运行中(1)的任务"""
        sql = "SELECT * FROM buff_scan_task WHERE status = 1"
        return self.pg_pool.fetch_all(sql)

    def process_task(self, task):
        """处理单个扫描任务"""
        goods_id = task['goods_id']
        max_price = task['max_price']
        min_wear = task['min_wear']
        max_wear = task['max_wear']
        
        logger.info(f"正在扫描任务 [ID:{task['id']}] GoodsID:{goods_id}...")
        
        # 调用爬虫获取商品列表 (默认第一页)
        items = self.spider.get_goods_list(goods_id)
        if not items:
            return

        # 筛选符合条件的商品
        match_count = 0
        for item in items:
            try:
                # 过滤掉近期购买失败的 ID
                if item.get('id') in self.failed_items:
                    continue

                price_str = item.get('price_buff')
                paintwear_str = item.get('paintwear')
                
                if not price_str or not paintwear_str:
                    continue
                    
                price = float(price_str)
                paintwear = float(paintwear_str)
                
                # 价格过滤
                if price > float(max_price):
                    continue
                
                # 磨损过滤 (如果有配置)
                if min_wear is not None and paintwear < float(min_wear):
                    continue
                if max_wear is not None and paintwear > float(max_wear):
                    continue
                
                # 发现匹配商品！
                match_count += 1
                logger.info(f"✅ 发现目标商品! 价格:{price}, 磨损:{paintwear}, ID:{item['id']}")
                
                # 执行购买
                self.buy_goods(task, item)
                
                # 如果任务只需要买一个，买完就跳出
                # (这里可以根据业务需求扩展，比如买完后自动停止任务)
                break 

            except Exception as e:
                logger.error(f"处理商品项异常: {e}")
        
        if match_count == 0:
            # 记录一下当前最低价，方便观察
            min_price = items[0].get('price_buff')
            logger.info(f"📈 扫描完成: 商品[{items[0].get('name')}] 当前最低价: {min_price} (目标上限: {max_price}) - 未满足筛选条件")

    def buy_goods(self, task, item):
        """执行购买逻辑"""
        logger.info(f"💰 [发起购买] 任务ID:{task['id']} 商品ID:{task['goods_id']} ItemID:{item['id']} 价格:{item['price_buff']}")
        
        # 1. 尝试默认支付方式 (44=余额)
        result = self.spider.buy(task['goods_id'], item['id'], item['price_buff'], pay_method=44)
        
        # 检查是否由于支付方式不支持或被封禁导致失败
        if isinstance(result, dict) and result.get("code") != "OK":
            error_msg = result.get("error_msg", "")
            error_code = result.get("code", "")
            
            # 如果有未支付订单
            if "Already Paying" in error_msg or "Already Paying" in error_code:
                logger.warning("⚠️ 检测到有未支付订单，激活保护锁...")
                self.has_pending_order = True
                return

            # 如果余额支付不支持，或者余额支付进入冷却期 (Cooling Down)
            if "该饰品暂不支持此支付方式" in error_msg or "Cooling Down" in error_code:
                logger.warning(f"⚠️ 余额支付不可用({error_code})，尝试使用 [支付宝] 模式仅下单...")
                # 2. 备选方案：使用支付宝模式 (pay_method=3)，生成订单并返回支付链接
                result = self.spider.buy(task['goods_id'], item['id'], item['price_buff'], pay_method=3)
        
        # 判断最终结果
        if isinstance(result, dict) and (result.get("id") or result.get("code") == "OK"):
            order_data = result if result.get("id") else result.get("data", {})
            logger.info(f"✅ 购买/下单成功！更新任务进度... 订单ID: {order_data.get('id')}")
            
            # 如果是支付宝模式下单，激活保护锁
            if order_data.get('pay_url'):
                logger.info("🔔 支付宝下单成功，激活待支付保护锁。")
                self.has_pending_order = True
            self.update_task_progress(task['id'])
            # 发送通知 (包含支付链接)
            self.send_buy_notification(task, item, order_data)
        else:
            logger.error(f"❌ 购买最终失败: {result}")
            # 记录失败，防止缓存导致重试
            self.failed_items[item['id']] = time.time()

    def update_task_progress(self, task_id):
        """更新任务进度"""
        try:
            # 任务执行次数+1，如果达到购买数量则停止任务
            # 这里简单处理：先只更新执行次数
            sql = "UPDATE buff_scan_task SET buy_count = buy_count + 1 WHERE id = %s"
            self.pg_pool.execute(sql, (task_id,))
            
            # 检查是否完成
            check_sql = "SELECT buy_count, buy_limit FROM buff_scan_task WHERE id = %s"
            res = self.pg_pool.fetch_one(check_sql, (task_id,))
            if res and res['buy_count'] >= res['buy_limit']:
                stop_sql = "UPDATE buff_scan_task SET status = 2 WHERE id = %s"
                self.pg_pool.execute(stop_sql, (task_id,))
                logger.info(f"🏁 任务 [ID:{task_id}] 已达到购买上限，自动停止。")
        except Exception as e:
            logger.error(f"更新任务进度失败: {e}")

    def send_buy_notification(self, task, item, result):
        """发送购买成功通知 (使用文本卡片消息，兼容微信插件)"""
        try:
            name = item.get('name', '未知商品')
            price = item.get('price_buff', '0')
            paintwear = item.get('paintwear', '无')
            order_id = result.get('id', '未知')
            state = result.get('state_text', '已下单')
            pay_url = result.get('pay_url') or "https://buff.163.com/market/buy_order/history"
            
            status_text = "✅ 购买成功" if "支付成功" in state else "🔔 订单已创建 (待支付)"
            
            title = status_text
            description = f"""商品名称: {name}
成交价格: ¥{price}
磨损程度: {paintwear}
订单状态: {state}
订单编号: {order_id}

请尽快点击下方按钮完成支付"""

            # 使用文本卡片消息，微信插件完美支持
            notifier.send_textcard(
                title=title,
                description=description,
                url=pay_url,
                btntxt="点击前往支付"
            )
        except Exception as e:
            logger.error(f"发送购买通知失败: {e}")
