import pendulum
import pydash
import time
import os
import random
from datetime import datetime, timedelta
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger
from spiders.buff_spider import BuffSpider
from spiders.get_buff_goods import run_goods_sync
from spiders.get_buff_goods_category import run_category_sync
from spiders.buff_sticker_spider import run_sticker_sync
from enums.task_enums import BuffTaskType
from storage.models import BuffScanTask, BuffPriceHistory, BuffScanTaskAccount, BuffAccount
from storage.database import Session
from storage.redis_pool import redis_client
from sqlalchemy import select, update, func, insert
from sqlalchemy.dialects.postgresql import insert as pg_insert
from utils.logger import get_logger, account_name_var, account_id_var, task_id_var
from utils.notifier import notifier
from utils.exception_handler import LoginRequiredError
from dto.task_dto import BuffScanTaskDTO
from dto.buff_dto import BuffStickerInfo
from utils.premium_calculator import PremiumCalculator
from tenacity import retry, stop_after_attempt, wait_fixed, retry_if_exception_type, before_sleep_log

logger = get_logger(__name__)

class TaskScanner:
    def __init__(self):
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
        # 记录任务最后一次运行结束的时间: {task_id: timestamp}
        self.last_finished_tasks = {}

    def get_spider(self, user_id):
        """获取或创建用户的爬虫实例"""
        if user_id not in self.user_spiders:
            logger.info(f"🆕 为用户 {user_id} 创建独立爬虫实例")
            self.user_spiders[user_id] = BuffSpider(user_id=user_id)
        return self.user_spiders[user_id]

    def clean_failed_items(self):
        """清理过期的失败记录"""
        now = pendulum.now().timestamp()
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
        session = Session()
        pid = os.getpid()
        try:
            self.clean_failed_items()

            # 获取所有任务
            tasks = session.query(BuffScanTask).all()

            active_task_ids = set()

            if not tasks:
                if hasattr(self, '_last_task_count') and self._last_task_count > 0:
                    logger.info(f"ℹ️ [PID:{pid}] 数据库中任务已清空")
                self._last_task_count = 0
                return

            # 记录任务总数变化
            task_count = len(tasks)
            if not hasattr(self, '_last_task_count') or self._last_task_count != task_count:
                logger.info(f"🔍 [PID:{pid}] 数据库任务同步: 总计 {task_count} 个任务")
                self._last_task_count = task_count

            for task in tasks:
                task_id = task.id
                status = task.status

                # 如果任务是运行中 (1)
                if status == 1:
                    active_task_ids.add(task_id)
                    # 使用 Pydantic 从 SQLAlchemy 模型转换
                    task_dto = BuffScanTaskDTO.model_validate(task)
                    self.schedule_task(task_dto.model_dump())
                elif status == 4:
                    # 如果任务状态是“执行中”，但当前实例并没有记录它在运行，说明可能卡住了（如进程重启）
                    if task_id not in self.running_tasks:
                        # 增加一个小的宽限期，避免刚完成状态还没更新完就被同步逻辑重置
                        last_finished = getattr(self, 'last_finished_tasks', {})
                        finish_time = last_finished.get(task_id, 0)
                        elapsed = pendulum.now().timestamp() - finish_time
                        if elapsed < 30:
                            logger.info(f"⏳ [PID:{pid}] 任务 [ID:{task_id}] 处于状态 4 且不在队列中，但刚结束不久 ({elapsed:.1f}s)，跳过重置")
                            continue

                        logger.warning(f"⚠️ [PID:{pid}] 任务 [ID:{task_id}] 状态为执行中但未在当前进程运行队列，尝试重置状态...")
                        # 如果有 cron，重置为 1 (待运行)，否则重置为 0 (停止)
                        if task.cron_expression:
                            self.update_task_status(task_id, 1)
                        else:
                            self.update_task_status(task_id, 0)
                    else:
                        # 正在当前进程运行的任务，也计入活跃列表，防止日志统计误判
                        active_task_ids.add(task_id)
                else:
                    # 如果任务不是运行中，确保调度器中已移除
                    self.unschedule_task(task_id)

            # 记录运行中任务的变化
            active_count = len(active_task_ids)
            if not hasattr(self, '_last_active_count') or self._last_active_count != active_count:
                if active_count > 0:
                    logger.info(f"📈 [PID:{pid}] 当前有 {active_count} 个运行中的任务")
                else:
                    logger.info(f"ℹ️ [PID:{pid}] 当前无运行中的任务")
                self._last_active_count = active_count

            # 清理已经不在数据库活跃列表中的配置缓存
            cached_ids = list(self.task_configs.keys())
            for tid in cached_ids:
                if tid not in active_task_ids:
                    del self.task_configs[tid]

        except Exception as e:
            logger.error(f"❌ 同步任务失败: {e}", exc_info=True)
        finally:
            Session.remove()

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
        cron_expr = pydash.get(task, 'cron_expression')
        scan_interval_min = pydash.get(task, 'scan_interval_min', 15)
        scan_interval_max = pydash.get(task, 'scan_interval_max', 20)

        # 构造一个唯一的配置标识，用于检测配置是否变更
        new_config = {
            'cron': cron_expr,
            'interval_min': scan_interval_min,
            'interval_max': scan_interval_max,
            'type': pydash.get(task, 'task_type')
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
                scan_interval = random.randint(scan_interval_min, scan_interval_max)
                logger.info(f"🚀 任务启动 (循环扫描): {task['name']} (ID: {task_id}) [间隔范围: {scan_interval_min}-{scan_interval_max}s, 本次: {scan_interval}s]")
                # 普通任务：开启间隔扫描
                job_id = self.start_scan_job(task)

            self.scheduled_jobs[task_id] = job_id

        self.task_configs[task_id] = new_config

    def trigger_cron_task(self, task_id):
        """Cron 触发器：开始执行扫描任务"""
        logger.info(f"⏰ Cron 触发: 任务 [ID:{task_id}] 开始运行...")
        # 重新从数据库获取最新任务信息
        session = Session()
        try:
            task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()
            if task and task.status == 1:
                task_dto = BuffScanTaskDTO.model_validate(task)
                task_dict = task_dto.model_dump()
                task_type = task_dto.task_type
                if task_type >= 2:
                    # 系统任务：设置为执行中状态并直接执行
                    self.running_tasks[task_id] = True
                    self.update_task_status(task_id, 4)

                    # 系统任务启动，刷新指纹 (如果用户有对应 spider)
                    if task_dto.user_id:
                        self.get_spider(task_dto.user_id).refresh_profile()

                    self.scheduler.add_job(
                        self.run_system_task,
                        args=[task_dict],
                        id=f"system_task_{task_id}_{int(time.time())}"
                    )
                else:
                    # 普通任务：开启间隔扫描
                    job_id = self.start_scan_job(task_dict)
                    # 记录下这个活跃的扫描作业，防止 sync_tasks 认为它没上架
                    self.scheduled_jobs[task_id] = job_id
        except Exception as e:
            logger.error(f"❌ 触发 Cron 任务失败: {e}")
        finally:
            Session.remove()

    def get_task_account_info(self, task_id):
        """获取任务绑定的账号信息 (支持多账号轮询)"""
        if not task_id: return None, "System"
        session = Session()
        try:
            # 获取该任务关联的所有有效账号
            accounts = session.query(BuffAccount).join(
                BuffScanTaskAccount, BuffScanTaskAccount.account_id == BuffAccount.id
            ).filter(BuffScanTaskAccount.task_id == task_id).order_by(BuffAccount.id).all()
            
            if not accounts:
                return None, "System"
            
            if len(accounts) == 1:
                return accounts[0].id, accounts[0].account_name
                
            # 多账号轮询逻辑 (Round Robin)
            redis_key = f"niro:task:account_index:{task_id}"
            current_index = redis_client.get(redis_key)
            current_index = int(current_index) if current_index else 0
            
            # 确保索引不越界 (可能中途删除了账号)
            if current_index >= len(accounts):
                current_index = 0
                
            target_acc = accounts[current_index]
            
            # 更新下一个索引到 Redis
            next_index = (current_index + 1) % len(accounts)
            redis_client.set(redis_key, next_index)
            
            logger.info(f"🔄 任务 [ID:{task_id}] 触发账号轮询: [{current_index+1}/{len(accounts)}] 使用账号: {target_acc.account_name}")
            
            return target_acc.id, target_acc.account_name
        finally: Session.remove()

    def run_system_task(self, task):
        """执行单次系统同步任务 (带自动重试机制)"""
        import uuid
        from utils.logger import trace_id_var
        task_id = task['id']
        task_type = task.get('task_type')
        task_name = task.get('name', '系统同步任务')

        if hasattr(self, 'running_tasks'):
            self.running_tasks[task_id] = True

        # 任务执行链路追踪标识 (非持久化)
        trace_id = str(uuid.uuid4()).replace('-', '')
        token = trace_id_var.set(trace_id)
        task_id_var.set(task_id)
        
        # 设置账号上下文
        acc_id, acc_name = self.get_task_account_info(task_id)
        account_id_var.set(acc_id)
        account_name_var.set(acc_name)
        
        try:
            task_logger = logger.bind(
                task_id=task_id, 
                task_name=task_name,
                traceId=trace_id,
                accountName=acc_name
            )
            task_logger.info(f"🚀 开始执行系统任务 [ID:{task_id}]: {task_name}")
            self.send_task_start_notification(task)

            # 使用内部带重试的方法执行核心逻辑
            self._execute_system_task_with_retry(task_id, task_type)

            # 执行成功后的处理
            is_running = task_id in self.running_tasks if hasattr(self, 'running_tasks') else True
            if is_running:
                task_logger.info(f"✅ 系统任务执行完成 [ID:{task_id}]")
                cron_expr = task.get('cron_expression')
                if cron_expr:
                    self.update_task_status(task_id, 1)
                else:
                    self.stop_task_in_db(task_id)
            else:
                task_logger.warning(f"⚠️ 系统任务 [ID:{task_id}] 在运行过程中已被停止")

        except LoginRequiredError:
            task_logger.critical(f"🛑 系统任务 [ID:{task_id}] 登录失效且重试耗尽，正在停止任务...")
            self.set_task_error(task_id)
            self.send_task_stop_notification(task, "Cookie 连续失效，请更新全局 BUFF_COOKIE")
        except Exception as e:
            task_logger.error(f"🛑 系统任务 [ID:{task_id}] 执行异常且重试耗尽: {e}")
            self.set_task_error(task_id)
            self.send_task_stop_notification(task, f"同步失败: {e}")
        finally:
            trace_id_var.reset(token)
            self.last_finished_tasks[task_id] = time.time()
            if hasattr(self, 'running_tasks') and task_id in self.running_tasks:
                del self.running_tasks[task_id]
            if not task.get('cron_expression') and task_id in self.scheduled_jobs:
                del self.scheduled_jobs[task_id]

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_fixed(5),
        retry=(retry_if_exception_type(Exception) | retry_if_exception_type(LoginRequiredError)),
        before_sleep=before_sleep_log(logger, "WARNING"),
        reraise=True
    )
    def _execute_system_task_with_retry(self, task_id, task_type):
        """核心系统任务逻辑，由 tenacity 负责重试"""
        session = Session()
        try:
            task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()
            if not task:
                logger.error(f"❌ 任务 [ID:{task_id}] 已不存在")
                return

            if task_type == BuffTaskType.SYNC_CATEGORY:
                run_category_sync(task_id=task_id)
            elif task_type == BuffTaskType.SYNC_GOODS:
                run_goods_sync(force=False, task_id=task_id)
            elif task_type == BuffTaskType.SYNC_STICKER:
                run_sticker_sync(user_id=task.user_id if task.user_id else 1, task_id=task_id)
            elif task_type == BuffTaskType.SYNC_CATEGORY_GOODS:
                # 借用 goods_id 字段作为 category_id
                category_id = task.goods_id
                if not category_id:
                    logger.error(f"❌ 分类同步任务 [ID:{task_id}] 缺失分类 ID")
                    return
                run_goods_sync(force=False, task_id=task_id, category_id=category_id)
        finally:
            Session.remove()

    def start_scan_job(self, task):
        """开始间隔扫描作业"""
        task_id = task['id']
        user_id = task.get('user_id')
        scan_interval_min = task.get('scan_interval_min', 15)
        scan_interval_max = task.get('scan_interval_max', 20)
        scan_interval = random.randint(scan_interval_min, scan_interval_max)
        job_id = f"active_scan_{task_id}"

        # 任务启动，刷新指纹
        if user_id:
            self.get_spider(user_id).refresh_profile()

        # 记录启动时间用于持续时间判断
        self.task_start_times[task_id] = pendulum.now()

        self.scheduler.add_job(
            self.run_scan_cycle,
            trigger=IntervalTrigger(seconds=scan_interval),
            args=[task_id],
            id=job_id,
            replace_existing=True
        )
        logger.info(f"✅ 激活间隔扫描 [ID:{task_id}] 范围: {scan_interval_min}-{scan_interval_max}s, 本次: {scan_interval}s")

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

        session = Session()
        try:
            # 1. 获取最新任务状态
            task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()

            if not task or task.status != 1:
                self.unschedule_task(task_id)
                return

            # 使用 Pydantic 从 SQLAlchemy 模型转换
            task_dto = BuffScanTaskDTO.model_validate(task)
            task_dict = task_dto.model_dump()
            user_id = task_dict.get('user_id')

            # 为当前扫描循环绑定上下文，方便 ELK 检索
            acc_id, acc_name = self.get_task_account_info(task_id)
            account_id_var.set(acc_id)
            account_name_var.set(acc_name)

            task_logger = logger.bind(
                task_id=task_id, 
                user_id=user_id, 
                task_name=task_dict.get('name'),
                traceId=task_dict.get('trace_id', ''),
                accountName=acc_name
            )

            # 2. 持续时间检查
            duration = pydash.get(task_dict, 'duration_minutes', 0)
            if duration > 0 and task_id in self.task_start_times:
                start_time = self.task_start_times[task_id]
                if pendulum.now() > start_time.add(minutes=duration):
                    task_logger.info(f"⏱️ 任务运行时间已达 {duration} 分钟，自动停止。")
                    self.stop_task_in_db(task_id)
                    self.send_task_stop_notification(task_dict, "运行时间到期")
                    self.unschedule_task(task_id)
                    return

            # 3. 待支付锁检查
            user_id = task_dict['user_id']
            if self.user_pending_locks.get(user_id):
                # 尝试通过爬虫检查是否还有未支付订单
                spider = self.get_spider(user_id)
                if self.check_user_pending_orders(spider):
                    task_logger.debug(f"⏳ 用户仍有未支付订单，跳过本次扫描...")
                    return
                else:
                    task_logger.info(f"🔓 用户未支付订单已处理，释放保护锁")
                    self.user_pending_locks[user_id] = False

            # 4. 执行业务处理
            spider = self.get_spider(user_id)
            spider.refresh_cookie()
            self.process_task(task_dict, spider)

        except LoginRequiredError:
            task_logger.error(f"🔑 [任务:{task_id}] Cookie 已失效或未登录，正在停止任务...")
            self.stop_task_in_db(task_id)
            self.send_task_stop_notification(task_dict, "Cookie 已失效，请重新登录并更新 Cookie")
            self.unschedule_task(task_id)
        except Exception as e:
            task_logger.error(f"❌ 扫描循环异常: {e}", exc_info=True)
        finally:
            self.last_finished_tasks[task_id] = pendulum.now().timestamp()
            if task_id in self.running_tasks:
                del self.running_tasks[task_id]
            Session.remove()

    def check_user_pending_orders(self, spider):
        """检查用户是否有待支付订单 (通过调用 API)"""
        # 这里的实现取决于 BuffSpider 是否有检查订单的方法
        # 暂时简单处理：返回 True（即默认锁定，靠外部逻辑或超时释放）
        # 或者调用 spider.get_buy_order_history() 检查是否有 "待支付" 状态
        return self.user_pending_locks.get(spider.user_id, False)

    def update_task_status(self, task_id, status):
        """更新数据库任务状态"""
        session = Session()
        try:
            session.query(BuffScanTask).filter(BuffScanTask.id == task_id).update({"status": status})
            session.commit()
        except Exception as e:
            session.rollback()
            logger.error(f"更新任务状态失败: {e}")
        # 注意：不要在这里调用 Session.remove()，由调用方负责生命周期控制

    def stop_task_in_db(self, task_id):
        """更新数据库任务状态为停止(0)"""
        self.update_task_status(task_id, 0)

    def set_task_error(self, task_id):
        """更新数据库任务状态为异常(3)"""
        self.update_task_status(task_id, 3)

    def process_task(self, task, spider):
        """处理单个扫描任务"""
        import uuid
        from utils.logger import trace_id_var
        task_id = task.get('id')
        task_name = task.get('name')
        task_type = task.get('task_type', 0)
        goods_id = task['goods_id']
        
        # 任务执行链路追踪标识 (非持久化)
        trace_id = str(uuid.uuid4()).replace('-', '')
        token = trace_id_var.set(trace_id)

        try:
            # 获取当前出口 IP 并创建带 IP 和 TraceID 的 logger
            acc_id, acc_name = self.get_task_account_info(task_id)
            account_id_var.set(acc_id)
            account_name_var.set(acc_name)

            task_logger = logger.bind(
                task_id=task_id, 
                traceId=trace_id,
                accountName=acc_name
            )
            
            type_text = "炼金扫货" if task_type == BuffTaskType.SNIPING else "站内倒卖"
            task_logger.info(f"🔍 [任务:{task_id}] 正在执行任务 | 类型:{type_text}")

            # 获取饰品列表
            items = spider.get_goods_list(goods_id)
            if not items:
                task_logger.warning(f"⚠️ [任务:{task_id}] 未获取到商品列表，跳过本次循环")
                return

            # 记录价格历史 (取第一项的元数据即可)
            self.record_price_history(items[0])

            match_found = False
            if task_type == BuffTaskType.FLIPPING:
                # 站内倒卖模式
                match_found = self.process_flipping_logic(task, items, spider, task_logger)
            elif task_type == BuffTaskType.SNIPING:
                # 炼金扫货模式
                match_found = self.process_sniping_logic(task, items, spider, task_logger)

            if not match_found:
                task_logger.info(f"😴 [任务:{task_id}] 本轮未发现符合条件的商品")

            scan_interval_min = task.get('scan_interval_min', 15)
            scan_interval_max = task.get('scan_interval_max', 20)
            scan_interval = random.randint(scan_interval_min, scan_interval_max)
            task_logger.info(f"💤 [任务:{task_id}] 扫描完成，休眠 {scan_interval}s 后开始下轮...")
        finally:
            trace_id_var.reset(token)

    def record_price_history(self, item):
        """记录价格历史"""
        session = Session()
        try:
            goods_id = pydash.get(item, 'goods_id')
            price = pydash.get(item, 'sell_min_price')
            buy_max_price = pydash.get(item, 'buy_max_price')
            sell_num = pydash.get(item, 'sell_num')

            if not goods_id or price is None:
                return

            # 使用 PostgreSQL 的 ON CONFLICT DO NOTHING
            stmt = pg_insert(BuffPriceHistory).values(
                goods_id=goods_id,
                price=price,
                buy_max_price=buy_max_price,
                sell_num=sell_num
            ).on_conflict_do_nothing(index_elements=['goods_id', 'create_time'])

            session.execute(stmt)
            session.commit()
        except Exception as e:
            session.rollback()
            logger.error(f"记录价格历史异常: {e}")
        finally:
            Session.remove()

    def get_avg_price_24h(self, goods_id):
        """获取 24 小时内的平均价"""
        session = Session()
        try:
            res = session.query(func.avg(BuffPriceHistory.price)).filter(
                BuffPriceHistory.goods_id == goods_id,
                BuffPriceHistory.create_time > func.now() - datetime.timedelta(hours=24)
            ).scalar()
            return float(res) if res else None
        except Exception as e:
            logger.error(f"查询历史均价异常: {e}")
            return None
        finally:
            Session.remove()

    def process_sniping_logic(self, task, items, spider, task_logger=None):
        """处理炼金扫货逻辑 (严格匹配价格和磨损 + 印花捡漏)"""
        if task_logger is None:
            task_logger = logger

        task_id = task.get('id')
        task_name = task.get('name')
        goods_id = task['goods_id']
        max_price = task['max_price']
        min_wear = task['min_paintwear']
        max_wear = task['max_paintwear']

        task_logger.info(f"🎯 [扫货规则] | 商品ID:{goods_id} | 商品名:{task_name} | 目标价:≤{max_price} | 磨损范围:[{min_wear or 0}, {max_wear or 1}] | 候选项目:{len(items)}")

        # 印花捡漏开关，默认开启
        enable_sticker_sniping = True
        match_found = False

        for item in items:
            try:
                if item.get('id') in self.failed_items:
                    continue

                price_str = item.get('price_buff')
                paintwear_str = item.get('paintwear')
                
                if not price_str:
                    continue
                    
                price = float(price_str)
                # 兼容无磨损字段的物品 (如箱子、胶囊)
                paintwear = float(paintwear_str) if paintwear_str else None
                
                # 1. 基础炼金校验
                is_basic_match = True
                # 校验价格
                if price > float(max_price):
                    is_basic_match = False
                
                # 校验磨损 (仅当物品有磨损且任务设置了磨损要求时校验)
                if paintwear is not None:
                    if min_wear is not None and paintwear < float(min_wear):
                        is_basic_match = False
                    if max_wear is not None and paintwear > float(max_wear):
                        is_basic_match = False
                elif (min_wear is not None and float(min_wear) > 0) or (max_wear is not None and float(max_wear) < 1.0):
                    # 如果物品没磨损，但任务却设置了非默认的磨损范围，则认为不匹配
                    is_basic_match = False

                if is_basic_match:
                    task_logger.info(f"🎯 [炼金发现] 价格:{price}, 磨损:{paintwear}, ID:{item['id']}")
                    self.buy_goods(task, item, spider)
                    match_found = True
                    break

                # 2. 如果基础炼金不匹配，尝试印花捡漏逻辑
                if enable_sticker_sniping:
                    stickers_raw = item.get('stickers', [])
                    if stickers_raw:
                        # stickers_raw 已经是 dict 列表 (来自 ParsedBuffItemDTO.model_dump)
                        stickers = [BuffStickerInfo(**s) for s in stickers_raw]
                        # 获取该物品的底价用于溢价评估
                        market_floor = float(item.get('sell_min_price') or 0)
                        if market_floor > 0:
                            # 评估该物品的印花溢价
                            is_premium_match, reason = PremiumCalculator.evaluate_item_premium(
                                current_price=price,
                                market_floor=market_floor,
                                stickers=stickers
                            )

                            if is_premium_match:
                                task_logger.info(
                                    f"✨ [印花捡漏] {reason}, 价格:{price}, 底价:{market_floor}, ID:{item['id']}"
                                )
                                # 记录捡漏理由
                                item['_leak_reason'] = reason
                                self.buy_goods(task, item, spider)
                                match_found = True
                                break

            except Exception as e:
                task_logger.error(f"处理炼金商品项异常: {e}")

        return match_found

    def process_flipping_logic(self, task, items, spider, task_logger=None):
        """处理站内倒卖逻辑 (计算利润 + 均价风控)"""
        if task_logger is None:
            task_logger = logger

        task_id = task.get('id')
        task_name = task.get('name')
        goods_id = task['goods_id']
        min_profit_config = float(task.get('min_profit') or 0)

        # 获取 24 小时均价用于风控
        avg_price_24h = self.get_avg_price_24h(goods_id)
        avg_price_text = f"¥{avg_price_24h:.2f}" if avg_price_24h else "暂无"

        task_logger.info(f"📈 [盈利标准] | 商品ID:{goods_id} | 商品名:{task_name} | 目标利润:≥{min_profit_config} | 24h均价:{avg_price_text} | 候选项目:{len(items)}")
        match_found = False

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
                # 如果当前市场底价远高于 24h 均价（例如超过 1.15 倍），说明可能有人在拉高价格钓鱼，跳过
                if avg_price_24h and market_floor > avg_price_24h * 1.15:
                    task_logger.warning(f"⚠️ [风控] 商品 {goods_id} 当前底价 {market_floor} 远高于 24h 均价 {avg_price_24h:.2f}，疑似价格操纵，跳过。")
                    continue
                # --- 风控逻辑 END ---

                # 计算利润 = (市场最低价 * 0.975) - 当前价格
                # 0.975 是扣除 2.5% 手续费后的比例
                estimated_profit = (market_floor * 0.975) - current_price

                if estimated_profit >= min_profit_config:
                    task_logger.info(f"💰 [倒卖发现] 当前价:{current_price}, 最低价:{market_floor}, 预计利润:¥{estimated_profit:.2f}, ID:{item['id']}")
                    self.buy_goods(task, item, spider, task_logger)
                    match_found = True
                    break

            except Exception as e:
                task_logger.error(f"处理倒卖商品项异常: {e}")

        return match_found

    def buy_goods(self, task, item, spider, task_logger=None):
        """执行购买逻辑 (已切换为测试模式，仅发送通知不真实下单)"""
        if task_logger is None:
            task_logger = logger

        user_id = task['user_id']

        # --- 模拟测试模式 START (不调用真实 API) ---
        task_logger.info(f"🧪 [测试模式] 模拟下单成功: {item['name']} (ID:{item['id']})")
        result = {
            "code": "OK",
            "data": {
                "id": f"MOCK_ORDER_{int(time.time())}",
                "state_text": "支付成功 (模拟测试)",
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

            self.update_task_progress(task['id'], task_logger)
            self.send_buy_notification(task, item, order_data)
        else:
            self.failed_items[item['id']] = time.time()

    def update_task_progress(self, task_id, task_logger=None):
        """更新任务进度"""
        if task_logger is None:
            task_logger = logger

        session = Session()
        try:
            task = session.query(BuffScanTask).filter(BuffScanTask.id == task_id).first()
            if not task:
                return

            task.success_count += 1
            session.commit()

            if task.success_count >= task.buy_count:
                task.status = 2
                session.commit()
                task_logger.info(f"🏁 任务 [ID:{task_id}] 已达到购买上限，自动停止。")

                # 获取完整任务信息用于通知
                task_dict = {
                    'id': task.id,
                    'name': task.name,
                    'status': task.status,
                    'user_id': task.user_id,
                    'success_count': task.success_count,
                    'buy_count': task.buy_count
                }
                self.send_task_stop_notification(task_dict, "达到购买上限")
                self.unschedule_task(task_id)
        except Exception as e:
            session.rollback()
            task_logger.error(f"更新任务进度异常: {e}")
        finally:
            Session.remove()

    def send_task_start_notification(self, task):
        """发送任务启动通知"""
        try:
            task_name = task.get('name', '未知任务')
            task_type = task.get('task_type', 0)

            start_time = datetime.now()
            start_time_str = start_time.strftime("%Y-%m-%d %H:%M:%S")

            if BuffTaskType.is_system_task(task_type):
                # 系统同步任务通知
                task_type_text = '分类同步'
                if task_type == BuffTaskType.SYNC_GOODS:
                    task_type_text = '商品同步'
                elif task_type == BuffTaskType.SYNC_STICKER:
                    task_type_text = '印花同步'

                content = (
                    f"🚀 系统同步启动\n"
                    f"------------------\n"
                    f"任务名称: {task_name}\n"
                    f"启动时间: {start_time_str}\n"
                    f"任务类型: {task_type_text}"
                )
            else:
                # 普通扫货任务通知
                scan_interval = task.get('scan_interval') or 15
                duration = task.get('duration_minutes') or 0
                buy_count = task.get('buy_count') or 0

                end_time_str = "无限制"
                if duration > 0:
                    end_time = start_time + timedelta(minutes=duration)
                    end_time_str = end_time.strftime("%Y-%m-%d %H:%M:%S")

                scan_interval_min = task.get('scan_interval_min', 15)
                scan_interval_max = task.get('scan_interval_max', 20)

                content = (
                    f"🚀 任务启动通知\n"
                    f"------------------\n"
                    f"任务名称: {task_name}\n"
                    f"启动时间: {start_time_str}\n"
                    f"预计结束: {end_time_str}\n"
                    f"扫描间隔: {scan_interval_min}-{scan_interval_max}s\n"
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
                    f"完成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
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
            leak_reason = item.get('_leak_reason')

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

            if leak_reason:
                content += f"\n捡漏理由: {leak_reason}"

            notifier.send_text(
                content=content,
                user_id=task.get('user_id')
            )
        except Exception as e:
            logger.error(f"发送购买通知失败: {e}")
