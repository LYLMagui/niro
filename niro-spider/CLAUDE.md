# niro-spider CLAUDE.md

> Niro 项目爬虫模块专用规范
> 版本：1.0.0 | 更新日期：2026-02-24

---

## 1. 项目概述

### 1.1 核心定位
niro-spider 是 Niro 项目的爬虫执行模块，基于 Python asyncio 构建，负责从 Buff/C5 等平台抓取饰品数据、执行自动交易任务。

### 1.2 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Python | 3.10+ | 运行环境 |
| 异步框架 | asyncio | 内置 | 异步编程 |
| HTTP 客户端 | httpx | 2.4.0 | 网络请求 |
| 消息队列 | Redis | 8.0 | 任务队列 |
| 解析库 | lxml/BeautifulSoup | - | HTML 解析 |
| 浏览器 | Playwright | - | 动态渲染 |
| 测试 | pytest | - | 单元测试 |
| 构建 | Poetry | - | 依赖管理 |

### 1.3 核心依赖
```
asyncio
httpx
redis
lxml
beautifulsoup4
playwright
pydantic
pytest
```

---

## 2. 项目结构

```
niro-spider/
├── config/                     # 配置入口
│   ├── __init__.py
│   ├── constants.py           # 常量定义
│   └── settings.py            # 配置管理（所有配置必须在这里）
│
├── dto/                        # 数据传输对象
│   ├── __init__.py
│   ├── buff_dto.py            # Buff 相关 DTO
│   └── task_dto.py            # 任务相关 DTO
│
├── engine/                     # 爬虫引擎
│   ├── task_consumer.py        # 任务消费者（Redis BLPOP）
│   ├── sharded_executor.py    # 分片执行器
│   └── context.py             # 执行上下文
│
├── spiders/                    # 爬虫实现
│   ├── buff_spider.py         # Buff 爬虫
│   ├── async_buff_spider.py   # 异步 Buff 爬虫
│   ├── buff_sticker_spider.py # Buff 贴纸爬虫
│   └── get_buff_goods.py      # 商品数据爬取
│
├── storage/                    # 存储层
│   ├── redis_pool.py          # Redis 连接池
│   └── redis_storage.py       # Redis 存储封装
│
├── enums/                      # 枚举定义
│   ├── buff_enums.py          # Buff 相关枚举
│   └── task_enums.py          # 任务枚举
│
├── utils/                      # 工具函数
│   ├── logger.py              # 日志封装
│   ├── network_util.py        # 网络工具
│   ├── cookie_util.py         # Cookie 工具
│   ├── browser_helper.py      # 浏览器辅助
│   ├── proxy_helper.py        # 代理辅助
│   ├── proxy_checker.py       # 代理检查
│   ├── exception_handler.py   # 异常处理
│   ├── premium_calculator.py  # 溢价计算
│   └── notifier.py            # 通知工具
│
├── tests/                      # 测试用例
│   ├── test_c5_response.py    # C5 响应测试
│   ├── test_c5_batch_buy_response.py  # C5 批量购买测试
│   └── ...
│
├── main.py                     # 入口文件
└── requirements.txt            # 依赖清单
```

---

## 3. 开发规范

### 3.1 强制规范

#### 3.1.1 消息驱动模型（强制）
- **必须**保持 Redis 消息驱动模型
- **禁止**回退到数据库轮询模式

```python
# ✅ 正确：Redis 阻塞监听
async def consume_tasks():
    while True:
        # BLPOP 阻塞获取
        task = await redis.blpop("niro:task:queue:buff")
        if task:
            await process_task(json.loads(task))

# ❌ 错误：数据库轮询
async def poll_database():
    while True:
        tasks = db.query("SELECT * FROM tasks WHERE status=0")
        for task in tasks:
            await process_task(task)
        await asyncio.sleep(5)  # 轮询间隔
```

#### 3.1.2 配置集中管理（强制）
- **所有配置**必须在 `config/settings.py` 中定义
- **禁止**在代码中散落读取环境变量

```python
# ✅ 正确：config/settings.py
class Settings:
    BUFF_API_BASE = os.getenv("BUFF_API_BASE", "https://buff.163.com")
    C5_API_BASE = os.getenv("C5_API_BASE", "https://api.c5game.com")

# 业务代码中使用
from config.settings import Settings
url = f"{Settings.BUFF_API_BASE}/api/market/buy"

# ❌ 错误：散落读取环境变量
url = os.getenv("BUFF_API_BASE") + "/api/market/buy"
```

#### 3.1.3 协程管理（强制）
- 关键协程**必须**支持取消、退出、心跳

