import time
import datetime
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger
from spiders.buff_spider import BuffSpider
from spiders.get_buff_goods import run_goods_sync
from spiders.get_buff_goods_category import run_category_sync
from storage.postgres_pool import PostgresPool
from utils.logger import get_logger
from utils.notifier import notifier
from utils.exception_handler import LoginRequiredError

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
        
        # 正在执行的任务记录: {task_id: True}
        self.running_tasks = {}
        
        # 登录失败次数统计: {task_id: count}
        self.login_error_counts = {}
        
        # 通用错误次数统计: {task_id: count}
        self.general_error_counts = {}
        
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
        
        # 立即执行一次同步
        self.sync_tasks()
        
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
            
            if not tasks:
                if hasattr(self, '_last_task_count') and self._last_task_count > 0:
                    logger.info("ℹ️ 数据库中任务已清空")
                self._last_task_count = 0
                return
            
            # 记录任务总数变化
            task_count = len(tasks)
            if not hasattr(self, '_last_task_count') or self._last_task_count != task_count:
                logger.info(f"🔍 数据库任务同步: 总计 {task_count} 个任务")
                self._last_task_count = task_count
            
            for task in tasks:
                task_id = task['id']
                status = task['status']
                
                # 如果任务是运行中 (1)
                if status == 1:
                    active_task_ids.add(task_id)
                    self.schedule_task(task)
                elif status == 4:
                    # 如果任务状态是“执行中”，但当前实例并没有记录它在运行，说明可能卡住了（如进程重启）
                    if task_id not in self.running_tasks:
                        logger.warning(f"⚠️ 任务 [ID:{task_id}] 状态为执行中但未在运行队列，尝试重置状态...")
                        # 如果有 cron，重置为 1 (待运行)，否则重置为 0 (停止)
                        if task.get('cron_expression'):
                            self.update_task_status(task_id, 1)
                        else:
                            self.update_task_status(task_id, 0)
                else:
                    # 如果任务不是运行中，确保调度器中已移除
                    self.unschedule_task(task_id)
            
            # 记录运行中任务的变化
            active_count = len(active_task_ids)
            if not hasattr(self, '_last_active_count') or self._last_active_count != active_count:
                if active_count > 0:
                    logger.info(f"📈 当前有 {active_count} 个运行中的任务")
                else:
                    logger.info("ℹ️ 当前无运行中的任务")
                self._last_active_count = active_count
            
            # 清理已经不在数据库活跃列表中的配置缓存
            cached_ids = list(self.task_configs.keys())
            for tid in cached_ids:
                if tid not in active_task_ids:
                    del self.task_configs[tid]
                    
        except Exception as e:
            logger.error(f"❌ 同步任务失败: {e}", exc_info=True)

    def parse_cron(self, cron_expr):
        """解析 6 位 Cron 表达式 (Spring/Quartz 格式) 为 APScheduler 的 CronTrigger"""
        parts = cron_expr.split()
        if len(parts) == 6:
            # Spring 格式: second minute hour day month day_of_week
            # 注意: Spring 的 ? 在 APScheduler 中直接用 * 即可
            return CronTrigger(
                second=parts[0],
                minute=parts[1],
                hour=parts[2],
                day=parts[3],
                month=parts[4],
                day_of_week=parts[5].replace('?', '*')
            )
        elif len(parts) == 5:
            # 标准 Unix 格式: minute hour day month day_of_week
            return CronTrigger.from_crontab(cron_expr)
        else:
            raise ValueError(f"不支持的 Cron 格式: {cron_expr}")

    def schedule_task(self, task):
        """将任务加入调度器或更新现有调度"""
        task_id = task['id']
        cron_expr = task.get('cron_expression')
        scan_interval = task.get('scan_interval') or 5
        duration = task.get('duration_minutes') or 0
        
        # 构造一个唯一的配置标识，用于检测配置是否变更
        new_config = {
            'cron': cron_expr,
            'interval': scan_interval,
            'type': task.get('task_type')
        }
        
        # 如果已经存在该任务的作业，检查配置是否有变化
        if task_id in self.scheduled_jobs:
            old_config = self.task_configs.get(task_id)
            if old_config == new_config:
                return
            else:
                logger.info(f"🔄 检测到配置变更，正在更新任务: {task['name']} (ID: {task_id})")
                self.unschedule_task(task_id)

        if cron_expr:
            logger.info(f"📅 任务上架 (周期执行): {task['name']} (ID: {task_id}) [Cron: {cron_expr}]")
            job_id = f"cron_task_{task_id}"
            try:
                trigger = self.parse_cron(cron_expr)
                self.scheduler.add_job(
                    self.trigger_cron_task,
                    trigger=trigger,
                    args=[task_id],
                    id=job_id,
                    replace_existing=True
                )
                self.scheduled_jobs[task_id] = job_id
            except Exception as e:
                logger.error(f"❌ Cron 表达式解析失败: {cron_expr}, 错误: {e}")
                return
        else:
            # 没有 Cron 表达式，立即开始
            task_type = task.get('task_type', 0)
            if task_type >= 2:
                logger.info(f"🚀 任务启动 (单次执行): {task['name']} (ID: {task_id})")
                # 系统任务：执行一次
                job_id = f"system_task_immediate_{task_id}_{int(time.time())}"
                
                # 在启动前先标记为运行中，防止 sync_tasks 误判
                self.running_tasks[task_id] = True
                self.update_task_status(task_id, 4)
                
                self.scheduler.add_job(
                    self.run_system_task,
                    args=[task],
                    id=job_id
                )
            else:
                logger.info(f"🚀 任务启动 (循环扫描): {task['name']} (ID: {task_id}) [间隔: {scan_interval}s]")
                # 普通任务：开启间隔扫描
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
            task_type = task.get('task_type', 0)
            if task_type >= 2:
                # 系统任务：设置为执行中状态并直接执行
                self.running_tasks[task_id] = True
                self.update_task_status(task_id, 4)
                self.scheduler.add_job(
                    self.run_system_task,
                    args=[task],
                    id=f"system_task_{task_id}_{int(time.time())}"
                )
            else:
                # 普通任务：开启间隔扫描
                job_id = self.start_scan_job(task)
                # 记录下这个活跃的扫描作业，防止 sync_tasks 认为它没上架
                self.scheduled_jobs[task_id] = job_id

    def run_system_task(self, task):
        """执行单次系统同步任务"""
        task_id = task['id']
        task_type = task.get('task_type')
        task_name = task.get('name', '系统同步任务')
        
        # 标记为运行中 (如果 TaskScanner 中有 running_tasks 属性)
        if hasattr(self, 'running_tasks'):
            self.running_tasks[task_id] = True
            
        logger.info(f"🚀 开始执行系统任务 [ID:{task_id}]: {task_name}")
        
        # 发送启动通知
        self.send_task_start_notification(task)
        
        try:
            if task_type == 2:
                # 系统-分类同步
                run_category_sync(task_id=task_id)
            elif task_type == 3:
                # 系统-商品同步
                run_goods_sync(force=False, task_id=task_id)
            
            # 检查是否在运行过程中已被停止 (如果有 running_tasks 属性)
            is_running = True
            if hasattr(self, 'running_tasks'):
                is_running = task_id in self.running_tasks

            if is_running:
                logger.info(f"✅ 系统任务执行完成 [ID:{task_id}]")
                
                # 如果是周期任务，设置回 1 (Active)，否则设置为 0 (Stopped)
                cron_expr = task.get('cron_expression')
                if cron_expr:
                    self.update_task_status(task_id, 1)
                else:
                    self.stop_task_in_db(task_id)
                
                self.send_task_stop_notification(task, "同步完成")
                # 成功后重置计数
                if task_id in self.login_error_counts:
                    self.login_error_counts[task_id] = 0
                if task_id in self.general_error_counts:
                    self.general_error_counts[task_id] = 0
            else:
                logger.warning(f"⚠️ 系统任务 [ID:{task_id}] 在运行过程中已被停止")
                
        except LoginRequiredError:
            count = self.login_error_counts.get(task_id, 0) + 1
            self.login_error_counts[task_id] = count
            logger.error(f"🔑 系统任务 [ID:{task_id}] 第 {count} 次检测到 Cookie 失效/未登录")
            
            if count >= 3:
                logger.critical(f"🛑 系统任务 [ID:{task_id}] 连续 3 次登录失效，正在停止任务...")
                self.set_task_error(task_id)
                self.send_task_stop_notification(task, "Cookie 连续失效 3 次，请更新全局 BUFF_COOKIE")
                self.login_error_counts[task_id] = 0
            else:
                # 还没到 3 次，重置回待运行(1)状态，以便下次触发
                self.update_task_status(task_id, 1)
                
        except Exception as e:
            count = self.general_error_counts.get(task_id, 0) + 1
            self.general_error_counts[task_id] = count
            logger.error(f"❌ 系统任务 [ID:{task_id}] 第 {count} 次执行异常: {e}")
            
            if count >= 3:
                logger.error(f"🛑 系统任务 [ID:{task_id}] 连续 {count} 次执行异常，正在停止任务...")
                self.set_task_error(task_id)
                self.send_task_stop_notification(task, f"同步失败: {e}")
                self.general_error_counts[task_id] = 0
            else:
                # 还没到 3 次，重置回待运行(1)状态
                self.update_task_status(task_id, 1)
        finally:
            if hasattr(self, 'running_tasks') and task_id in self.running_tasks:
                del self.running_tasks[task_id]
            
            # 如果是单次执行的任务，从已调度列表中移除，以便下次 sync_tasks 能再次触发它
            if not task.get('cron_expression') and task_id in self.scheduled_jobs:
                del self.scheduled_jobs[task_id]

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

        if task_id in self.login_error_counts:
            del self.login_error_counts[task_id]

        if hasattr(self, 'running_tasks') and task_id in self.running_tasks:
            del self.running_tasks[task_id]

    def run_scan_cycle(self, task_id):
        """执行单次扫描循环"""
        # 标记为运行中
        self.running_tasks[task_id] = True
        
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
            
            # 成功执行后重置失败计数
            if task_id in self.login_error_counts:
                self.login_error_counts[task_id] = 0
            if task_id in self.general_error_counts:
                self.general_error_counts[task_id] = 0
            
        except LoginRequiredError:
            count = self.login_error_counts.get(task_id, 0) + 1
            self.login_error_counts[task_id] = count
            logger.error(f"🔑 任务 [ID:{task_id}] 第 {count} 次检测到 Cookie 失效/未登录")
            
            if count >= 3:
                logger.critical(f"🛑 任务 [ID:{task_id}] 连续 3 次登录失效，正在停止任务...")
                self.set_task_error(task_id)
                self.send_task_stop_notification(task, "Cookie 连续失效 3 次，请重新配置")
                self.unschedule_task(task_id)
                self.login_error_counts[task_id] = 0
                
        except Exception as e:
            count = self.general_error_counts.get(task_id, 0) + 1
            self.general_error_counts[task_id] = count
            logger.error(f"❌ 任务 [ID:{task_id}] 第 {count} 次扫描异常: {e}")
            
            if count >= 5: # 扫描任务允许更多次失败（如网络波动）
                logger.error(f"🛑 任务 [ID:{task_id}] 连续 {count} 次扫描异常，正在停止任务...")
                self.set_task_error(task_id)
                self.unschedule_task(task_id)
                self.general_error_counts[task_id] = 0
            # 不到 5 次则等待下个周期继续重试，不 unschedule
        finally:
            if task_id in self.running_tasks:
                del self.running_tasks[task_id]

    def check_user_pending_orders(self, spider):
        """检查用户是否有待支付订单 (通过调用 API)"""
        # 这里的实现取决于 BuffSpider 是否有检查订单的方法
        # 暂时简单处理：返回 True（即默认锁定，靠外部逻辑或超时释放）
        # 或者调用 spider.get_buy_order_history() 检查是否有 "待支付" 状态
        return self.user_pending_locks.get(spider.user_id, False)

    def update_task_status(self, task_id, status):
        """更新数据库任务状态"""
        sql = "UPDATE buff_scan_task SET status = %s WHERE id = %s"
        self.pg_pool.execute(sql, (status, task_id))

    def stop_task_in_db(self, task_id):
        """更新数据库任务状态为停止(0)"""
        sql = "UPDATE buff_scan_task SET status = 0 WHERE id = %s"
        self.pg_pool.execute(sql, (task_id,))

    def set_task_error(self, task_id):
        """更新数据库任务状态为异常(3)"""
        sql = "UPDATE buff_scan_task SET status = 3 WHERE id = %s"
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
            task_type = task.get('task_type', 0)
            
            start_time = datetime.datetime.now()
            start_time_str = start_time.strftime("%Y-%m-%d %H:%M:%S")
            
            if task_type >= 2:
                # 系统同步任务通知
                content = (
                    f"🚀 系统同步启动\n"
                    f"------------------\n"
                    f"任务名称: {task_name}\n"
                    f"启动时间: {start_time_str}\n"
                    f"任务类型: {'分类同步' if task_type == 2 else '商品同步'}"
                )
            else:
                # 普通扫货任务通知
                scan_interval = task.get('scan_interval') or 5
                duration = task.get('duration_minutes') or 0
                buy_count = task.get('buy_count') or 0
                
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
            task_type = task.get('task_type', 0)
            
            if task_type >= 2:
                # 系统同步任务停止通知
                content = (
                    f"🏁 系统同步完成\n"
                    f"------------------\n"
                    f"任务名称: {task_name}\n"
                    f"执行结果: {reason}\n"
                    f"完成时间: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
                )
            else:
                # 普通扫货任务停止通知
                success_count = task.get('success_count', 0)
                buy_count = task.get('buy_count') or 0
                
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
