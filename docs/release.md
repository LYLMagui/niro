# Release Notes
  
  
## v1.2.0 (2025-12-27)
### 架构重构与用户隔离 (重大更新)
- **多用户爬虫实例隔离 (User-Level Isolation)**：
  - **隔离式爬虫引擎**：重构 `BuffSpider` 类，支持按 `user_id` 初始化。每个用户拥有独立的 Cookie 空间、请求头配置和风控状态，彻底解决多用户混用 Cookie 的安全隐患。
  - **用户实例池管理**：`TaskScanner` 引入 `user_spiders` 实例池，为每个活跃用户按需创建并复用爬虫实例，实现任务执行层面的物理隔离。
  - **待支付锁隔离**：引入用户级 `user_pending_locks`，当某个用户产生待支付订单时，仅暂停该用户的扫描任务，不影响其他用户的扫货流程。
- **通知系统深度定制**：
  - **用户级通知配置**：重构 `notifier.py` 模块，支持从数据库动态加载每个用户独立的企业微信推送参数（CorpID, Secret, AgentID, ToUser）。
  - **自动回退机制**：若用户未配置私有通知参数，系统将自动回退至全局默认配置，确保通知不丢失。
  - **接口参数对齐**：同步更新 `TaskScanner.send_buy_notification`，确保推送消息准确送达对应用户的终端。
- **数据库与配置扩展**：
  - **配置表结构升级**：扩展 `user_buff_settings` 表，新增企业微信通知相关的 4 个关键配置字段。
  - **Java 后端同步更新**：同步重构 `UserBuffSettings` 实体类、DTO 及 Service 实现，实现配置项在 Web 端的可视化管理与持久化。

## v1.1.1 (2025-12-27)
### 爬虫性能与稳定性优化
- **全量商品抓取脚本优化 ([get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py))**：
  - **增量跳过逻辑**：引入分类预检查机制，当数据库商品数与 Buff 接口总数匹配时自动跳过，大幅减少冗余 API 请求。
  - **智能停机 (Early Exit)**：翻页过程中若检测到当前页所有商品已存在，则自动停止后续页码抓取，针对“商品少页数多”的分类提升 80% 以上效率。
  - **指数退避限流**：针对 HTTP 429 错误引入指数退避算法（15s, 30s, 60s...）并结合随机抖动（Jitter），显著提升高频抓取下的账号安全性。
  - **强制更新模式**：新增 `--force` 命令行参数，支持手动忽略增量逻辑执行全量刷新。
- **数据库兼容性修复**：
  - **游标访问修正**：修复了 `get_db_existing_ids` 等函数因 `RealDictCursor` 导致的 `KeyError: 0` 报错，通过显式指定标准游标确保了索引访问的稳定性。
  - **健壮性增强**：优化了字段解析逻辑，增加了对 Cookie 失效（Login Required）等关键业务错误的捕获与提示。
  
## v1.1.0 (2025-12-26)
### 架构重构
- **Python 模块独立化**：
  - 将 `niro-spider` 从 `niro-server` 中提取到项目根目录，实现 Java 后端与 Python 爬虫的彻底解耦。
  - **简化层级**：移除了冗余的 Maven 包装（`pom.xml`、`src/main/python`），Python 核心代码直接位于 `niro-spider/` 下。
  - **路径引用重构**：
    - Python 端 `settings.py` 适配新的目录结构，支持从项目根目录自动加载 `.env` 环境变量。
    - Java 端 `LogController` 同步更新日志读取路径，确保实时日志流功能正常。
  - **依赖清理**：删除 `niro-server` 下冗余的 `requirements.txt` 和 Maven 模块配置，保持后端代码纯净。

## v1.0.18 (2025-12-25)
### 修复与优化
- **扫货逻辑增强**：
  - **自动支付重试与降级**：当余额支付（默认方式）返回“该饰品暂不支持此支付方式”时，系统会自动重试并降级为“支付宝”模式。
  - **仅下单不支付支持**：支付宝模式下，Buff 会创建待支付订单并返回支付链接，满足了“仅下单”并手动支付的需求。
  - **通知系统升级**：企业微信推送现在支持区分“购买成功”与“订单已创建（待支付）”，并附带支付跳转链接，点击即可在手机端完成后续支付。
- **Redis 连接优化**：
  - 针对 `QueryTimeoutException` 进行了深度优化，将命令超时与连接超时统一延长至 30s。
  - **连接稳定性增强**：在 `RedisConfig` 中通过 `LettuceClientConfigurationBuilderCustomizer` 开启了 TCP KeepAlive。
  - **自动维持连接**：在 Lettuce 连接池配置中新增 `time-between-eviction-runs: 30s`，定期检测并回收无效连接，有效防止因防火墙或服务端超时导致的频繁断连。