```python
async def run_task(task_id: str):
    try:
        # 定期检查取消状态
        while not asyncio.current_task().cancelled():
            result = await fetch_data()
            if result:
                await send_result(result)
            await asyncio.sleep(5)  # 心跳间隔
    except asyncio.CancelledError:
        logger.info(f"任务 {task_id} 被取消")
        raise
    except Exception as e:
        logger.error(f"任务失败: {e}")
```

### 3.2 日志规范

```python
from utils.logger import get_logger

logger = get_logger(__name__)

# 正确使用
logger.info(f"开始执行任务: {task_id}")
logger.debug(f"请求参数: {params}")
logger.warning(f"代理失效: {proxy}")
logger.error(f"请求失败: {error}", exc_info=True)
```

### 3.3 异常处理

```python
from utils.exception_handler import handle_exception

async def fetch_data():
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(url)
            response.raise_for_status()
            return response.json()
    except httpx.TimeoutException as e:
        logger.error(f"请求超时: {e}")
        raise
    except httpx.HTTPStatusError as e:
        logger.error(f"HTTP 错误: {e.response.status_code}")
        handle_exception(e)  # 统一处理
        raise
```

---

## 4. 核心模块

### 4.1 任务消费者

```python
# engine/task_consumer.py
class TaskConsumer:
    """任务消费者 - Redis BLPOP 驱动"""

    async def start(self):
        """启动消费者"""
        logger.info("任务消费者启动")
        while True:
            try:
                # 阻塞获取任务
                task_data = await self.redis.blpop(self.queue_key)
                if task_data:
                    task = json.loads(task_data[1])
                    await self.execute_task(task)
            except Exception as e:
                logger.error(f"消费异常: {e}")
                await asyncio.sleep(5)  # 异常后等待

    async def execute_task(self, task: dict):
        """执行任务"""
        task_type = task.get("type")
        if task_type == "scan":
            await self.scan_goods(task)
        elif task_type == "buy":
            await self.buy_goods(task)
        # ...
```

### 4.2 分片执行器

```python
# engine/sharded_executor.py
class ShardedExecutor:
    """分片执行器 - 支持多线程池"""

    def __init__(self, shard_count: int = 4):
        self.shard_count = shard_count
        self.executor = ThreadPoolExecutor(max_workers=shard_count)

    async def execute_shards(self, tasks: list):
        """分片执行任务"""
        # 将任务分成多个分片
        shards = self.split_tasks(tasks, self.shard_count)

        # 并行执行各分片
        futures = []
        for shard in shards:
            future = self.executor.submit(self.process_shard, shard)
            futures.append(future)

        # 等待所有分片完成
        results = await asyncio.gather(*[asyncio.wrap_future(f) for f in futures])
        return results
```

### 4.3 Redis 存储

```python
# storage/redis_storage.py
class RedisStorage:
    """Redis 存储封装"""

    def __init__(self, redis_url: str):
        self.redis = redis.from_url(redis_url)

    async def push_task(self, queue: str, task: dict):
        """推送任务到队列"""
        await self.redis.rpush(queue, json.dumps(task))

    async def pop_task(self, queue: str, timeout: int = 0):
        """弹出任务（阻塞）"""
        result = await self.redis.blpop(queue, timeout=timeout)
        if result:
            return json.loads(result[1])
        return None

    async def set_with_ttl(self, key: str, value: any, ttl: int):
        """设置带过期时间的键值"""
        await self.redis.setex(key, ttl, json.dumps(value))

    async def acquire_lock(self, lock_key: str, request_id: str, ttl: int = 30) -> bool:
        """获取分布式锁"""
        return await self.redis.set(lock_key, request_id, nx=True, ex=ttl)
```

---

## 5. 爬虫实现

### 5.1 Buff 爬虫

```python
# spiders/buff_spider.py
class BuffSpider:
    """Buff 平台爬虫"""

    BASE_URL = "https://buff.163.com"

    async def get_goods_list(self, category_id: int, page: int = 1) -> list:
        """获取商品列表"""
        url = f"{self.BASE_URL}/api/market/goods"
        params = {
            "category_id": category_id,
            "page_num": page,
            "page_size": 20
        }

        headers = await self.get_headers()
        async with httpx.AsyncClient() as client:
            response = await client.get(url, params=params, headers=headers)
            response.raise_for_status()
            data = response.json()

        return data.get("data", [])

    async def get_goods_detail(self, goods_id: str) -> dict:
        """获取商品详情"""
        url = f"{self.BASE_URL}/api/market/goods/detail"
        params = {"id": goods_id}

        async with httpx.AsyncClient() as client:
            response = await client.get(url, params=params)
            return response.json()
```

### 5.2 异步爬虫

