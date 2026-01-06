# Release Notes

## v1.9.9 (2026-01-06)
- [修复] **解决 Python 模块 SyntaxError 导致无法启动的问题**：
  - **修复日志错误**：修复了 [logger.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/logger.py) 中 `global logger` 声明位置不当导致的 `SyntaxError`。
  - **优化环境配置**：改进了 [settings.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/config/settings.py) 中的 `.env` 加载逻辑。现在会智能识别 Docker 环境并静默处理环境变量，消除了在容器化部署时反复出现的“未找到 .env 文件”警告，同时保留了在本地开发环境缺失配置时的提示。

## v1.9.8 (2026-01-06)
- [修复] **解决商品同步时的 NotNullViolation 约束冲突（默认值方案）**：
  - **问题根源**：Buff 接口中的某些商品（如音乐盒、印花、探员等）不包含 `exterior`（磨损）或 `rarity`（稀有度）等字段，而数据库 `buff_goods` 表设置了 `NOT NULL` 约束。
  - **架构同步**：更新了 [models.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/models.py) 中的 SQLAlchemy 模型，显式标注 `nullable=False` 并配置 `default=""` 或 `default=0`，使模型定义与数据库实际约束完全对齐。
  - **逻辑加固**：
    - 在 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 中，对所有必填字段（`exterior`、`rarity`、`icon_url` 等）增加了 `or ""` 兜底处理。
    - 在 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 中同步增强了分类字段的默认值逻辑。
  - **合规性**：坚持不修改数据库表结构的原则，通过应用层代码确保数据完整性，彻底解决了因字段缺失导致的批量保存失败问题。

