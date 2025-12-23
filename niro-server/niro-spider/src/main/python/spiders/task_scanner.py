import sys
import os
import time
from decimal import Decimal

# Add src/main/python to sys.path
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir) # src/main
src_dir = os.path.dirname(parent_dir) # src
# We need to add src/main/python to path. 
# If this file is in src/main/python/spiders, then...
# Wait, this file will be in src/main/python/spiders/
# But I prefer to import it from src/main/python/run_scanner.py which sets the path.
# So here I assume standard imports work if path is set correctly.

from spiders.buff_spider import BuffSpider
from storage.postgres_pool import PostgresPool
from utils.logger import get_logger

logger = get_logger(__name__)

class TaskScanner:
    def __init__(self):
        self.spider = BuffSpider()
        self.pg_pool = PostgresPool()

    def run(self):
        """
        主循环：获取任务 -> 扫描 -> 下单
        """
        logger.info("🚀 启动扫货任务扫描器...")
        while True:
            try:
                tasks = self.fetch_active_tasks()
                if not tasks:
                    logger.info("当前无运行中任务，休眠 5 秒...")
                    time.sleep(5)
                    continue

                logger.info(f"发现 {len(tasks)} 个运行中任务，开始扫描...")
                for task in tasks:
                    self.process_task(task)
                    # 避免请求过快，每个任务间隔 1-3 秒
                    time.sleep(2)

            except Exception as e:
                logger.error(f"扫描循环异常: {e}", exc_info=True)
                time.sleep(5)

    def fetch_active_tasks(self):
        """从数据库获取状态为 1 (运行中) 的任务"""
        with self.pg_pool.get_cursor() as cur:
            cur.execute("""
                SELECT id, goods_id, max_price, min_paintwear, max_paintwear, buy_count, success_count
                FROM buff_scan_task
                WHERE status = 1
            """)
            return cur.fetchall()

    def process_task(self, task):
        """处理单个任务"""
        goods_id = task['goods_id']
        task_id = task['id']
        logger.info(f"正在扫描任务 [ID:{task_id}] GoodsID:{goods_id}...")

        # 1. 获取在售列表
        # 注意：get_goods_list 可能会失败返回 []，日志在 Spider 中已记录
        items = self.spider.get_goods_list(goods_id)
        if not items:
            return

        # 2. 筛选满足条件的商品
        target_item = None
        for item in items:
            try:
                price_str = item.get('price_buff')
                paintwear_str = item.get('paintwear')
                
                if not price_str:
                    continue

                price = Decimal(price_str)
                # 如果没有磨损值（如箱子、钥匙），默认为 -1，方便后续逻辑判断
                # 但本系统主要针对饰品，通常都有磨损。如果数据库存了磨损范围，说明用户在意磨损。
                paintwear = Decimal(paintwear_str) if paintwear_str else Decimal('-1')

                # 2.1 价格判断
                if task['max_price'] is not None and price > task['max_price']:
                    continue
                
                # 2.2 磨损判断
                # 只有当商品有磨损值(>=0)时才进行磨损筛选
                if paintwear >= 0:
                    if task['min_paintwear'] is not None and paintwear < task['min_paintwear']:
                        continue
                    if task['max_paintwear'] is not None and paintwear > task['max_paintwear']:
                        continue
                else:
                    # 商品无磨损值，但任务设置了磨损要求，则跳过
                    if task['min_paintwear'] is not None or task['max_paintwear'] is not None:
                        continue

                # 找到符合条件的商品
                target_item = item
                logger.info(f"✅ 发现目标商品! 价格:{price}, 磨损:{paintwear}, ID:{item['id']}")
                break # 贪心策略：列表通常按价格升序，找到的第一个就是最低价

            except Exception as e:
                logger.warning(f"处理商品数据异常: {e}, item: {item}")
                continue

        # 3. 执行购买 (模拟)
        if target_item:
            self.buy_goods(task, target_item)

    def buy_goods(self, task, item):
        """执行购买逻辑"""
        logger.info(f"💰 [发起购买] 任务ID:{task['id']} 商品ID:{item['goods_id']} ItemID:{item['id']} 价格:{item['price_buff']}")
        
        # 调用 Spider 的 buy 方法
        # 注意：task['goods_id'] 是 Buff 的 goods_id (如 33852)
        # item['id'] 是具体的 sell_order_id (如 3915935432-...)
        result = self.spider.buy(task['goods_id'], item['id'], item['price_buff'])
        
        if result:
            logger.info(f"✅ 购买成功！更新任务进度... 订单ID: {result.get('id')}")
            self.update_task_progress(task['id'])
        else:
            logger.error("❌ 购买失败")

    def update_task_progress(self, task_id):
        """更新任务成功数量，如果达到目标则停止任务"""
        try:
            with self.pg_pool.get_conn() as conn:
                with conn.cursor() as cur:
                    # 增加成功计数
                    cur.execute("""
                        UPDATE buff_scan_task
                        SET success_count = success_count + 1,
                            update_time = NOW()
                        WHERE id = %s
                        RETURNING success_count, buy_count
                    """, (task_id,))
                    row = cur.fetchone()
                    if row:
                        success_count = row[0]
                        buy_count = row[1]
                        
                        # 检查是否完成
                        if buy_count > 0 and success_count >= buy_count:
                            cur.execute("""
                                UPDATE buff_scan_task
                                SET status = 2, update_time = NOW()
                                WHERE id = %s
                            """, (task_id,))
                            logger.info(f"🎉 任务 [ID:{task_id}] 已完成所有购买目标，自动停止。")
                conn.commit()
        except Exception as e:
            logger.error(f"更新任务进度失败: {e}")