- **后端查询报错修复**：
  - 修复了 `BuffScanTaskServiceImpl` 中因错误使用 `getOne(lambdaQuery())` 导致的 `MyBatisSystemException`。
  - 统一将 Service 层中不符合规范的 `getOne` 调用重构为 MyBatis-Plus 推荐的 `lambdaQuery().eq(...).one()` 链式写法。
- **任务管理完善**：
  - 实现了 `saveTask` 时自动获取当前登录用户 ID，替换了之前的 `0L` 硬编码占位符。
  - 引入 `StpUtil` (Sa-Token) 确保任务与创建者账号绑定。
- **代码规范重构**：
  - 对 `UserBuffSettingsServiceImpl` 进行了重构，移除了过时的 `LambdaQueryWrapper` 构造方式，全面对齐项目最新的 MyBatis-Plus 编码规范。

## v1.0.17 (2025-12-25)
### 新增功能
- **消息推送系统**：
  - 集成了企业微信自建应用推送功能，解决了 PushDeer 和 Server酱 的使用限制问题。
  - **模块化设计**：新增 `utils/notifier.py`，支持 Token 自动管理与 Markdown 格式化消息。
  - **业务集成**：在 `TaskScanner` 中实现扫货成功后的实时通知，包含商品名称、成交价、磨损度及 Buff 订单页跳转链接。
  - **可配置化**：在 `settings.py` 中新增企业微信相关的环境变量配置。

## v1.0.16 (2025-12-25)
### 修复与优化
- **下单接口修复**：
  - 修复了下单时报 `Invalid Argument - Field must be between 10 and 10 characters long` 的错误。
  - **根本原因**：之前将 32 位的 CSRF Token 错误地填充到了 JSON Payload 的 `token` 字段中，而该字段在 Buff API 中通常有特定长度限制（10位）或非必需。
  - **优化方案**：移除 Payload 中的 `token` 字段（保持 `X-CSRFToken` 请求头即可），并强制转换 `goods_id` 为整型、`sell_order_id` 为字符串，同时增加 `X-Requested-With` 请求头以增强兼容性。
  - **可观测性**：增加了下单失败时的详细 HTTP 状态码和完整响应报文记录，便于后续排查。

## v1.0.15 (2025-12-25)
### 架构优化
- **Cookie 来源统一**：
  - 爬虫读取 Cookie 的数据库表从自定义的 `buff_account` 迁移至主业务表 `user_buff_settings`。
  - 实现了前端设置页面与后端爬虫引擎的数据打通，用户在 Web 端更新 Cookie 后，爬虫会自动同步。
### 体验优化
- **任务编辑 UI 修复**：
  - 修复了“编辑任务”对话框中最高价格、磨损范围等输入框在数值较长时显示不全的问题。
  - 统一了所有数字输入框（`t-input-number`）的宽度为 100%，并增加了合理的小数位限制（价格 2 位，磨损 4 位）。

## v1.0.14 (2025-12-25)
### 新增功能
- **个人配置 (后端)**：
  - 新增 `UserBuffSettings` 实体及相关表结构（Buff Cookie、支付方式）。
  - 新增 `UserBuffSettingsController`，提供配置查询和保存接口。
- **个人配置 (前端)**：
  - 前端 `Settings.vue` 接入真实后端 API，实现配置持久化。
  - **UI 升级**：将支付方式选择由传统的单选按钮升级为现代化的“高对比度卡片网格”，引入 `LogoAlipayIcon` 和 `LogoWechatpayIcon` 等图标，显著提升视觉交互体验并解决了 `SyntaxError` 导致的页面崩溃问题。
- **Cookie 动态管理**：
  - 爬虫现已支持从数据库 `buff_account` 表动态读取 Cookie。
  - **无需重启**：在数据库中更新 Cookie 并将 `status` 设为 1，爬虫会在下一次扫描循环时自动加载。
  - **高可用性**：支持多账号预存，自动获取最新更新且状态正常的 Cookie。

## v1.0.13 (2025-12-25)
### 策略调整
- **扫描频率降低**：
  - 将默认的爬取间隔 (`CRAWL_INTERVAL`) 从 2 秒增加至 5 秒，以缓解 Buff 接口风控 (HTTP 429)。
  - `TaskScanner` 现已完全对接 `settings.py` 中的配置，支持通过环境变量动态调整间隔。

## v1.0.12 (2025-12-25)
### 架构优化
- **统一异常处理**：
  - 新增 `handle_api_error` 装饰器（位于 `utils/exception_handler.py`），实现类似 Java `@ExceptionHandler` 的全局异常捕获。
  - 自动拦截 HTTP 429/403/500 等常见错误，输出简洁的中文提示，避免控制台被 Traceback 刷屏。
- **代码重构**：
  - 简化 `BuffSpider` 中的 `get_goods_list` 和 `buy` 方法，移除冗余的 try-catch 逻辑，提升代码可读性。