```python
# spiders/async_buff_spider.py
class AsyncBuffSpider:
    """异步 Buff 爬虫 - 高并发版本"""

    def __init__(self, max_concurrent: int = 10):
        self.semaphore = asyncio.Semaphore(max_concurrent)

    async def batch_fetch(self, goods_ids: list) -> list:
        """批量获取商品信息"""
        async with self.semaphore:
            tasks = [self.fetch_goods(gid) for gid in goods_ids]
            results = await asyncio.gather(*tasks, return_exceptions=True)
            return [r for r in results if not isinstance(r, Exception)]

    async def fetch_goods(self, goods_id: str) -> dict:
        """获取单个商品"""
        # 实现细节
        pass
```

---

## 6. 工具函数

### 6.1 网络工具

```python
# utils/network_util.py
class NetworkUtil:
    """网络请求工具"""

    @staticmethod
    async def get(url: str, **kwargs) -> httpx.Response:
        """GET 请求"""
        async with httpx.AsyncClient(timeout=30.0) as client:
            return await client.get(url, **kwargs)

    @staticmethod
    async def post(url: str, **kwargs) -> httpx.Response:
        """POST 请求"""
        async with httpx.AsyncClient(timeout=30.0) as client:
            return await client.post(url, **kwargs)

    @staticmethod
    def build_headers(cookie: str = None) -> dict:
        """构建请求头"""
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept": "application/json",
        }
        if cookie:
            headers["Cookie"] = cookie
        return headers
```

### 6.2 代理辅助

```python
# utils/proxy_helper.py
class ProxyHelper:
    """代理管理辅助"""

    def __init__(self):
        self.proxy_pool = []

    async def get_proxy(self) -> str:
        """获取可用代理"""
        if not self.proxy_pool:
            await self.refresh_pool()
        return self.proxy_pool[0]

    async def validate_proxy(self, proxy: str) -> bool:
        """验证代理可用性"""
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get("http://httpbin.org/ip",
                                          proxies={"http": proxy},
                                          timeout=5.0)
                return response.status_code == 200
        except Exception:
            return False
```

---

## 7. 测试规范

### 7.1 测试框架

```python
# tests/test_c5_response.py
import pytest
import asyncio

@pytest.mark.asyncio
async def test_c5_response():
    """测试 C5 响应解析"""
    from spiders.c5_spider import C5Spider

    spider = C5Spider()
    response_data = {...}  # 模拟响应

    result = spider.parse_response(response_data)

    assert result is not None
    assert result["order_id"] == "expected_order_id"
```

### 7.2 运行测试

```bash
# 运行所有测试
cd niro-spider
pytest -q tests/

# 运行单个测试
pytest -q tests/test_c5_response.py
pytest -q tests/test_c5_response.py::test_c5_response
```

---

## 8. 常用命令

### 8.1 启动爬虫

```bash
# 基本启动（Redis 消息驱动）
cd niro-spider
python main.py

# 指定队列
python main.py --queue niro:task:queue:buff

# 指定日志级别
python main.py --log-level DEBUG
```

### 8.2 Redis 操作

```bash
# 查看任务队列
redis-cli LLEN niro:task:queue:buff

# 手动添加任务
redis-cli RPUSH niro:task:queue:buff '{"type":"scan","category_id":1}'

# 清空队列
redis-cli DEL niro:task:queue:buff
```

### 8.3 测试命令

```bash
# 运行测试
pytest -q tests/test_c5_response.py

# 运行测试（详细输出）
pytest -v tests/

# 运行测试（覆盖报告）
pytest --cov=. tests/
```

---

## 9. 业务场景

### 9.1 商品数据抓取

```python
# 抓取流程
1. 从 Redis 队列获取任务
2. 解析任务参数（分类ID、价格区间等）
3. 调用 Buff/C5 API 获取数据
4. 数据清洗和转换
5. 存储到 Redis/数据库
6. 发送结果通知
```

### 9.2 自动购买

```python
# 购买流程
1. 监听商品上架消息
2. 检查商品价格是否符合预期
3. 检查账号余额是否充足
4. 调用购买 API
5. 处理响应结果
6. 更新订单状态
7. 发送通知
```

---

## 10. 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 请求超时 | 网络波动/代理失效 | 更换代理、增加重试 |
| IP 被封 | 请求频率过高 | 降低频率、使用代理池 |
| Cookie 失效 | 登录态过期 | 重新登录获取新 Cookie |
| 响应解析失败 | API 变更 | 检查并更新解析逻辑 |
| Redis 连接失败 | Redis 服务异常 | 检查 Redis 配置 |

---

## 11. 参考资料

- [httpx 文档](https://www.python-httpx.org/)
- [asyncio 文档](https://docs.python.org/3/library/asyncio.html)
- [Redis 文档](https://redis.io/documentation)
- [Playwright 文档](https://playwright.dev/python/)

---

**维护者**：Niro 爬虫开发团队