## v1.9.7 (2026-01-06)
- [修复] **统一 Java 与 Python 模块的时区为 Asia/Shanghai (GMT+8)**：
  - **Java (Spring Boot)**：
    - 在 [NiroWebApplication.java](file:///e:/CodeSpace/PYTHON/niro/niro-server/niro-web/src/main/java/com/niro/web/NiroWebApplication.java) 中通过 `@PostConstruct` 设置 JVM 默认时区为 `Asia/Shanghai`，确保日志打印时间与北京时间一致。
    - 在 [common.yml](file:///e:/CodeSpace/PYTHON/niro/niro-server/niro-web/src/main/resources/config/common/common.yml) 中配置了 Jackson 的全局时区为 `GMT+8`，保证接口返回的 JSON 时间格式正确。
  - **Python (Spider)**：
    - 在 [logger.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/logger.py) 中利用 `loguru` 的 `patch` 功能，配合 `pendulum` 强制所有日志记录（控制台、文件、JSON）均使用 `Asia/Shanghai` 时区。解决了服务器系统时区为 UTC 时导致的 8 小时时间差问题。

## v1.9.6 (2026-01-06)
- [功能] **新增请求 IP 实时打印**：
  - **网络工具类**：新增了 [network_util.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/network_util.py)，支持通过 `httpbin.org` 实时获取当前请求的出口 IP 地址。
  - **全流程集成**：在 `BuffSpider`、商品全量同步及分类同步的关键 API 请求处集成了 IP 打印逻辑。现在每次调用 Buff 接口前都会在日志中清晰展示本次请求实际使用的 IP，极大地方便了代理生效情况的监控。
  - **修复隐患**：修复了 `get_buff_goods.py` 在 API 请求时未正确透传代理配置的潜在 Bug。

## v1.9.5 (2026-01-06)
- [修复] **优化代理配置加载与兼容性**：
  - **反引号自动清洗**：针对部分环境 `.env` 文件中 `PROXY_URL` 被反引号（`` ` ``）包裹导致解析失败的问题，在 [settings.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/config/settings.py) 中新增了自动清洗逻辑，确保代理地址合法。
  - **多路径环境变量探测**：增强了环境变量加载的鲁棒性，现在系统会按优先级探测 `niro-spider/`、`niro-web/` 及项目根目录下的 `.env` 文件，解决了 Docker 部署模式下配置无法读取的痛点。
  - **代理助手增强**：在 [proxy_helper.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/proxy_helper.py) 中引入了日志记录，方便排查代理调用链路。

## v1.9.4 (2026-01-06)
- [修复] **解决商品表 tags 字段为空的问题**：
  - **模型映射补全**：在 [models.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/models.py) 中为 `BuffGoods` 实体类新增了 `tags` 字段（Text 类型），并对齐数据库中的 JSON 结构。
  - **提取与序列化优化**：在 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 中利用 Pydantic 的 `AliasPath` 完整提取了 API 响应中的 `tags` 字典对象，并在保存前通过 `json.dumps` 进行了序列化。
  - **数据一致性验证**：通过重启扫描器验证了 `tags` 字段已能正确持久化到数据库中，包含了饰品的磨损、稀有度、类型、品质等核心标签信息。

## v1.9.3 (2026-01-06)
- [优化] **增强任务执行过程的可观测性与日志细节**：
  - **暂停时间可视化**：应用户需求，在 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 和 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 中将爬取间隔的“暂停秒数”日志级别提升至 `INFO`。现在日志流中会明确显示 `💤 暂停 XX.XX 秒后继续...`，方便实时观察采集节奏。
  - **调度日志增强**：在 [task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py) 的任务状态检查逻辑中增加了耗时计算日志。当任务处于“已完成”状态但未被重置时，会记录 `⏳ [PID:XXXX] 任务 [ID:X] ... 刚结束不久 (XX.Xs)，跳过重置`，帮助排查多进程竞争下的状态同步问题。
  - **热重启验证**：通过重启 `run_scanner.py` 验证了代码变更在生产模式下的即时生效，并确认了系统任务在重连后的状态自动修复机制。

## v1.9.2 (2026-01-06)
- [修复] **彻底解决数据库非空约束冲突与任务调度异常**：
  - **映射缺陷修复**：在 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 中，通过 `AliasPath` 正确提取了嵌套对象中的 `icon_url`，解决了 `NotNullViolation` 报错。
  - **通知系统修复**：在 [task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py) 中，重构了 `datetime` 导入方式，解决了 `NameError`。
  - **调度稳定性增强**：
    - 修复了 `update_task_status` 过早释放 `scoped_session` 导致的 `Instance not bound to Session` 异常。
    - 在日志中引入 **PID (进程ID)** 标识，并优化了 `sync_tasks` 逻辑，解决了多进程冲突导致的任务意外重置问题。
  - **健壮性增强**：统一了时间处理逻辑，确保单次系统同步任务在复杂环境下能稳定运行。

## v1.9.1 (2026-01-06)
- [重构] **深度优化 BuffSpider 及其 DTO 模型**：
  - **Pydantic V2 深度适配**：在 [buff_dto.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/dto/buff_dto.py) 中利用 `AliasPath` 实现了对 `sell_order` 接口中 `asset_info.info.tags` 嵌套字段的自动提取（包括 `rarity` 和 `exterior`）。
  - **防御性请求升级**：在 [buff_spider.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/buff_spider.py) 中统一了编码处理逻辑（`utf-8`）与 JSON 安全解析流程，增强了 API 通信的稳定性。
  - **数据一致性加固**：同步更新了 `ParsedBuffItemDTO`，确保扫货逻辑能够直接获取饰品的稀有度和磨损标签，为后续更精细化的扫货策略打下基础。

## v1.9.0 (2026-01-06)
- [重构] **深度优化 API 响应模型与数据提取逻辑**：
  - **Pydantic V2 增强**：引入 `AliasPath` 替代繁琐的手动字典提取。现在 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 和 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 能够通过声明式模型直接解析 `goods_info.info.tags` 中的深度嵌套字段（如 `rarity`、`exterior`、`type`）。
  - **代码精简**：移除了 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 中对 `jmespath` 的依赖，将原本分布在 `property` 或手动循环中的提取逻辑统一收拢至 DTO 定义中。
  - **防御性解析**：统一了 `fetch_buff_goods_api` 的响应处理逻辑，强制 `utf-8` 编码并优先使用安全可靠的 `response.json()` 解析，仅在异常时回退至 `model_validate_json`。
  - **模型一致性**：同步了各模块间的 Pydantic 模型定义，确保系统各处对 Buff API 响应的理解完全一致。

## v1.8.9 (2026-01-06)
- [修复] **解决数据库批量保存失败与通知发送异常**：
  - **编码纠正**：在 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 中强制设置 API 响应编码为 `utf-8`，并改用 `response.json()` 解析，彻底解决了因中文乱码导致的 `name` 字段长度溢出（StringDataRightTruncation）问题。
  - **导入补全**：在 [task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py) 中补齐了缺失的 `import datetime`，修复了发送任务启动通知时的 `NameError`。
  - **健壮性优化**：优化了 API 响应解析逻辑，增加了异常捕获与回退机制，确保在复杂网络环境下数据抓取的稳定性。

## v1.8.8 (2026-01-05)
- [修复] **解决 Python 环境依赖缺失与导入错误**：
  - **依赖补全**：安装了 `requirements.txt` 中定义的全部依赖（包括 `pendulum` 等），解决了 `ModuleNotFoundError`。
  - **导入修正**：在 [task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py) 中补齐了缺失的 `import time`，修复了 `time.sleep` 导致的 `NameError`。
  - **启动验证**：确认 `run_scanner.py` 已能正常初始化环境、同步数据库任务并启动调度中心。

## v1.8.7 (2026-01-05)
- [优化] **引入现代工具库进一步精简代码逻辑**：
  - **Pendulum**：全面替换标准库 `datetime` 和 `time`，利用其更直观的 Fluent API 处理任务持续时间检查、时间戳转换及日志时间格式化。
  - **Pydash**：在 `BuffSpider` 和 `TaskScanner` 中引入 `pydash.get` 进行深层嵌套字典的安全访问，消灭了大量的 `if/else` 判空逻辑，增强了系统健壮性。
  - **依赖预引入**：提前引入 `Boltons` 和 `More-itertools` 库，为后续更复杂的集合操作和系统工具开发打下基础。

## v1.8.6 (2026-01-05)
- [优化] **为 ELK 日志分析系统做深度适配**：
  - **结构化日志输出**：在 [logger.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/logger.py) 中新增了 `niro_spider.json` 处理器。该文件采用 `serialize=True` 配置，每一行均为标准的 JSON 对象，包含时间戳、日志级别、模块名、函数名及所有绑定的上下文。
  - **业务上下文绑定**：
    - 在 [task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py) 的核心扫描循环中，使用 `logger.bind` 自动注入了 `task_id`、`user_id` 和 `task_name`。
    - 在 [buff_spider.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/buff_spider.py) 的 `BuffSpider` 类中实现了实例级的 `user_id` 绑定，确保所有 API 请求相关的日志均带有用户属性。
    - 这使得 ELK 可以实现秒级的多维度筛选与链路追踪。
  - **全局元数据配置**：统一配置了 `service` 和 `env` 基础元数据，确保日志在分布式环境下具备唯一可识别性。

## v1.8.5 (2026-01-05)
- [优化] **引入现代 Python 工具链，全方位简化与加固爬虫业务逻辑**：
  - **日志系统升级**：正式引入 `Loguru` 替换原生 `logging` 模块。实现了零配置的彩色控制台输出、自动日志轮转（10MB）、压缩存档以及异步线程安全写入。
  - **数据建模与校验 (DTO)**：引入 `Pydantic` 建立了标准化的数据传输对象（DTO）。
    - **任务模型**：创建了 [task_dto.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/dto/task_dto.py)，实现了 SQLAlchemy 模型到 DTO 的自动转换与验证，消除了手动构建字典的冗余代码。
    - **API 响应模型**：创建了 [buff_dto.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/dto/buff_dto.py)，支持将 Buff API 的复杂 JSON 响应直接解析为嵌套对象，提升了数据提取的鲁棒性。
  - **自动重试机制**：引入 `Tenacity` 框架重构了 API 请求与系统任务的重试逻辑。
    - 在 [buff_spider.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/buff_spider.py) 中实现了带指数退避的 API 请求自动重试。
    - 在 [task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py) 中使用声明式重试装饰器替代了繁琐的手动计数器，代码量大幅减少。
  - **依赖同步更新**：在 `requirements.txt` 中新增了 `loguru`、`pydantic` 和 `tenacity` 依赖。

## v1.8.4 (2026-01-05)
- [重构] **深度迁移 ORM 框架至 SQLAlchemy，全面拥抱生产级数据库标准**：
  - **核心架构升级**：正式弃用轻量级 Peewee，迁移至功能更强大的 [SQLAlchemy](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/database.py)。建立了基于 `scoped_session` 的线程安全连接池管理机制，确保高并发爬虫场景下的数据连接稳定性。
  - **模型定义对齐**：在 [models.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/models.py) 中利用 SQLAlchemy 的 `Declarative Base` 完整重构了 6 张核心表的映射模型，并实现了更精确的字段类型（如 `Numeric(10, 8)` 用于磨损程度）与索引约束。
  - **全模块依赖重构**：
    - **TaskScanner**：深度重构了 [task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py)，将所有原生 SQL 和 Peewee 逻辑替换为 SQLAlchemy Session。优化了 `run_scan_cycle`、`update_task_status` 等核心方法的异常捕获与会话自动清理流程。
    - **Cookie 机制**：重写了 [cookie_util.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/cookie_util.py)，利用 SQLAlchemy 模型检索用户 Cookie，移除了对 `PostgresPool` 的原生 SQL 依赖。
    - **通知系统**：重构了 [notifier.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/notifier.py)，实现了基于 ORM 的动态通知配置读取。
    - **爬虫基类**：清理了 [buff_spider.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/buff_spider.py) 中残留的旧版连接池引用。
  - **清理旧冗余**：彻底删除 `storage/postgres_pool.py` 和 `storage/base_model.py`，项目架构更加纯粹，统一使用 SQLAlchemy 处理所有持久化操作。
  - **依赖同步更新**：更新 `requirements.txt`，正式确立 `sqlalchemy` 为 Python 模块的唯一 ORM 方案。

## v1.8.3 (2026-01-05)
- [重构] **引入 Peewee ORM 提升 Python 数据库层开发效率**：
  - **基础设施搭建**：新增 [base_model.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/base_model.py) 建立 Peewee 数据库连接实例，并创建 [models.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/models.py) 完成对 `buff_goods`、`buff_scan_task`、`sys_user` 等 6 张核心表的 ORM 模型定义。
  - **业务逻辑重构**：深度重构了 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py)，将原生的 `psycopg2` SQL 拼接逻辑替换为 Peewee 的链式调用，代码可读性与健壮性显著提升。
  - **性能与一致性**：利用 Peewee 的 `on_conflict` 机制完美兼容了 PostgreSQL 的 `UPSERT` 操作，确保商品批量同步时的原子性。
  - **依赖管理**：同步更新 `requirements.txt`，正式将 `peewee` 纳入项目核心依赖。

## v1.8.2 (2026-01-05)
- [优化] **精简系统任务重试与通知逻辑**：
  - **静默重试机制**：系统同步任务（商品/分类）在触发重试时，不再重复发送“任务启动”通知，仅在首次启动时推送。
  - **减少通知干扰**：取消了系统同步任务成功完成后的即时推送，改为仅在后台日志记录，保持通知频道的整洁。
  - **异常分级处理**：
    - 将前两次执行异常的日志级别由 `ERROR` 降级为 `WARNING`，明确标注为“自动重试”状态。
    - 仅在连续失败达到阈值（3次）导致任务熔断停止时，才会触发企业微信告警。
  - **交互体验提升**：优化了任务执行状态的日志输出，使开发者能更清晰地通过日志流区分正常波动与系统故障。

## v1.8.1 (2026-01-05)
- [优化] **爬取间隔配置动态化**：
  - **环境变量解耦**：将商品数据和分类数据的爬取间隔时间由硬编码（10-15s）改为从环境变量读取。
  - **新增配置项**：在 [settings.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/config/settings.py) 中新增了 `CRAWL_INTERVAL_MIN` (默认 10s) 和 `CRAWL_INTERVAL_MAX` (默认 15s) 配置，支持在 `.env` 中灵活调整。
  - **全模块覆盖**：同步更新了 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 和 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py)，确保全局生效。

## v1.8.0 (2026-01-05)
- [功能] **新增爬虫代理全局开关控制**：
  - **ENABLE_PROXY 开关**：在 [settings.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/config/settings.py) 中引入了 `ENABLE_PROXY` 配置项。现在可以通过 `.env` 文件一键控制爬虫是否启用代理（默认 `false` 为直连模式）。
  - **逻辑解耦**：在 [proxy_helper.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/proxy_helper.py) 中实现了开关拦截逻辑。当开关关闭时，系统会彻底忽略 `PROXY_URL` 和 `PROXIES` 配置，直接使用本地网络请求，满足用户在已有系统代理或特定网络环境下的直连需求。
  - **鲁棒性验证**：已通过单元测试验证了开关切换对代理地址返回的准确控制，确保逻辑闭环。

## v1.7.9 (2026-01-05)
- [修复] **解决分类同步时的数据库唯一键冲突问题**：
  - **数据库约束优化建议**：定位到 `buff_goods_categories` 表中 `name` 字段的 `UNIQUE` 约束导致了同步任务回滚。由于 Buff API 存在不同 `internal_name` 对应相同 `name` 的情况，建议移除该约束（SQL 已提供）。
  - **Python 预去重增强**：在 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 的一级和二级分类抓取逻辑中，新增了基于 `name` 的内存级预去重机制。在不影响数据抓取完整性的前提下，确保单次批量写入数据库时不会触发名称冲突，大幅提升了同步任务的鲁棒性。

## v1.7.8 (2026-01-05)
- [优化] **爬虫频率限制与同步策略调整**：
  - **请求间隔延长**：为了降低被 Buff 平台封禁的风险，将商品同步和分类同步的单页请求间隔从原有的 2-5 秒大幅延长至 **10-15 秒**。
  - **最大同步页数限制**：新增了单次同步任务的页数上限。无论是分类抓取还是商品抓取，系统现在最多只同步前 **20 页** 的数据，在保证核心数据新鲜度的同时，有效规避因扫描过深触发的平台风控。
  - **交互体验优化**：在爬虫日志中新增了总页数与同步上限的实时提示，并在每次请求前打印具体的休眠时长，方便监控同步进度。
- [修复] **Docker 生产环境代理连通性加固**：
  - 在 [docker-compose.prod.yml](file:///e:/CodeSpace/PYTHON/niro/docker-compose.prod.yml) 和 [docker-compose.test.yml](file:///e:/CodeSpace/PYTHON/niro/docker-compose.test.yml) 中同步补齐了 `extra_hosts` 配置，确保在所有部署环境下容器均能稳定访问宿主机的 v2rayA 代理服务。

## v1.7.7 (2026-01-05)
- [功能] **新增全局代理支持 (v2rayA 适配)**：
  - **单点代理配置**：在 [settings.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/config/settings.py) 中新增了 `PROXY_URL` 配置项，支持通过环境变量直接设置全局 HTTP/SOCKS5 代理。
  - **Docker 容器间通信优化**：在 [docker-compose.prod.yml](file:///e:/CodeSpace/PYTHON/niro/docker-compose.prod.yml) 和 [docker-compose.test.yml](file:///e:/CodeSpace/PYTHON/niro/docker-compose.test.yml) 中为爬虫服务添加了 `extra_hosts` 配置（`host.docker.internal:host-gateway`）。这使得运行在 Docker 容器内的爬虫能够通过 `host.docker.internal` 直接访问宿主机上的 v2rayA 代理服务，解决了跨容器网络通信的痛点。
  - **全模块代理覆盖**：
    - **基础爬虫**：[buff_spider.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/buff_spider.py) 的 `get_goods_list` 和 `buy` 接口现在均支持代理。
    - **分类同步**：[get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 的 API 请求已接入代理。
    - **Cookie 验证**：[cookie_util.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/cookie_util.py) 的预检查环节同步支持代理，确保验证逻辑与抓取逻辑路径一致。
  - **灵活调度**：通过 [proxy_helper.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/proxy_helper.py) 实现了代理优先级的智能选择（PROXY_URL 优先于代理池），方便用户在不同环境下快速切换。

## v1.7.6 (2026-01-05)
- [修复] **解决服务器环境 403 Forbidden 导致的 Cookie 验证失效问题**：
  - **请求头特征深度增强**：在 [cookie_util.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/utils/cookie_util.py) 的 `verify_cookie` 函数中，同步了全量浏览器指纹请求头（包括 `sec-ch-ua`、`sec-fetch-*` 等），使预检查环节的 HTTP 特征与真实浏览器完全一致。
  - **反爬策略对齐**：统一了整个爬虫模块（Spider, Category Sync, Goods Sync）的 HTTP 身份标识，降低了因预检查请求特征过简而在高安全等级（IDC IP）环境下被 WAF 拦截的概率。

## v1.7.5 (2026-01-05)
- [优化] **分类与商品抓取深度优化 (全量同步支持)**：
  - **移除硬编码页数限制**：在 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 中，彻底移除了原有一级分类（15页）和二级分类（20页）的硬编码限制。
  - **动态分页自适应**：重构了分类抓取核心逻辑。系统现在会自动解析 Buff API 响应中的 `total_page` 字段，并根据该字段动态调整循环次数，从而实现分类数据的 100% 全量覆盖。
  - **商品同步完整性校验**：确认并优化了 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 中的分页逻辑，确保其严格遵循 API 返回的总页数进行抓取，消除了潜在的数据遗漏风险。
  - **性能与安全平衡**：在开启全量抓取的同时，保留并优化了“任务手动停止”秒级响应检查以及基于 `random.uniform` 的随机延时策略，确保在追求数据完整性的同时，最大程度规避 Buff 的反爬风控。

## v1.7.4 (2026-01-04)
- [修复] **爬虫反爬指纹对齐与任务调度状态机优化**：
  - **反爬指纹深度对齐**：修复了 `User-Agent` 与 `sec-ch-ua` 版本不一致的问题（之前为 144 与 143 混用，现统一对齐至 131），并补齐了 `sec-fetch-*` 系列现代浏览器安全请求头，降低被 Buff 判定为 Bot 的概率。
  - **任务重置竞态修复**：优化了 `TaskScanner` 的任务监控逻辑。通过引入 `last_finished_tasks` 宽限期机制（30秒），解决了系统任务在正常结束/异常捕获瞬间，因数据库状态未及时同步而被调度中心误判为“卡死”并强行重置的问题。
  - **Cookie 预检查机制**：在全量商品同步和分类同步启动前，新增了轻量级的 `verify_cookie` 验证环节。若 Cookie 已失效，任务将立即停止并发出预警，避免在无效状态下进行大量请求导致 IP 风险。
  - **代码健壮性提升**：统一了 `BuffSpider`、`get_buff_goods.py` 等多处的请求头模板，确保全模块行为一致。

## v1.7.3 (2026-01-04)
- [优化] **爬虫 Cookie 加载机制增强与全量同步稳定性提升**：
  - **数据库 Cookie 实时检索优化**：重构了 [get_buff_goods.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods.py) 和 [get_buff_goods_category.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/get_buff_goods_category.py) 的入口逻辑。显式支持通过 `user_id` 或 `task_id` 检索对应用户的 Cookie。在未指定参数时，系统将自动回退至数据库中最新更新的有效 Cookie，确保了全量同步任务不再受限于硬编码或过期配置。
  - **请求头指纹对齐**：统一并升级了商品全量抓取与分类同步的 HTTP 请求头。引入了更现代的 `User-Agent`、`sec-ch-ua` 系列指纹以及更完善的 `Referer` 策略，降低了因请求特征不匹配导致的被动反爬风险。
  - **脚本交互性增强**：为 `get_buff_goods.py` 脚本增加了命令行参数支持（`--user_id`, `--task_id`），方便开发者在终端进行手动测试或任务补录。
  - **日志诊断增强**：在同步开始阶段新增了 Cookie 来源与所属用户的关键日志输出，极大地方便了 Cookie 失效场景下的快速定位。

## v1.7.2 (2026-01-04)
- [修复] **核心请求挂起与假性超时修复**：
  - **Nginx 路由精细化隔离**：彻底解决了普通 API 请求（如登录）在 Nginx 代理层出现的“响应不闭合”问题。通过将 SSE（实时日志流）专用配置剥离到独立的 `/log/stream` 路径，恢复了普通 API 请求对 `Chunked Transfer Encoding` 的默认支持，消除了浏览器因等待连接关闭而导致的假性超时现象。
  - **转发稳定性提升**：明确了普通 API 路径的 `proxy_buffering` 和 `proxy_read_timeout` 标准配置，确保高频短连接请求的快速响应与资源及时回收。

## v1.7.1 (2026-01-04)
- [优化] **请求链路诊断能力升级与 Nginx 配置精简**：
  - **全链路耗时监控**：在 [request.ts](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/utils/request.ts) 中引入了请求/响应拦截器耗时统计。现在控制台会清晰输出每个接口从发起请求到接收响应的毫秒数，帮助定位后端业务性能瓶颈。
  - **深度错误诊断**：优化了 Axios 响应拦截器的错误处理逻辑。新增了“请求超时”专属提示，并区分了“网络错误”、“跨域拦截”与“业务代码异常”，极大降低了假性超时的排查难度。
  - **性能阈值调整**：将全站 API 请求超时上限从 15s 延长至 **30s**，以适配复杂数据同步任务在服务器端的极端响应场景。
  - **Nginx 配置精简**：在 [nginx.prod.conf](file:///e:/CodeSpace/PYTHON/niro/niro-client/nginx.prod.conf) 和 [nginx.test.conf](file:///e:/CodeSpace/PYTHON/niro/niro-client/nginx.test.conf) 中移除了冗余的 `rewrite` 指令。利用 `proxy_pass` 结尾斜杠的路径替换特性实现更规范的转发，避免了双重重写可能导致的 URL 路径解析缓慢问题。

## v1.7.0 (2026-01-04)
- [修复] **生产/测试环境关键配置纠正与超时优化**：
  - **Docker 配置修复**：修复了 `docker-compose.prod.yml` 和 `docker-compose.test.yml` 中 Redis 密码环境变量的拼写错误 (`SPRING_DATA_REDIS_PASSWRD` -> `SPRING_DATA_REDIS_PASSWORD`)。该错误曾导致后端无法连接 Redis，进而引发登录接口挂起超时。
  - **Nginx 链路优化**：移除了 Nginx 代理层冗余的 CORS 响应头配置，统一由 Spring Boot 后端处理，避免了响应头重复导致的浏览器拦截。同时精简了 `proxy_pass` 逻辑，移除了冗余的 `rewrite` 指令。
  - **前端稳定性增强**：将 Axios 默认请求超时时间从 10s 提升至 15s，并优化了超时错误提示，更精准地引导用户排查环境问题。

## v1.6.9 (2026-01-04)
- [修复] **全站跨域 (CORS) 策略增强与登录异常修复**：
  - **后端全局配置**：在 `niro-core` 的 [WebConfig.java](file:///e:/CodeSpace/PYTHON/niro/niro-server/niro-core/src/main/java/com/niro/core/config/WebConfig.java) 中实现了 `addCorsMappings`，允许来自任何源的跨域请求，并支持 `allowCredentials` 和常用 HTTP 方法，彻底解决了本地开发环境（如 localhost）请求远程生产环境 API 时的 CORS 拦截问题。
  - **Nginx 代理层加固**：在 [nginx.prod.conf](file:///e:/CodeSpace/PYTHON/niro/niro-client/nginx.prod.conf) 和 [nginx.test.conf](file:///e:/CodeSpace/PYTHON/niro/niro-client/nginx.test.conf) 的 `/prod-api/` 路径下补充了 `Access-Control-Allow-Origin` 等跨域头处理逻辑，并对 `OPTIONS` 预检请求进行了 `204` 快速响应处理，确保在多级代理环境下跨域策略的一致性。
  - **前端异常诊断优化**：改进了 [request.ts](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/utils/request.ts) 中的网络异常捕获逻辑。针对 200 OK 但无响应内容的典型 CORS/网络错误场景，提供了更具指导性的错误提示（如提示检查后端服务或跨域配置），提升了故障排查效率。

## v1.6.8 (2026-01-04)
- [重构] **Redis 基础设施统一化管理**：
  - **连接池收归**：删除了冗余的 `redis_client.py`，新建通用的 [redis_pool.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/redis_pool.py)，采用单例模式统一管理全站 Redis 连接，避免重复创建连接池导致资源浪费。
  - **命名语义优化**：将原有的 `RedisProxyPool` 重命名为 [redis_storage.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/storage/redis_storage.py) 中的 `RedisStorage` 类。去除了“代理池”这一具有误导性的命名，使其回归“通用 Redis 业务存储”的本质。
  - **解耦业务逻辑**：`RedisStorage` 不再自建连接，而是通过引用全局 `redis_pool` 来执行业务逻辑，符合高内聚低耦合的架构设计。

## v1.6.7 (2026-01-04)
- [优化] **商品分类同步性能极致优化 (Redis 指纹 + 内存比对)**：
  - **Redis 指纹校验**：引入 Redis 存储分类数据的 MD5 指纹。在每次执行分类抓取任务前，先对比抓取到的数据与 Redis 中的指纹。若无变化（99% 的场景），则直接跳过后续所有的数据库查询与更新操作，将任务耗时从秒级降低至毫秒级。
  - **内存增量同步**：若指纹发生变化，系统将全量加载对应父分类下的数据库记录到内存，通过 Python 字典进行字段级深度比对，仅对真正发生变更（Name, ParentID, FullInternalName 变化）或新增的记录执行 SQL UPSERT。
  - **资源节约**：该优化极大减少了数据库的 IO 压力、事务开销以及索引更新频率，特别是在 CSGO 分类极其稳定的环境下，几乎消除了所有无意义的数据库写操作。
  - **单例 Redis 客户端**：在 `niro-spider` 模块中抽象了通用的单例 `RedisClient` 工具类，并支持连接失败时的自动降级处理。

## v1.6.6 (2026-01-04)
- [优化] **生产环境日志配置增强**：
  - 支持通过环境变量 `LOG_MAX_BYTES` 和 `LOG_BACKUP_COUNT` 动态配置爬虫日志的轮转策略（单文件大小及备份保留数）。
  - 完善了 Docker 部署模式下的日志路径共享建议，确保 Java 后端与 Python 爬虫在容器化环境下仍能实现实时日志流同步。

## v1.6.5 (2026-01-04)
- [优化] **实时日志路径智能搜索升级**：
  - 重构了 `LogController` 的日志文件定位逻辑，引入了“递归向上路径搜索”算法。系统现在会从当前工作目录开始，逐级向上查找直到找到 `niro-spider/logs/niro_spider.log`。
  - 解决了由于 IDE 启动、命令行启动（根目录 vs 模块目录）导致的工作目录不一致，进而无法定位爬虫日志的问题。
  - 增强了路径探测的调试日志，并在定位失败时提供更具指导性的错误提示。

## v1.6.4 (2026-01-04)
- [优化] **Cookie 实时加载与环境适配**：
  - **数据库实时同步**：重构了 `BuffSpider`、`get_buff_goods_category.py` 和 `get_buff_goods.py` 的 Cookie 加载逻辑。现在所有爬虫组件在发起请求前都会先从数据库 (`user_buff_settings`) 实时获取最新的 Cookie，解决了“登录更新后爬虫仍使用旧 Cookie”的问题。
  - **配置加载优先级**：优化了 `settings.py` 的环境变量加载顺序，优先加载 Web 端的 `.env` 文件，确保企业微信通知参数和全局 Cookie 能够正确读取。
- [修复] **通知发送可见性与诊断增强**：
  - 在 `notifier.py` 中增加了详细的请求结果日志记录。当企业微信通知发送失败时（如 IP 未白名单 `60020` 错误），日志将直接输出具体原因，极大提升了运维排错效率。
- [修复] **任务调度状态同步补丁**：
  - 修复了 `task_scanner.py` 中任务启动时的状态同步时序问题。通过在标记数据库状态为“执行中”的同时同步更新内存中的 `running_tasks` 列表，彻底消除了任务启动瞬间因同步延迟导致的“状态误判与自动重置”告警。

## v1.6.3 (2026-01-04)
- [优化] **任务控制体验与稳定性增强**：
  - **手动控制闭环**：在前端任务列表中，允许用户在“执行中 (4)”、“异常 (3)”和“已停止 (2)”状态下手动触发“停止”或“启动”操作，解决了任务卡死或异常后无法手动恢复的问题。
  - **状态自愈机制**：优化了 `task_scanner.py` 的同步逻辑，增加了“卡死状态自动修复”。若任务在数据库中标记为“执行中”但实际调度引擎未运行（如进程异常重启场景），系统将自动重置其状态，确保任务能被重新拉起。
  - **容错重试升级**：针对扫描任务和系统任务，引入了通用异常的连续失败重试机制，避免因单次网络波动导致任务熔断。
    - **扫描任务**：容忍连续 5 次扫描异常。
    - **系统任务**：容忍连续 3 次执行异常。

## v1.6.2 (2026-01-04)
- [修复] **Cookie 失效熔断机制深度优化**：
  - **异常穿透修复**：修复了 `get_buff_goods.py` 和 `get_buff_goods_category.py` 中 403 错误被重试逻辑吞掉的问题。现在 HTTP 403 状态码会立即抛出 `LoginRequiredError`。
  - **熔断判定增强**：修复了系统同步任务在单次执行失败后状态被卡在“执行中 (4)”的问题。现在任务失败但未达熔断上限（3次）时，会自动恢复为“待运行 (1)”状态以便后续重试。
  - **状态语义化**：引入“异常 (3)”状态，当任务因连续 3 次 Cookie 失效或运行期间发生非预期异常时，数据库状态将更新为 `3`（异常），在前端界面显示为“异常”红色标签，提供更清晰的故障反馈。
  - **精准停止响应**：在系统任务的重试循环和分类迭代中均完善了 `LoginRequiredError` 的向上抛出逻辑，确保熔断器能实时拦截并处理登录失效。

## v1.6.1 (2026-01-04)
- [优化] **实时日志路径智能探测**：
  - 增强了 `LogController` 的日志文件定位能力，引入 `findLogFile` 逻辑，支持从根目录、`niro-server` 或 `niro-web` 等不同启动层级自动适配爬虫日志路径。
  - 改进了错误提示，当无法定位日志时会输出当前工作目录（`user.dir`），方便排查环境问题。

## v1.6.0 (2026-01-04)
- [修复] **系统同步任务误触停止逻辑修复**：
  - 引入了 `TASK_STATUS_RUNNING (4)` 状态，解决了系统同步任务（非 Cron）启动时因立即更新数据库状态为 `0` 导致的异步进程误判“手动停止”的问题。
  - 优化了 `TaskScanner` 的调度逻辑：任务启动后先标记为 `4`，同步脚本运行期间检查状态是否为 `1` 或 `4`。
  - 修复了 Cron 系统任务执行一次后自动停止的问题，现在执行完后会正确恢复为 `1` 状态等待下次触发。
  - 修改文件：`get_buff_goods.py`, `get_buff_goods_category.py`, `task_scanner.py`。

## v1.5.9 (2026-01-04)
- [修复] **实时日志路径配置纠正**：
  - 修正了 `common.yml` 中 `spider.log.path` 的相对路径，从 `../niro-spider` 调整为 `../../niro-spider`，使其在 `niro-web` 模块启动时能正确定位到跨模块的爬虫日志文件。
  - 解决了在本地开发环境下由于工作目录层级导致 SSE 日志流提示“日志文件不存在”的问题。

## v1.5.8 (2026-01-04)
- [功能] **Cookie 失效熔断机制**：
  - **异常自动捕获**：引入 `LoginRequiredError` 异常，全量覆盖 `BuffSpider`、商品同步及分类同步模块，精准识别 Buff 官网返回的“未登录”状态。
  - **三连击熔断策略**：当某个任务（普通任务或系统同步任务）连续 3 次检测到 Cookie 失效或未登录时，系统将自动执行“熔断”操作。
  - **自动停止与通知**：熔断后，系统会主动在数据库中停止该任务 (`status=0`)，从调度器中移除，并向管理员发送企业微信通知，提醒及时更新 Cookie，避免无意义的无效抓取。
  - **自愈机制**：任务一旦成功执行一次，对应的失效计数器将自动清零。

## v1.5.7 (2026-01-04)
- [修复] **长耗时任务停止响应机制**：
  - **动态状态检查**：针对 `系统-分类同步` 和 `系统-商品同步` 这类执行周期较长的系统任务，在爬虫端的每一轮迭代（分类切换、页码切换）中均引入了数据库状态主动检查。
  - **毫秒级停止响应**：当管理员在后台手动停止任务后，爬虫脚本能立即识别 `status=0` 状态并优雅终止当前的同步流程，彻底解决了“后台停止后爬虫仍继续执行”的问题。
  - **线程安全同步**：在 `TaskScanner` 中引入 `running_tasks` 运行状态表，确保调度器在停止作业的同时能正确同步状态给正在后台执行的同步线程。
- [修复] **任务通知系统健壮性**：
  - 优化了系统任务停止时的通知逻辑，能够区分“正常同步完成”与“手动终止同步”，并在通知中准确记录执行结果。

## v1.5.6 (2026-01-04)
- [修复] **登录接口稳定性与逻辑增强**：
  - **预防栈溢出 (StackOverflowError)**：针对服务器 Docker 环境下出现的 `/user/login` 接口异常，增强了 `WebLogAspect` 日志切面的健壮性。
    - 在 JSON 序列化阶段引入 `Throwable` 级异常捕获，彻底杜绝由于对象循环引用或序列化深度过大导致的无限递归问题。
  - **Redis 驱动与 Sa-Token 深度兼容性修复**：
    - 将 `Redisson` 版本从 `3.24.3` 升级至 `3.44.0`。
    - **重磅改进**：将 Sa-Token 的 Redis 插件由 `sa-token-redis-jackson` 切换为 **`sa-token-redisson-spring-boot-starter`**。
    - **原理**：直接使用 Redisson 的原生 API 存储 Session，彻底绕过了 Spring Data Redis 的 `RedisConnection` 抽象层。这从根本上消除了在 Spring Boot 3.5.x 环境下由 `DefaultedRedisConnection.pExpire` 引起的递归死循环（StackOverflowError）。
  - **环境配置一致性对齐**：
    - 修复了测试环境 (`test` profile) 配置文件中残留的 `lettuce` 连接池配置。
    - 统一将测试环境的 Redis 驱动配置切换为 `Redisson` 模式，并同步了生产环境的长连接探测 (PING) 与超时策略，彻底解决测试环境下 Redis 连接不稳定导致的登录异常。
- [修复] **实时日志流 (SSE) 稳定性修复**：
  - **路径兼容性**：修复了 `spider.log.path` 在 Windows 环境下的硬编码问题，引入 `SPIDER_LOG_PATH` 环境变量，适配 Docker (Linux) 部署。
  - **容器间日志共享**：通过 Docker Volume (`niro_shared_logs`) 实现了 `niro-spider` 与 `niro-web` 容器间的日志文件实时共享。
  - **前端连接修复**：修正了 `Logs.vue` 中的硬编码 API 前缀，使其动态适配 `VITE_BASE_API`。
  - **Nginx 转发优化**：针对 SSE 特性优化了 Nginx 配置，禁用了 `proxy_buffering` 并延长了超时时间，解决了连接频繁中断的问题。
  - **鉴权白名单**：将 `/log/stream` 加入 Sa-Token 免登录白名单，确保日志流连接不被拦截。
- [修复] **爬虫端依赖与导入修复**：
  - **移除冗余导入**：移除了 `buff_spider.py` 中未使用的 `pandas` 库导入，解决了 Docker 环境下因缺少该库导致的 `ModuleNotFoundError`。
  - **补充缺失依赖**：在 `requirements.txt` 中补充了 `jmespath` 依赖，确保分类同步功能在容器化环境下正常运行。
- [修复] **用户状态校验逻辑修正**：
  - 修复了 `UserStatusEnum.isNormal` 中的类型比较 Bug，确保状态判定准确无误。
  - 调整了 `UserServiceImpl` 中的登录拦截逻辑，适配现有的 `Assert.validateTrue` 断言工具类。

## v1.5.5 (2026-01-03)

- [功能] **系统同步任务支持**：
  - **任务类型扩展**：新增 `系统-分类同步` (SYNC_CATEGORY) 和 `系统-商品同步` (SYNC_GOODS) 任务类型，支持通过定时任务自动更新 Buff 官网的基础数据。
  - **数据库结构优化**：修改 `buff_scan_task` 表约束，将 `goods_id` 和 `max_price` 设为可选，并更新任务类型注释。
  - **后端业务重构**：
    - 新增 `TaskTypeEnum` 枚举类，统一管理任务类型及系统任务判定逻辑。
    - 优化 `BuffScanTaskServiceImpl` 的校验逻辑，针对系统任务自动设置任务名称并跳过商品关联性检查。
    - 调整 `BuffScanTaskParam` 校验规则，支持条件化参数校验。
  - **爬虫端 (Python) 深度改造**：
    - **模块化解耦**：重构 `get_buff_goods_category.py` 和 `get_buff_goods.py`，封装标准化同步入口函数，支持被其他模块直接调用。
    - **调度引擎升级**：`TaskScanner.py` 新增对系统任务的支持，区分“普通扫描任务”与“系统同步任务”执行逻辑。
    - **执行策略优化**：系统任务支持 Cron 周期性调度和“立即执行”模式；“立即执行”的系统任务在单次同步完成后自动进入停止状态。
    - **通知系统适配**：系统任务执行过程集成任务通知体系，针对管理员发送专属的“系统同步启动/完成”通知，并优化了消息排版与内容展示。
  - **前端交互升级**：
    - `TaskConfig.vue` 支持配置系统同步任务，并根据任务类型动态切换表单项（自动隐藏商品选择、价格磨损、购买数量等无关字段）。
    - **UI 深度优化**：针对系统任务自动隐藏“扫描间隔”与“持续时间”配置项，简化操作路径。
    - **智能调度支持**：新增“执行计划”概念替代 Cron 表达式，并为系统任务提供默认调度建议（每日凌晨 2 点自动同步）。
    - 优化任务列表展示，为系统任务增加专用图标标识，并隐藏不适用的进度信息。
    - 改进执行预览文案，根据任务类型自动切换“采集价格”或“同步数据”描述。
- [修复] **扫描间隔限制优化**：
  - **维持安全阈值**：恢复普通扫描任务的最小扫描间隔为 5 秒，以降低触发平台限流的风险。
  - **系统任务适配**：针对系统同步任务，前端提交时自动填充默认扫描间隔（5s），确保通过后端 JSR303 (`@Min(5)`) 校验。
  - **实时响应**：修复了前端编辑任务时因 `formData` 异步更新导致的校验提示滞后问题。

## v1.5.4 (2025-12-31)

- [运维] **修复前端构建依赖冲突与清理冗余依赖**：
  - **移除冗余依赖引用**：从 `niro-client/vite.config.ts` 的 `manualChunks` 中移除了未安装的 `naive-ui` 以及确认未使用的 `element-plus`。
  - **卸载未使用依赖包**：彻底卸载了 `element-plus` 和 `@element-plus/icons-vue`，减小了项目体积并降低了构建时的依赖解析压力。
  - **优化分包策略**：精简了 `ui-vendor` 分包配置，确保生产环境构建的稳定性，解决了 "Could not resolve entry module" 导致的构建中断。
  - **增强构建鲁棒性**：通过清理无效配置和依赖，提升了 `vite build` 在 2G 内存等受限环境下的成功率。

## v1.5.3 (2025-12-30)

- [运维] **修复前端 Docker 构建 OOM 问题**：
  - 在 `niro-client/Dockerfile` 中增加了 `NODE_OPTIONS="--max-old-space-size=2048"` 环境变量，解决了在内存受限环境（如 2G 内存服务器）下 Vite 构建失败的问题。
  - 优化了构建阶段的内存分配，确保生产环境构建稳定性。

## v1.5.2 (2025-12-30)

- [功能] **动态通知配置系统**：
  - **按需加载**：Python 爬虫端的通知组件 (`Notifier`) 现在会根据任务所属的 `user_id` 动态从数据库 `user_buff_settings` 表中读取企业微信配置（CorpID, Secret, AgentID, ToUser）。
  - **多租户支持**：重构了 Token 缓存机制，支持同时为多个不同企业微信账号管理 `access_token`，避免多用户环境下的 Token 冲突。
  - **智能兜底**：如果任务未指定 `user_id`，系统将自动使用数据库中最新更新的一组配置作为兜底，确保通知不丢失。
  - **零重启配置**：用户在前端页面修改企业微信配置后，爬虫端下次发送通知时将自动生效，无需重启服务。

## v1.5.1 (2025-12-30)

- [运维] **Docker 生产环境健壮性增强**：
  - **全链路健康检查 (Healthcheck)**：
    - **数据库 (PostgreSQL)**：集成 `pg_isready` 检查，确保数据库完全就绪。
    - **缓存 (Redis)**：集成 `redis-cli ping` 检查，确保缓存连接正常。
    - **后端 (Spring Boot)**：集成 `wget` 端口存活检查，并设置 `40s` 启动宽限期（`start_period`），适配 Java 应用启动耗时。
  - **智能启动编排**：
    - 升级 `depends_on` 配置，从简单的 `service_started` 优化为 `service_healthy`。
    - 确保 `niro-web` 和 `niro-spider` 仅在数据库和 Redis 真正“可用”时才启动，彻底杜绝启动初期的连接超时报错。
    - 前端 `niro-client` 仅在后端 API 真正“可用”时才启动，提升容器集群的自愈能力。

## v1.5.0 (2025-12-30)

- [功能] **双环境 Docker 部署方案**：
  - **生产环境 (PROD)**：新增 `docker-compose.prod.yml`，包含独立的数据库、Redis 及业务容器，网络后缀统一为 `-prod`。
  - **测试环境 (TEST)**：新增 `docker-compose.test.yml`，适配服务器已有的 PostgreSQL 18 和 Redis 8 容器。
  - **环境隔离**：通过不同的 Compose 文件和端口映射（PROD: 81/8081/5433/6790；TEST: 80/8080/5432/6789）实现完全的运行隔离。
  - **版本对齐**：生产环境镜像版本已同步为 PostgreSQL 18 和 Redis 8.0，确保与测试环境基础设施一致。
  - **配置标准化**：支持 `.env.prod` 和 `.env.test` 分别管理不同环境的敏感信息。

## v1.4.3 (2025-12-30)
- [功能] **任务状态全生命周期通知**：新增任务启动与停止的微信通知机制。
  - **启动提醒**：任务正式开始扫描时，发送包含详细参数（启动时间、预计结束时间、扫描间隔、目标数量）的通知。
  - **到期提醒**：当任务运行时间达到预设的持续时间（`duration_minutes`）自动停止时，发送通知。
  - **上限提醒**：当购买成功的商品数量达到目标数量（`buy_count`）任务完成时，发送通知。
  - **进度摘要**：通知消息中包含任务名称、停止原因以及最终的完成进度（如 10/10）。
- [优化] **通知体验升级**：
  - **纯文本通知回归**：响应用户反馈，将通知样式统一改回纯文本格式。
  - **零跳转、零误触**：纯文本消息在任何客户端点击均无反应，彻底解决了点击跳转的困扰。
  - **结构化排版**：通过分隔符（`---`）对任务详细信息进行排版，保证了信息的清晰度。
- [修复] **UI 交互优化**：
  - **表单布局修复**：解决了“新增任务”弹窗中，商品校验错误提示文字与下方单选框重叠的问题。
  - **间距统一**：移除了过度的 CSS 间距压缩，改用 Tailwind 类名统一控制表单项间距，确保校验信息展示空间。

## v1.4.2 (2025-12-29)
- [功能] **价格历史追踪**：新增 `buff_price_history` 模块，全量记录扫描过程中的市场价格波动。
- [后端] **Java 基础设施升级**：
  - 新增 `BuffPriceHistory` 实体类与 Mapper。
  - 实现 `getAveragePrice` 业务逻辑，支持查询商品近期（24h/7d）的市场均价。
- [爬虫] **倒卖风控引擎**：
  - **自动价格采样**：在执行扫描任务时同步更新历史价格表，无需额外调度。
  - **均价偏离度风控**：引入 `get_avg_price_24h` 检查机制。如果当前市场底价偏离 24 小时均价过高（默认 >15%），系统将自动拦截，防止落入恶意抬价的“钓鱼盘”陷阱。
- [优化] **数据解析增强**：`BuffSpider` 现在会额外提取饰品的“在售数量” (`sell_num`)，为后续分析市场深度提供数据支持。

## v1.4.1 (2025-12-29)
- [功能] **任务类型解耦**：将“炼金扫货”与“站内倒卖”功能彻底分离。
  - **炼金扫货 (Sniping)**：保持原有逻辑，严格根据设定的最高价格和磨损区间进行精准捡漏。
  - **站内倒卖 (Flipping)**：新增利润优先模式。系统自动根据 Buff 当前市场最低售价（Sell Min Price）计算扣除手续费（2.5%）后的预期利润，仅在利润达标时触发下单。
- [后端] 升级 Java 实体类与 DTO，支持 `taskType` 和 `minProfit` 字段。
- [爬虫] 增强 `BuffSpider` 解析能力，实时获取饰品市场定价信息；重构 `TaskScanner` 扫描逻辑，支持多模式并行调度。

## v1.4.0 (2025-12-29)
- [架构] 彻底切换 Redis 驱动从 Lettuce 到 **Redisson**。
- [修复] 解决 Redis 连接长时间闲置后断开的问题：利用 Redisson 强大的连接管理机制和内置的 `ping-connection-interval` (30s) 自动探活功能，彻底消除“断卡”现象。
- [优化] 精简 `RedisConfig` 配置类，移除复杂的 Lettuce 补丁逻辑，提升系统可维护性。

## v1.3.43 (2025-12-29)
- [撤回] 撤回 v1.3.43 中针对 Lettuce 的探活修复方案，改为 v1.4.0 的 Redisson 架构升级。

## v1.3.42 (2025-12-29)
+- [功能] 爬虫模块新增“测试模式”：注释了真实下单 API 调用，改为模拟下单成功并发送通知，方便在服务器上进行长周期调度测试而不产生真实订单。
+
 ## v1.3.41 (2025-12-28)
- [优化] 任务配置的执行预览文案现在会显示具体的下一次启动时间点，而不仅仅是 Cron 描述
- [修复] 彻底解决商品列表页“扫货”弹窗无法打开的问题（移除父容器 `display: none` 并改用组件内 `v-if` 控制渲染）
- [修复] 解决商品列表页“扫货”弹窗可能因后台任务列表请求超时（ECONNABORTED）而无法正常打开的问题
- [修复] 解决 TaskConfig.vue 中 `handleEdit` 在初始化前被访问导致的运行时错误
- [优化] 任务配置弹窗布局优化，解决报错提示被遮挡的问题，并支持自动滚动到错误项
- [优化] 编辑任务时 Cron 表达式默认为空，打开可视化配置时默认为每秒执行
- [功能] 商品列表页“扫货”功能重构，直接复用任务配置弹窗，提升操作连贯性
- [功能] 增加 `openWithGoods` 接口支持从外部直接传入商品信息打开新增任务弹窗

### 任务配置灵活性增强：支持多单位选择
- **持续时间与扫描间隔单位自定义 ([TaskConfig.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/views/TaskConfig.vue))**：
  - 将原本固定的“分钟”和“秒”单位升级为可选下拉框，支持“秒/分/时/日/月”多种单位。
  - **智能换算逻辑**：前端在提交时自动将用户输入的数值转换为后端基准单位（持续时间->分钟，扫描间隔->秒），确保与现有 Python 爬虫逻辑完全兼容。
  - **自动回显优化**：编辑任务时，系统会自动将存储的基准值换算为最合适的 UI 单位进行显示（例如 1440 分钟自动显示为 1 天），提升了长周期任务的可读性。
  - **BEM 布局适配**：使用 Flex 布局对表单项进行重构，确保数值输入框与单位选择框在视觉上保持对齐。
- **扫描间隔安全下限与风控提示 ([TaskConfig.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/views/TaskConfig.vue))**：
  - **右置内嵌提示**：将帮助图标与 Tooltip 内嵌至输入框内部右侧（利用 `suffix` 插槽），符合主流 UI 设计规范，视觉更平衡。
  - **智能自动校正**：新增 `blur` 失焦校验逻辑。若用户手动输入小于 5 秒的数值，系统会自动将其重置为 5 秒，并弹出警告提示，实现“即时输入即时校正”。
  - **双重校验锁**：
    - **UI 层限制**：在“秒”单位下，输入框最小值强制设为 5。
    - **提交层拦截**：表单校验规则确保换算后的基准秒数不低于 5 秒。
  - **动态单位适配**：当用户切换单位至“秒”时，系统会自动将小于 5 的数值重置为安全值。
- **Cron 可视化编辑器体验优化 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **任务配置 Cron 表达式逻辑优化**：
    - 默认值设为“每秒执行 (* * * * * ?)”，业务上视为“立即启动”。
    - **优化文案显示**：当 Cron 为每秒执行时，预览区域显示为“立即启动”；否则显示具体的定时启动描述。
    - **判定逻辑统一**：统一了 Cron 编辑器与任务预览区域的立即启动判定逻辑。
    - **UI 布局优化**：优化了 Cron 编辑器的 UI 布局，预览区更加清晰并增加了“立即启动”状态标签。
  - **解决弹窗抖动与溢出**：为编辑器内容区设置固定高度（180px）及滚动条，彻底解决了切换“日/周”等选项卡时因高度剧烈变化导致的弹窗位移（乱跳）问题，并增加了视口边界约束以适配小屏幕设备。

### 交互体验升级：运行计划逻辑闭环
- **逻辑分组可视化**：将 Cron 表达式、持续时间、扫描间隔整合至统一的“运行计划”面板，视觉层次更清晰。
- **智能语义化预览**：
  - **Cron 表达式翻译**：集成 `cronstrue` 国际化组件，将晦涩的 Cron 表达式实时翻译为自然语言（如：“每分钟”、“每天 10:00”等），极大地提升了配置可读性。
  - **实时执行摘要**：根据用户配置动态生成完整的业务逻辑描述（如：“任务将在每分钟启动，运行 1 小时后停止”），彻底消除配置歧义。

## v1.3.40 (2025-12-28)
### Cron 编辑器 UI 细节优化
- **间距优化 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON\niro\niro-client\src\components\CronEditor.vue))**：
  - 在所有配置选项的文字描述与输入框、下拉框之间添加了适当的边距（Margin）。
  - 使用 `span` 标签包裹文案并配合 TailwindCSS 的 `mx-1`、`mr-1` 等类，解决了文案与组件紧贴在一起的问题，提升了界面的美观度与易读性。

## v1.3.39 (2025-12-28)
### Cron 编辑器功能增强：支持“最后一天” (last)
- **全面开启 APScheduler last 特性支持 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 移除了 UI 中关于“APScheduler 不支持本月最后一天”的错误警告，正式开放该功能。
  - **表达式生成优化**：将 `formatUnit` 的输出格式从 Quartz 风格的 `L` 调整为 APScheduler 语义的 `last`（如 `last` 表示月末最后一天，`last sun` 表示本月最后一个周日）。
  - **解析逻辑兼容**：增强了 `parsePart` 解析器，同时支持识别回传的 `last` 关键字与传统的 `L` 字符，确保旧数据与新格式的无缝迁移。
  - **预览逻辑适配**：在 `executeParse` 中增加了自动转换逻辑，在预览时将 `last` 转换回 `L` 以适配 `cron-parser` 库的解析规则，确保执行时间预览依然准确。
- **后端调度适配 ([task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py))**：
  - 增强了后端任务扫描器的解析能力，自动识别 Cron 表达式中的 `last` 关键字并正确映射到 APScheduler 的 `day` 和 `day_of_week` 参数，实现真正的月末调度支持。

## v1.3.38 (2025-12-28)
### Cron 编辑器逻辑与 UI 深度修复
- **修复时间单位判定失效问题 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 重构了 `formatUnit` 函数，将判断逻辑从响应式对象对比改为显式的 `unit` 字符串对比。这解决了在某些情况下（如 Vue 响应式代理影响）导致秒、分、时无法正确识别为时间单位，从而错误回退到 `*` 的问题。
  - 确保了在未明确配置时间时，表达式各部分能严格遵循默认值（秒/分/时为 `0`，日/月为 `1`），彻底消除了“每秒执行”的异常行为。
- **优化 UI 布局与组件样式**：
  - 修复了单选框内部 `t-select` 下拉框样式异常变宽的问题。通过移除 `flex: 1` 并强制设定 `width: auto`，恢复了可视化编辑器的整洁布局。
  - 对 Tab 标签页和单选框组的间距进行了微调，进一步提升了移动端和小屏幕下的显示效果。

## v1.3.37 (2025-12-28)
### Cron 编辑器鲁棒性增强
- **修复特定配置回退逻辑 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 增强了 `formatUnit` 函数，当用户选择“指定”模式但未勾选任何具体数值时，系统将不再盲目回退到 `*`（每秒/每分），而是智能回退到 `0`（对于时间单位）或 `1`（对于日期单位）。这有效防止了因操作失误导致的“每秒执行”风险。
  - **排序逻辑优化**：在生成“指定”模式的表达式时，自动对选中的数值进行从小到大排序（如 `5,1,10` -> `1,5,10`），提升了生成的 Cron 表达式的标准性与可读性。
- **初始化逻辑合并**：清理了冗余的 `onMounted` 钩子，统一了组件加载时的状态初始化流程，确保新建与编辑模式下的行为高度一致。

## v1.3.36 (2025-12-28)
### Cron 编辑器交互与默认值优化
- **优化默认执行频率 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 将秒、分、时的默认状态由“每（*）”修改为“指定 0”。此项改动解决了新建任务时预览显示“每秒执行”的问题，避免了因误配置导致的系统负载过高风险。
  - **初始化逻辑修正**：在组件挂载和重置时，默认将“日”设置为“每（*）”，确保生成的 Cron 表达式在初始状态下即为有效可运行状态。
- **UI 文案与解析逻辑对齐**：
  - 修正了周字段“步进”模式下的 UI 描述，从“每周执行一次”改为“每天执行一次”，更准确地描述了 `1/1` 等表达式在周字段上的实际业务含义（APScheduler 语义）。
  - **增强解析兼容性**：统一了前端对周几字段（数字 0-7 与英文缩写）的解析逻辑，确保从后端读取的各种格式表达式都能正确回显到 UI 界面。

## v1.3.35 (2025-12-28)
### 后端 Cron 调度兼容性深度优化
- **增强周字段解析逻辑 ([task_scanner.py](file:///e:/CodeSpace/PYTHON/niro/niro-spider/spiders/task_scanner.py))**：
  - 重构了后端任务扫描器的周几映射函数，支持全小写英文缩写（`mon`, `tue`...）及由其构成的列表（`,`）和范围（`-`）。
  - **跨平台兼容性**：保留了对 Quartz 数字格式（1=Sun）的自动转换逻辑，确保了前端可视化编辑器与后端 APScheduler 调度引擎在周几定义上的完美统一。
- **周名标准化 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 将 `weekOptions` 的 `value` 标准化为全小写英文缩写（如 `SUN` -> `sun`），以完全匹配 APScheduler 的原生首选格式，解决了之前因大小写或缩写不一致导致的后端解析失败问题。
### Cron 编辑器字段精简与错误修复
- **移除“年”选项卡 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 彻底移除了“年”配置选项卡及其相关的响应式状态与逻辑。
  - **修复核心报错**：解决了由于 Cron 表达式包含 7 位字段导致 `cron-parser` 或后端调度器报错 `Invalid cron expression, too many fields` 的问题。
  - **简化生成逻辑**：现在生成的 Cron 表达式固定为 6 位（秒、分、时、日、月、周），更符合主流调度引擎的默认预期。
  - **解析鲁棒性提升**：同步更新了 `parseCron` 逻辑，不再尝试解析第 7 个字段，提升了对不同来源表达式的兼容性。

## v1.3.33 (2025-12-28)
### Cron 编辑器 UI 与交互回退及优化
- **选项卡顺序还原 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 还原了“月”选项卡的位置，将其重新放置在“日”与“周”之间，符合标准的 Cron 字段顺序（秒、分、时、日、月、周、年）。
- **范围限制强化**：
  - 在 `t-input-number` 组件上直接增加了 `:min` 和 `:max` 的互相约束（如 `rangeStart` 的最大值为 `rangeEnd - 1`），从 UI 层面彻底杜绝了“开始时间大于结束时间”的非法输入，解决了预览报错问题。

## v1.3.32 (2025-12-28)
### Cron 编辑器鲁棒性增强
- **范围输入自动校验 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 增加了对所有时间单位（秒、分、时、日、月、年、周）“周期”模式下的输入限制。
  - 通过 Vue Watchers 实现了 `rangeStart` 与 `rangeEnd` 的自动联动：当用户设置的开始时间大于等于结束时间时，系统会自动将另一端调整到合理范围，彻底解决了 `cron-parser` 因 `Invalid range` (如 4-1) 导致的预览报错。

## v1.3.31 (2025-12-28)
### Cron 编辑器 APScheduler 兼容性与稳定性修复
- **周名标准化 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 将 `weekOptions` 的 `value` 从数字改为英文缩写 (`SUN-SAT`)，彻底解决了 Java/Python/JS 之间对“周几是第一天”的定义冲突，并原生适配 APScheduler。
- **符号规范调整**：
  - 修改 `formatUnit` 的默认 `noneSymbol` 为 `*`，符合 APScheduler 的强制规范。
- **预览引擎重构**：
  - 增强了 `executeParse` 的鲁棒性，显式开启 `hasSeconds` 支持秒级解析。
  - 在计算预览前将 `?` 自动转换为 `*`，解决了 `cron-parser` 对非标准 Quartz 字符的报错。
- **还原解析逻辑增强**：
  - 完善了 `parsePart` 的数字解析逻辑，支持将后端传回的数字或英文周名准确映射回 UI 状态。
- **风险提示**：
  - 在 UI 上针对 APScheduler 不支持的“最后一天 (L)”类型增加了明显的橙色警告提示。

## v1.3.30 (2025-12-28)
### Cron 编辑器 UI/UX 深度优化
- **彻底移除解析预处理互斥逻辑**：
  - 删除了 `calcNextExecutions` 中强制将“周”设为 `?` 的逻辑，现在解析器会完全尊重用户在 UI 上的所有配置，不再进行隐式修正。

## v1.3.29 (2025-12-28)


## v1.3.18 (2025-12-27)
### Cron 解析器导入兼容性加强修复
- **导入方式调整 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 将 `import cronParser` 改为 `import * as cronParser`，以更好地支持 CJS 库在 Vite (ESM) 环境下的加载。
  - 增强了 `parser` 对象的探测逻辑，依次尝试 `module`、`module.default` 等多种导出结构，彻底解决 `parseExpression is not a function` 的顽疾。

## v1.3.17 (2025-12-27)
### Cron 逻辑与兼容性终极修复
- **解析逻辑全面增强 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **规范严格对齐**：在计算预览前，不仅处理“日”与“周”的冲突，还增加了对“日”和“周”同时为 `*` 的非法场景修复，确保符合 Quartz 规范。
  - **ESM 兼容性重构**：优化了对 `cron-parser` 的调用路径检测，采用更通用的 `default` 检查逻辑，彻底解决在不同构建环境下的运行报错。
  - **字符串生成优化**：重构了 `cronString` 的计算逻辑，使用数组过滤方式彻底解决末尾多余空格问题，避免了解析器因格式微差而报错。
  - **体验细节优化**：精简了预览区的错误提示，只保留核心原因，提升了界面的整洁度。

## v1.3.16 (2025-12-27)
### Cron 解析器变量定义修复
- **变量命名修正 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 修复了 `calcNextExecutions` 函数中 `parser` 变量未定义的错误，统一使用 `import` 引入的 `cronParser` 变量名。
  - 完善了 `cron-parser` 的导入逻辑，确保在 ESM 环境下能正确识别默认导出或命名空间导出。

## v1.3.15 (2025-12-27)
### Cron 解析器 ESM 兼容性深度修复
- **调用逻辑防御式重构 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 针对 `cron-parser` 在不同构建环境（Vite/ESM/CJS）下导出结构不一致的问题，引入了防御式编程逻辑。
  - 自动检测并适配 `parser.parseExpression` 与 `parser.default.parseExpression` 两种调用路径，彻底解决 `is not a function` 运行时报错。

## v1.3.14 (2025-12-27)
### Cron 表达式解析库兼容性修复
- **导入方式修正 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 修复了 `cron-parser` 在 Vite 环境下因默认导入（Default Import）失败导致的 `parseExpression is not a function` 运行时错误。
  - 将导入方式修改为命名空间导入（`import * as cronParser`），确保在 ESM 模式下能正确访问到库的 API 方法。

## v1.3.13 (2025-12-27)
### Cron 预览计算逻辑健壮性增强
- **预览基准时间动态更新 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 修复了预览计算基准时间（`openedTime`）固定不变的问题。现在每次打开弹窗或长时间未操作后重新计算时，会自动更新为当前最新时间，确保预览结果的时效性。
- **Quartz 规范严格校验**：
  - 在计算预览时，自动检测并修复“日”与“周”同时指定的问题。若两者都未设为 `?`，则强制将“周”设为 `?`，以符合 Quartz 标准并防止 `cron-parser` 解析报错。
- **错误反馈增强**：
  - 将 `cron-parser` 的原始错误信息透出到 UI 界面，不再只显示模糊的“格式错误”，极大地方便了复杂表达式的调试与排错。

## v1.3.12 (2025-12-27)
### Cron 预览计算逻辑深度修复
- **解析库兼容性增强 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **开启秒级支持**：明确在 `cron-parser` 配置中开启了 `second: true` 参数。默认情况下解析库仅支持 5 位标准 Cron，开启此项后才能正确解析包含“秒”字段的 6 位或 7 位表达式。
  - **表达式格式标准化**：优化了 `cronString` 的生成逻辑，通过正则过滤掉可能存在的冗余空格，确保输出给解析库的字符串格式严谨。
  - **排序逻辑修复**：修复了“指定”模式下选项排序可能触发的原始数据污染问题，使用非破坏性排序确保预览计算的准确性。

## v1.3.11 (2025-12-27)
### Cron 编辑器交互逻辑与稳定性优化
- **渲染异常修复 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 彻底解决了 `minuteOptions` 在渲染时偶发性未定义的警告，确保分钟下拉框在任何情况下都能正确渲染数据。
- **弹窗交互闭环 ([TaskConfig.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/views/TaskConfig.vue))**：
  - **状态联动**：在任务配置页面引入了 `cronVisible` 状态，实现了可视化配置弹窗的受控显示。
  - **自动关闭**：现在点击 Cron 编辑器底部的“保存配置”或“取消”按钮，会自动关闭当前的浮层弹窗，提升了操作的连贯性。

## v1.3.10 (2025-12-27)
### Cron 编辑器缺陷修复
- **数据源修复 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 修复了“分钟”选项卡中“指定”模式下拉框数据缺失的问题。现在分钟选项已正确初始化（00-59），确保用户可以正常选择具体的分钟执行点。

## v1.3.9 (2025-12-27)
### Cron 编辑器功能增强与自动化预览
- **自动执行预览 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **即时计算**：实现了 Cron 表达式未来 5 次执行时间的自动计算功能。现在无需手动点击“刷新预览”，在打开弹窗或修改表达式时，系统会自动实时更新执行时间列表。
  - **基准时间锚定**：根据弹窗打开的时间点作为计算基准，确保预览结果的直观性和准确性。
  - **异常反馈优化**：当 Cron 表达式无效或无法解析时，预览区域会显示清晰的错误提示，辅助用户快速定位配置问题。

## v1.3.8 (2025-12-27)
### Cron 编辑器 UI 风格统一与布局极致优化
- **风格回归与统一 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **按钮样式还原**：将底部“重置”、“取消”、“保存配置”按钮还原为 TDesign 标准样式（去除了 `shape="round"` 和 `size="small"`），确保与整个前端项目的 UI 风格高度一致。
  - **基础组件对齐**：将编辑器圆角从 `8px` 还原为 `4px`，匹配全局组件的默认视觉语言。
- **空间利用率极致提升**：
  - **垂直高度大幅压缩**：将组件最大高度限制从 `80vh` 降至 `70vh`，并极致压缩了 Tab 高度（36px）、单选框选项内边距（2px）和行高，彻底防止在小分辨率屏幕（如 1080P 缩放后）下溢出。
  - **留白进一步精简**：移除了单选框组的 `gap`，通过包裹层的微量 `padding` 精确控制间距，使表单内容布局更加紧凑。

## v1.3.7 (2025-12-27)
### Cron 编辑器紧凑化优化
- **间距深度压缩 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **选项行压缩**：将单选选项包裹层 `radio-item-wrapper` 的垂直内边距从 `8px` 压缩至 `4px`，并将选项间的 `gap` 从 `6px` 减小至 `2px`，大幅提升了表单区域的利用率。
  - **预览与按钮区精简**：压缩了“执行预览”区域和底部按钮区的垂直内边距（从 `py-4` 降至 `py-3`），并调小了底部按钮和部分文字的尺寸，使整体视觉更紧凑。
  - **高度自适应优化**：将组件最大高度限制从 `85vh` 调整为 `80vh`，配合内部留白的精简，确保在小分辨率屏幕下弹窗内容也能完整显示而不超出屏幕边界。

## v1.3.6 (2025-12-27)
### Cron 编辑器视觉深度优化
- **UI 质感提升 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **容器重塑**：为编辑器添加了圆角（8px）和柔和阴影，增强了组件的悬浮感和现代感。
  - **选项交互增强**：引入了 `radio-item-wrapper`，为每个单选选项添加了悬停（Hover）和激活（Active）状态背景色切换，使当前选中的配置项更加醒目。
  - **预览区域改版**：重新设计了“执行预览”面板，采用渐变色背景展示 Cron 表达式，并优化了未来运行时间的列表展示，提升了信息的可读性和美感。
  - **细节打磨**：统一了按钮形状为圆角（Round），微调了字体大小与颜色层级，优化了整体呼吸感。

## v1.3.5 (2025-12-27)
### Cron 编辑器交互体验微调
- **间距优化 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 增加了 Cron 表达式编辑器中文字与输入组件（数字输入框、选择框）之间的水平间距（从 `mx-1` 提升至 `mx-2`，`ml-2` 提升至 `ml-3`），解决了文字与按钮过于拥挤的问题，提升了视觉呼吸感和操作准确性。

## v1.3.4 (2025-12-27)
### 全局 UI 布局紧凑化优化
- **任务编辑弹窗优化 ([TaskConfig.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/views/TaskConfig.vue))**：
  - 深度压缩表单项间距并优化输入框宽度占比，使垂直布局更紧凑。
  - 为表单区域添加边框容器与淡灰色背景，增强区块感。
  - 为弹窗整体添加柔和阴影（Box Shadow），提升界面层次感。
  - 恢复弹窗标准宽度与字体大小，确保阅读体验一致。
- **Cron 编辑器优化 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - 大幅缩小弹窗整体尺寸，适配不同分辨率屏幕。
  - 优化内部元素（选择框、数字输入框等）宽度占比，使功能排布更精细。
  - 移除单选框冗余边距，改用统一间距控制，提升选项行紧凑度。
  - 压缩标签页内容区与底部按钮区边距，减少无效留白。
- **功能修复**：修复了因输入框过窄导致无法正常操作数字组件的 Bug。

## v1.3.3 (2025-12-27)
### Cron 编辑器深度 UI 优化
- **界面精简与布局重构 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **移除复杂表格**：去除了冗余的时间表达式拆解表格，改为在显著位置直接展示完整的 Cron 表达式字符串，使界面重心更聚焦。
  - **单选框样式修正**：修复了单选按钮与描述文字对齐不当的问题，通过 `align-items: center` 确保两者始终在同一行展示，并优化了选项间的垂直间距。
  - **按需预览机制**：将"最近 5 次运行时间"由实时计算改为"按需预览"。系统在弹窗打开时自动计算一次，后续可通过新增的"刷新预览"按钮手动触发，有效降低界面频繁变动带来的视觉疲劳。
- **交互逻辑与一致性增强**：
  - **选项顺序统一**：按照 XXL-Job 风格重新排列了所有标签页（秒、分、时、日、月、周、年）中的单选框顺序，统一将"指定"选项放在第二位，提升了操作直觉。
  - **文案与符号优化**：统一了"每秒钟"、"每分钟"等描述文案；将所有周期范围分隔符由"到"替换为标准的 `-`。
  - **新增操作按钮**：在底部增加了标准的"确定"与"取消"按钮，并保留了"重置"功能，完善了编辑器的交互闭环。
  - **响应式间距压缩**：进一步压缩了各功能区间的垂直间距，解决了由于功能堆叠导致的溢出和过度留白问题。


## v1.3.2 (2025-12-27)
### Cron 可视化配置与交互优化
- **新增 Cron 可视化编辑器 ([CronEditor.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/components/CronEditor.vue))**：
  - **XXL-Job 风格交互**：采用 TDesign 选项卡架构，提供秒、分、时、日、月、周、年（可选）的全维度配置界面，降低非技术用户配置门槛。
  - **双向数据绑定**：实现可视化界面与 Cron 字符串的实时同步，支持从原始字符串反向解析为界面状态。
  - **交互体验优化**：
    - 解决 Tab 切换导致的容器高度抖动问题，固定组件宽高以确保 UI 稳定性。
    - 集成“一键复制”与“快速重置”功能，提升配置效率。
    - 严格遵循 Cron 语法约束（如日与周的互斥逻辑）。
  - **预览与校验**：
    - 新增“时间表达式”面板，实时展示秒、分、时、日、月、周、年各维度的解析值。
    - 新增“最近5次运行时间”预览，基于 `cron-parser` 实时计算并展示未来 5 次的触发时间点，帮助用户验证 Cron 表达式的正确性。
  - **响应式布局与小屏幕适配**：
    - 移除硬编码的最小高度，引入 `max-height: 85vh` 约束，确保组件在不同分辨率屏幕下均能完整显示。
    - 优化内部滚动机制，为内容区增加自定义细长滚动条，解决功能堆叠导致的溢出问题。
    - 压缩各功能区块间距，显著减少“时间表达式”上方的冗余空白，使界面布局更紧凑。
    - 调整 tab 内容区最小高度，兼顾切换平稳性与垂直空间利用率。
- **任务配置页集成 ([TaskConfig.vue](file:///e:/CodeSpace/PYTHON/niro/niro-client/src/views/TaskConfig.vue))**：
  - 在 Cron 表达式输入框右侧新增“可视化配置”入口，通过 `t-popup` 气泡窗口弹出，保持页面布局简洁。

## v1.3.1 (2025-12-27)
### 全局代码质量与类型安全提升
- **严格类型检查 (TypeScript Strict Typing)**：
  - **彻底移除 `any`**：遵循最新规范，将项目中所有 `any` 显式替换为 `unknown` 或具体业务类型（如 `PageInfo`、`Result<T>` 等），增强了编译期的类型约束。
  - **HTTP 请求类型对齐**：重构 `request.ts` 中的 `RequestInstance` 接口，解决了 `AxiosResponse` 泄露导致的类型不匹配问题，确保 API 返回值直接对应业务 DTO。
- **Lint 规范强制执行 (ESLint Compliance)**：
  - **规则收紧**：在 `eslint.config.js` 中重新启用并强化了 `@typescript-eslint/no-explicit-any` 规则。
  - **全面修复**：解决了包括未使用的变量、无效的 void 类型、冗余导入在内的所有 lint 报错，实现 `pnpm lint:eslint` 零错误通过。
- **UI 组件逻辑优化**：
  - **TaskConfig 稳定性增强**：修复了任务配置页面中商品搜索、分页切换等环节的类型推断错误，优化了 `handleEdit` 时商品数据的回显逻辑。
  - **Layout 交互优化**：修正了侧边栏菜单切换与个人中心下拉菜单的事件处理类型定义。

## v1.3.0 (2025-12-27)
### 扫货任务调度系统升级 (重大更新)
- **双层调度架构 (Two-Tier Scheduling Architecture)**：
  - **核心引擎重构**：引入 `APScheduler` (BackgroundScheduler) 替换原有的死循环扫描模式，实现了任务管理的异步化与高并发支持。
  - **任务同步层 (Task Manager)**：新增 `sync_tasks` 逻辑，每 10 秒自动感知数据库任务状态变化，动态完成 Job 的注册、更新与注销。
  - **任务执行层 (Job Executor)**：每个扫货任务现在拥有独立的线程执行空间，互不阻塞，支持精准的秒级频率控制。
- **定时与自动化增强 (Automation & Scheduling)**：
  - **Cron 定时触发**：支持标准的 Cron 表达式（如 `0 0 * * *`），允许用户预设任务在特定时间点（如零点刷新、限时促销等）自动启动。
  - **运行持续时间限制**：新增 `duration_minutes` 配置，任务启动后可在达到指定分钟数后自动平滑停止，避免无人看守下的过度扫描。
  - **动态扫描频率**：支持按任务独立配置 `scan_interval`，用户可根据商品热门程度灵活调整扫描密度。
- **全链路参数对齐**：
  - **Java 后端扩展**：同步更新 `BuffScanTask` 实体类、DTO 和 Param 校验规则，支持新调度参数的持久化。
  - **前端交互升级**：在 `TaskConfig.vue` 任务配置弹窗中集成 Cron 表达式、持续时间和扫描间隔的配置项，并提供友好的操作提示。
  - **状态机优化**：完善了任务从“定时等待”到“运行中”再到“自动停止”的完整状态流转逻辑。

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