## v1.0.11 (2025-12-25)
### 体验优化
- **扫描日志增强**：
  - 当未找到符合条件的商品时，打印当前市场的最低价、商品名称以及与目标价格的对比情况。
  - 格式示例：`📈 扫描完成: 商品[AK-47 | 红线] 当前最低价: 150.5 (目标上限: 140.0) - 未满足筛选条件`

## v1.0.10 (2025-12-25)
### 调试优化
- **错误日志增强**：
  - 在下单失败（`buy` 接口）的日志中，追加完整的响应报文 (`Response: ...`)。
  - 目的：便于分析 CSRF、参数错误或业务拒绝的具体原因。

## v1.0.9 (2025-12-25)
### 修复与优化
- **Cookie 解析增强**：
  - 使用 `http.cookies.SimpleCookie` 替代字符串分割，提高 CSRF Token 提取的健壮性。
  - 增加对 Cookie 缺失或格式错误的日志警告。

## v1.0.8 (2025-12-24)
### 功能变更
- **爬虫真实模式**：
  - 关闭模拟测试模式（Mock Data），恢复 `get_goods_list` 与 `buy` 的真实网络请求逻辑。
  - **风险提示**：此版本会执行真实的 Buff 接口调用与余额扣除，请确保测试账号资金安全与参数正确。

## v1.0.7 (2025-12-24)
### 修复与优化
- **日志路径修正**：
  - 修复 Python 爬虫日志输出路径不正确的问题，统一指向根目录下的 `niro-spider/logs/niro_spider.log`，确保与 Java 后端读取路径一致。

## v1.0.6 (2025-12-24)
### 优化与修复
- **后端**：
  - `BuffScanTaskServiceImpl` 中 `saveTask` 方法完善：使用 `StpUtil` 获取当前登录用户 ID，替换之前的占位符。

## v1.0.6 (2025-12-24)
### 调试功能
- **爬虫模拟模式**：
  - 强制开启模拟扫货与模拟下单：`get_goods_list` 必定返回测试商品，`buy` 必定返回支付成功。
  - 目的：用于前端日志展示联调，验证系统日志流是否正常。

## v1.0.5 (2025-12-24)
### 优化与修复
- **爬虫模块**：
  - 激活 `BuffSpider.buy` 真实下单逻辑，移除模拟开关。
  - 完善 API 响应解析，支持记录订单 ID 和交易状态。
  - `TaskScanner` 更新为基于 API 返回数据进行结果判定和日志记录。

## v1.0.4 (2025-12-24)
### 优化与修复
- **爬虫模块**：
  - 修正 `BuffSpider.buy` 接口参数，对齐官方 API 规范（增加 `goods_id`、`sell_order_id`、`X-CSRFToken` 等）。
  - `TaskScanner` 适配新的购买接口参数调用。

## v1.0.3 (2025-12-24)
### 新增功能
- **爬虫模块**：
  - 完善 `BuffSpider` 的购买接口 (`buy`)，支持模拟下单流程。
  - `TaskScanner` 集成购买逻辑，任务匹配成功后自动触发下单。
  - 增加 CSRF Token 自动提取逻辑，为后续对接真实交易做准备。

## v1.0.2 (2025-12-24)
### 优化与修复
- **扫货任务管理**：
  - 修复前端 `BuffScanTask` 类型定义与后端 DTO 不一致的问题（增加 `goodsName`, `goodsIconUrl`）。
  - 补充 `buff_scan_task` 表结构的初始化 SQL 脚本 (`docs/sql/v1.0.0_init_task_table.sql`)。
- **系统日志**：
  - 后端：新增 `LogController`，支持通过 SSE (Server-Sent Events) 实时推送爬虫日志。
  - 前端：实现 Web 终端风格的日志查看器，支持实时滚动、清屏和连接控制。
- **体验优化**：
    - 在商品列表页 (`GoodsList.vue`) 新增“扫货”按钮，支持直接针对商品创建扫货任务。
    - 优化商品列表页布局，将搜索栏与数据表格合并展示，提升视觉紧凑感。
  - **Bug修复**：
    - 修复商品列表页“详情”按钮无法跳转至Buff商品详情页的问题。
    - 修复点击侧边栏菜单无法正确跳转路由的问题。
    - 修复 `GoodsList.vue` 中图标引用错误导致的页面卡死问题。

## v1.0.1 (2025-12-24)
### 新增功能
- **扫货任务管理**：
  - 后端：新增 `BuffScanTask` 实体、Mapper 和 Service。
  - 后端：新增任务管理 API (增删改查、状态控制)。
  - 前端：新增任务管理页面，支持任务列表展示、新增/编辑任务、启停控制及关联商品搜索。
  - 爬虫：新增 `TaskScanner` 扫货调度器，支持从数据库读取运行中任务并执行扫描。
  - 爬虫：实现基于价格和磨损范围的商品筛选逻辑，并集成模拟下单功能。
