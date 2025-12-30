import time
import datetime
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger
from spiders.buff_spider import BuffSpider
from storage.postgres_pool import PostgresPool
from utils.logger import get_logger
from utils.notifier import notifier

logger = get_logger(__name__)

class TaskScanner:
    def __init__(self):
        self.pg_pool = PostgresPool()
        # 用户级爬虫实例池: {user_id: BuffSpider}
        self.user_spiders = {}
        # 记录购买失败的 ItemID
        self.failed_items = {}
        self.failed_expire = 120 
        # 待支付保护锁：{user_id: has_pending_order}
        self.user_pending_locks = {}
        
        # 任务启动时间记录: {task_id: start_datetime}
        self.task_start_times = {}
        
        # 初始化调度器
        self.scheduler = BackgroundScheduler()
        self.scheduler.start()
        
        # 已调度的任务作业 ID 映射: {task_id: job_id}
        self.scheduled_jobs = {}
        # 任务配置缓存: {task_id: config_dict}
        self.task_configs = {}

    def get_spider(self, user_id):
        """获取或创建用户的爬虫实例"""
        if user_id not in self.user_spiders:
            logger.info(f"🆕 为用户 {user_id} 创建独立爬虫实例")
            self.user_spiders[user_id] = BuffSpider(user_id=user_id)
        return self.user_spiders[user_id]

    def clean_failed_items(self):
        """清理过期的失败记录"""
        now = time.time()
        expired = [k for k, v in self.failed_items.items() if now - v > self.failed_expire]
        for k in expired:
            del self.failed_items[k]

    def run(self):
        """
        启动主控循环，负责同步数据库任务状态到调度器
        """
        logger.info("🚀 启动扫货任务调度中心 (APScheduler 模式)...")
        
        # 添加一个定时同步任务的作业
        self.scheduler.add_job(
            self.sync_tasks, 
            trigger=IntervalTrigger(seconds=10), 
            id="sync_tasks_job",
            replace_existing=True
        )
        
        # 保持主线程运行
        try:
            while True:
                time.sleep(1)
        except (KeyboardInterrupt, SystemExit):
            self.scheduler.shutdown()

    def sync_tasks(self):
        """同步数据库任务到调度器"""
        try:
            self.clean_failed_items()
            
            # 获取所有任务
            sql = "SELECT * FROM buff_scan_task"
            tasks = self.pg_pool.fetch_all(sql)
            
            active_task_ids = set()
            
            for task in tasks:
                task_id = task['id']
                status = task['status']
                
                # 如果任务是运行中 (1)
                if status == 1:
                    active_task_ids.add(task_id)
                    self.schedule_task(task)
                else:
                    # 如果任务不是运行中，确保调度器中没有它的作业
                    self.unschedule_task(task_id)
            
            # 清理已经不存在的任务
            scheduled_ids = list(self.scheduled_jobs.keys())
            for tid in scheduled_ids:
                if tid not in active_task_ids:
                    self.unschedule_task(tid)
                    
        except Exception as e:
            logger.error(f"❌ 同步任务异常: {e}")

    def schedule_task(self, task):
        """将任务加入调度器或更新现有调度"""
        task_id = task['id']
        cron_expr = task.get('cron_expression')
        scan_interval = task.get('scan_interval') or 5
        duration = task.get('duration_minutes') or 0
        
        new_config = {
            'cron': cron_expr,
            'interval': scan_interval,
            'duration': duration
        }
        
        # 如果已经存在该任务的作业，检查配置是否有变化
        if task_id in self.scheduled_jobs:
            old_config = self.task_configs.get(task_id)
            if old_config == new_config:
                return
            else:
                logger.info(f"🔄 检测到配置变更，正在热更新任务: {task['name']} (ID: {task_id})")
                self.unschedule_task(task_id)

        cron_info = f" [Cron: {cron_expr}]" if cron_expr else " [立即开始]"
        logger.info(f"📅 任务上架: {task['name']} (ID: {task_id}){cron_info} 间隔: {scan_interval}s")
        
        if cron_expr:
            # 如果有 Cron 表达式，先调度一个 Cron 作业，触发时再开启间隔扫描
            job_id = f"scan_task_{task_id}"
            try:
                # 处理 Cron 表达式兼容性 (将 ? 转换为 *，并处理周几的映射)
                # APScheduler 不支持 ?，且周一到周日是 0-6
                parts = cron_expr.strip().split()
                
                # 预处理每一位，将 ? 转换为 *
                processed_parts = [p.replace('?', '*') for p in parts]
                
                if len(processed_parts) == 5:
                    # 标准 crontab: m h d M w
                    trigger = CronTrigger.from_crontab(" ".join(processed_parts))
                elif len(processed_parts) >= 6:
                    # 处理 6 位 (带秒) 或 7 位 (带秒和年) 的表达式
                    # APScheduler CronTrigger 参数顺序: second, minute, hour, day, month, day_of_week, year
                    # 我们的前端顺序是: s, m, h, d, M, w, y
                    
                    second = processed_parts[0]
                    minute = processed_parts[1]
                    hour = processed_parts[2]
                    day = processed_parts[3]
                    month = processed_parts[4]
                    day_of_week = processed_parts[5]
                    year = processed_parts[6] if len(processed_parts) > 6 else None
                    
                    # 处理 last 关键字映射 (Quartz 风格的 L 转换为 APScheduler 的 last)
                    if day.upper() in ['L', 'LAST']:
                        day = 'last'
                    
                    if day_of_week.upper().endswith('L'):
                        # 转换 "SUNL" 为 "last sun"
                        val = day_of_week.upper().replace('L', '')
                        day_of_week = f"last {val.lower()}"
                    elif day_of_week.lower().startswith('last '):
                        # 前端可能直接发送 "last sun"
                        pass
                    
                    # 特殊处理周几的映射
                    # 前端现在发送小写英文缩写 (mon, tue...)，APScheduler 原生支持且语义一致 (mon=0, sun=6)
                    # 如果是数字，则认为是 Quartz 格式 (1=Sun, 2=Mon...)，需要转换为 APScheduler 格式 (0=Mon, 6=Sun)
                    
                    def map_week_part(p):
                        p = p.lower()
                        # 跳过带 last 的部分，因为已经在上面处理过了
                        if 'last' in p:
                            return p
                        if p.isdigit():
                            val = int(p)
                            # Quartz: 1(Sun), 2(Mon), 3(Tue), 4(Wed), 5(Thu), 6(Fri), 7(Sat)
                            # APScheduler: 0(Mon), 1(Tue), 2(Wed), 3(Thu), 4(Fri), 5(Sat), 6(Sun)
                            # 转换逻辑: 1->6, 2->0, 3->1, 4->2, 5->3, 6->4, 7->5
                            return str((val + 5) % 7)
                        return p

                    if ',' in day_of_week:
                        day_of_week = ",".join([map_week_part(p) for p in day_of_week.split(',')])
                    elif '-' in day_of_week:
                        day_of_week = "-".join([map_week_part(p) for p in day_of_week.split('-')])
                    else:
                        day_of_week = map_week_part(day_of_week)
                    
                    trigger = CronTrigger(
                        second=second,
                        minute=minute,
                        hour=hour,
                        day=day,
                        month=month,
                        day_of_week=day_of_week,
                        year=year
                    )
                else:
                    trigger = CronTrigger.from_crontab(" ".join(processed_parts))
                
                self.scheduler.add_job(
                    self.trigger_cron_task,
                    trigger=trigger,
                    args=[task_id],
                    id=job_id,
                    replace_existing=True
                )
                self.scheduled_jobs[task_id] = job_id
            except Exception as e:
                logger.error(f"❌ 解析任务 [ID:{task_id}] 的 Cron 表达式失败: {cron_expr}, 错误: {e}")
                # 如果解析失败，任务将不会被调度
        else:
            # 没有 Cron 表达式，立即开始间隔扫描
            job_id = self.start_scan_job(task)
            self.scheduled_jobs[task_id] = job_id
            
        self.task_configs[task_id] = new_config

    def trigger_cron_task(self, task_id):
        """Cron 触发器：开始执行扫描任务"""
        logger.info(f"⏰ Cron 触发: 任务 [ID:{task_id}] 开始运行...")
        # 重新从数据库获取最新任务信息
        sql = "SELECT * FROM buff_scan_task WHERE id = %s"
        task = self.pg_pool.fetch_one(sql, (task_id,))
        if task and task['status'] == 1:
            job_id = self.start_scan_job(task)
            # 记录下这个活跃的扫描作业，防止 sync_tasks 认为它没上架
            self.scheduled_jobs[task_id] = job_id

    def start_scan_job(self, task):
        """开始间隔扫描作业"""
        task_id = task['id']
        scan_interval = task.get('scan_interval') or 5
        job_id = f"active_scan_{task_id}"
        
        # 记录启动时间用于持续时间判断
        self.task_start_times[task_id] = datetime.datetime.now()
        
        self.scheduler.add_job(
            self.run_scan_cycle,
            trigger=IntervalTrigger(seconds=scan_interval),
            args=[task_id],
            id=job_id,
            replace_existing=True
        )
        logger.info(f"✅ 激活间隔扫描 [ID:{task_id}] 频率: {scan_interval}s")
        
        # 发送启动通知
        self.send_task_start_notification(task)
        
        return job_id

    def unschedule_task(self, task_id):
        """从调度器中移除任务相关的所有作业"""
        if task_id in self.scheduled_jobs:
            job_id = self.scheduled_jobs[task_id]
            if self.scheduler.get_job(job_id):
                self.scheduler.remove_job(job_id)
            del self.scheduled_jobs[task_id]
            
        # 移除活跃的扫描作业
        active_job_id = f"active_scan_{task_id}"
        if self.scheduler.get_job(active_job_id):
            self.scheduler.remove_job(active_job_id)
            
        if task_id in self.task_start_times:
            del self.task_start_times[task_id]
            
        if task_id in self.task_configs:
            del self.task_configs[task_id]

    def run_scan_cycle(self, task_id):
        """执行单次扫描循环"""
        try:
            # 1. 获取最新任务状态
            sql = "SELECT * FROM buff_scan_task WHERE id = %s"
            task = self.pg_pool.fetch_one(sql, (task_id,))
            
            if not task or task['status'] != 1:
                self.unschedule_task(task_id)
                return

            # 2. 持续时间检查
            duration = task.get('duration_minutes') or 0
            if duration > 0 and task_id in self.task_start_times:
                start_time = self.task_start_times[task_id]
                if datetime.datetime.now() > start_time + datetime.timedelta(minutes=duration):
                    logger.info(f"⏱️ 任务 [ID:{task_id}] 运行时间已达 {duration} 分钟，自动停止。")
                    self.stop_task_in_db(task_id)
                    self.send_task_stop_notification(task, "运行时间到期")
                    self.unschedule_task(task_id)
                    return

            # 3. 待支付锁检查
            user_id = task['user_id']
            if self.user_pending_locks.get(user_id):
                # 尝试通过爬虫检查是否还有未支付订单
                spider = self.get_spider(user_id)
                if self.check_user_pending_orders(spider):
                    logger.debug(f"⏳ 用户 {user_id} 仍有未支付订单，跳过本次扫描...")
                    return
                else:
                    logger.info(f"🔓 用户 {user_id} 未支付订单已处理，释放保护锁")
                    self.user_pending_locks[user_id] = False

            # 4. 执行业务处理
            spider = self.get_spider(user_id)
            spider.refresh_cookie()
            self.process_task(task, spider)
            
        except Exception as e:
            logger.error(f"❌ 任务 [ID:{task_id}] 扫描异常: {e}")

    def check_user_pending_orders(self, spider):
        """检查用户是否有待支付订单 (通过调用 API)"""
        # 这里的实现取决于 BuffSpider 是否有检查订单的方法
        # 暂时简单处理：返回 True（即默认锁定，靠外部逻辑或超时释放）
        # 或者调用 spider.get_buy_order_history() 检查是否有 "待支付" 状态
        return self.user_pending_locks.get(spider.user_id, False)

    def stop_task_in_db(self, task_id):
        """更新数据库任务状态为停止(0)"""
        sql = "UPDATE buff_scan_task SET status = 0 WHERE id = %s"
        self.pg_pool.execute(sql, (task_id,))

    def process_task(self, task, spider):
        """处理单个扫描任务"""
        task_type = task.get('task_type', 0)
        goods_id = task['goods_id']
        
        # 获取饰品列表
        items = spider.get_goods_list(goods_id)
        if not items:
            return

        # 记录价格历史 (取第一项的元数据即可)
        self.record_price_history(items[0])
        
        if task_type == 1:
            # 站内倒卖模式
            self.process_flipping_logic(task, items, spider)
        else:
            # 炼金扫货模式 (默认)
            self.process_sniping_logic(task, items, spider)

    def record_price_history(self, item):
        """记录价格历史"""
        try:
            goods_id = item.get('goods_id')
            price = item.get('sell_min_price')
            buy_max_price = item.get('buy_max_price')
            sell_num = item.get('sell_num')
            
            if not goods_id or price is None:
                return
                
            sql = """
                INSERT INTO buff_price_history (goods_id, price, buy_max_price, sell_num)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (goods_id, create_time) DO NOTHING
            """
            self.pg_pool.execute(sql, (goods_id, price, buy_max_price, sell_num))
        except Exception as e:
            logger.error(f"记录价格历史异常: {e}")

    def get_avg_price_24h(self, goods_id):
        """获取 24 小时内的平均价"""
        try:
            sql = """
                SELECT AVG(price) as avg_price 
                FROM buff_price_history 
                WHERE goods_id = %s AND create_time > NOW() - INTERVAL '24 hours'
            """
            res = self.pg_pool.fetch_one(sql, (goods_id,))
            return float(res['avg_price']) if res and res['avg_price'] else None
        except Exception as e:
            logger.error(f"查询历史均价异常: {e}")
            return None

    def process_sniping_logic(self, task, items, spider):
        """处理炼金扫货逻辑 (严格匹配价格和磨损)"""
        max_price = task['max_price']
        min_wear = task['min_paintwear']
        max_wear = task['max_paintwear']
        
        for item in items:
            try:
                if item.get('id') in self.failed_items:
                    continue

                price_str = item.get('price_buff')
                paintwear_str = item.get('paintwear')
                
                if not price_str or not paintwear_str:
                    continue
                    
                price = float(price_str)
                paintwear = float(paintwear_str)
                
                # 校验价格
                if price > float(max_price):
                    continue
                
                # 校验磨损
                if min_wear is not None and paintwear < float(min_wear):
                    continue
                if max_wear is not None and paintwear > float(max_wear):
                    continue
                
                logger.info(f"🎯 [炼金发现] 价格:{price}, 磨损:{paintwear}, ID:{item['id']}")
                self.buy_goods(task, item, spider)
                break 

            except Exception as e:
                logger.error(f"处理炼金商品项异常: {e}")

    def process_flipping_logic(self, task, items, spider):
        """处理站内倒卖逻辑 (计算利润 + 均价风控)"""
        goods_id = task['goods_id']
        min_profit_config = float(task.get('min_profit') or 0)
        
        # 获取 24 小时均价用于风控
        avg_price_24h = self.get_avg_price_24h(goods_id)
        
        for item in items:
            try:
                if item.get('id') in self.failed_items:
                    continue

                price_str = item.get('price_buff')
                if not price_str:
                    continue
                
                current_price = float(price_str)
                # 获取市场最低售价
                market_floor = float(item.get('sell_min_price') or 0)
                
                if market_floor <= 0:
                    continue
                
                # --- 风控逻辑 START ---
                # 如果当前市场底价远高于 24h 均价（例如超过 1.1 倍），说明可能有人在拉高价格钓鱼，跳过
                if avg_price_24h and market_floor > avg_price_24h * 1.15:
                    logger.warning(f"⚠️ [风控] 商品 {goods_id} 当前底价 {market_floor} 远高于 24h 均价 {avg_price_24h:.2f}，疑似价格操纵，跳过。")
                    continue
                # --- 风控逻辑 END ---

                # 计算利润 = (市场最低价 * 0.975) - 当前价格
                # 0.975 是扣除 2.5% 手续费后的比例
                estimated_profit = (market_floor * 0.975) - current_price
                
                if estimated_profit >= min_profit_config:
                    logger.info(f"💰 [倒卖发现] 当前价:{current_price}, 最低价:{market_floor}, 预计利润:¥{estimated_profit:.2f}, ID:{item['id']}")
                    self.buy_goods(task, item, spider)
                    break

            except Exception as e:
                logger.error(f"处理倒卖商品项异常: {e}")

    def buy_goods(self, task, item, spider):
        """执行购买逻辑 (已切换为测试模式，仅发送通知不真实下单)"""
        user_id = task['user_id']
        
        # --- 模拟测试模式 START (不调用真实 API) ---
        logger.info(f"🧪 [测试模式] 模拟下单成功: {item['name']} (ID:{item['id']})")
        result = {
            "code": "OK",
            "data": {
                "id": f"MOCK_ORDER_{int(time.time())}",
                "state_text": "待支付 (模拟测试)",
                "pay_url": "https://buff.163.com/market/buy_order/history"
            }
        }
        
        # 原真实下单逻辑 (暂时注释)
        """
        result = spider.buy(task['goods_id'], item['id'], item['price_buff'], pay_method=44)
        
        if isinstance(result, dict) and result.get("code") != "OK":
            error_msg = result.get("error_msg", "")
            if "Already Paying" in error_msg:
                self.user_pending_locks[user_id] = True
                return
            if "该饰品暂不支持此支付方式" in error_msg:
                result = spider.buy(task['goods_id'], item['id'], item['price_buff'], pay_method=3)
        """
        # --- 模拟测试模式 END ---
        
        if isinstance(result, dict) and (result.get("id") or result.get("code") == "OK"):
            order_data = result if result.get("id") else result.get("data", {})
            # if order_data.get('pay_url'):
            #     self.user_pending_locks[user_id] = True
            
            self.update_task_progress(task['id'])
            self.send_buy_notification(task, item, order_data)
        else:
            self.failed_items[item['id']] = time.time()

    def update_task_progress(self, task_id):
        """更新任务进度"""
        try:
            sql = "UPDATE buff_scan_task SET success_count = success_count + 1 WHERE id = %s"
            self.pg_pool.execute(sql, (task_id,))
            
            check_sql = "SELECT success_count, buy_count FROM buff_scan_task WHERE id = %s"
            res = self.pg_pool.fetch_one(check_sql, (task_id,))
            if res and res['success_count'] >= res['buy_count']:
                stop_sql = "UPDATE buff_scan_task SET status = 2 WHERE id = %s"
                self.pg_pool.execute(stop_sql, (task_id,))
                logger.info(f"🏁 任务 [ID:{task_id}] 已达到购买上限，自动停止。")
                
                # 获取完整任务信息用于通知
                task_sql = "SELECT * FROM buff_scan_task WHERE id = %s"
                task = self.pg_pool.fetch_one(task_sql, (task_id,))
                if task:
                    self.send_task_stop_notification(task, "达到购买上限")
                
                self.unschedule_task(task_id)
        except Exception as e:
            logger.error(f"更新任务进度失败: {e}")

    def send_task_start_notification(self, task):
        """发送任务启动通知"""
        try:
            task_name = task.get('name', '未知任务')
            scan_interval = task.get('scan_interval') or 5
            duration = task.get('duration_minutes') or 0
            buy_count = task.get('buy_count') or 0
            
            start_time = datetime.datetime.now()
            start_time_str = start_time.strftime("%Y-%m-%d %H:%M:%S")
            
            end_time_str = "无限制"
            if duration > 0:
                end_time = start_time + datetime.timedelta(minutes=duration)
                end_time_str = end_time.strftime("%Y-%m-%d %H:%M:%S")
            
            content = (
                f"🚀 任务启动通知\n"
                f"------------------\n"
                f"任务名称: {task_name}\n"
                f"启动时间: {start_time_str}\n"
                f"预计结束: {end_time_str}\n"
                f"扫描间隔: {scan_interval}s\n"
                f"目标数量: {buy_count}"
            )
            
            notifier.send_text(
                content=content,
                user_id=task.get('user_id')
            )
            logger.info(f"📤 已发送任务启动通知: {task_name}")
        except Exception as e:
            logger.error(f"发送任务启动通知失败: {e}")

    def send_task_stop_notification(self, task, reason):
        """发送任务停止通知"""
        try:
            task_name = task.get('name', '未知任务')
            success_count = task.get('success_count', 0)
            buy_count = task.get('buy_count', 0)
            
            content = (
                f"🏁 任务停止通知\n"
                f"------------------\n"
                f"任务名称: {task_name}\n"
                f"停止原因: {reason}\n"
                f"完成进度: {success_count}/{buy_count}"
            )
            
            notifier.send_text(
                content=content,
                user_id=task.get('user_id')
            )
            logger.info(f"📤 已发送任务停止通知: {task_name} ({reason})")
        except Exception as e:
            logger.error(f"发送任务停止通知失败: {e}")

    def send_buy_notification(self, task, item, result):
        """发送购买成功通知"""
        try:
            name = item.get('name', '未知商品')
            price = item.get('price_buff', '0')
            paintwear = item.get('paintwear', '无')
            order_id = result.get('id', '未知')
            state = result.get('state_text', '已下单')
            
            status_text = "✅ 购买成功" if "支付成功" in state else "🔔 订单已创建 (待支付)"
            content = (
                f"{status_text}\n"
                f"------------------\n"
                f"商品名称: {name}\n"
                f"成交价格: ¥{price}\n"
                f"磨损程度: {paintwear}\n"
                f"订单状态: {state}\n"
                f"订单编号: {order_id}"
            )

            notifier.send_text(
                content=content,
                user_id=task.get('user_id')
            )
        except Exception as e:
            logger.error(f"发送购买通知失败: {e}")
