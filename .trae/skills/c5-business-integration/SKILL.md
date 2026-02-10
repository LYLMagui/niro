---
name: c5-expert
description: C5 平台业务专家。处理任何涉及 C5Game (C5 饰品交易平台) 的 API 对接、订单同步、余额查询、库存抓取及业务逻辑。当需求中出现“C5”、“饰品买入”、“订单状态”或涉及 com.niro.sdk.c5 代码库时，必须优先调用。
allowed-tools: "Read,Write,RunCommand,PostGreSQL,redis,tdesign-mcp-server,SearchCodebase,Grep"
---

# 何时使用

- 涉及 C5 平台的任何业务逻辑开发或修改。
- 涉及 `niro-server` 中 `com.niro.sdk.c5` 包下的 SDK 调用或扩展。
- 涉及 `niro-spider` 中 C5 相关的爬虫、数据解析或网络请求。
- 处理 C5 订单同步、自动下单、余额校验、风控熔断等核心流程。
- 分析或修复与 C5 接口返回错误码（如频率限制、参数错误）相关的 Bug。
- 需要通过 SQL 或 Redis 查看 C5 相关的任务状态或缓存数据。

## 前置步骤

- **文档研读**：在编写任何 C5 相关业务代码前，必须先阅读并理解相关的 API 接口文档。
- **接口验证**：通过编写独立的 Python 脚本来测试接口连通性、参数正确性及响应结构。

## 操作规范 (Operational SOP)

### 文档优先 (Documentation First)

- **定位文档**：根据需求关键词搜索并阅读本地或在线的 C5 接口文档。
- **理解协议**：明确接口的 URL、Method、Headers、Params 以及 Response 结构。

### 脚本验证 (Script Verification)

- **编写脚本**：在编写正式业务代码前，**必须**创建一个临时的 Python 脚本，位置在 `niro-spider` 模块下的测试目录（例如 `\niro\niro-spider\tests\temp_c5_test.py`）。
- **参数构造**：根据接口文档，构造正确的请求参数，如果有必要，调用 `PostGreSQL` 工具从数据库中获取动态参数（如用户 ID、订单号、商品表的 `marketHashName` 等）。
- **真实调用**：使用 `requests` 或项目封装的 `network_util` 发起真实的 API 调用，获取实际的响应数据。
- **分析响应**：打印并分析返回的 JSON 数据，确保后端的响应实体类字段含义与接口返回的 JSON 一致。

#### 可使用的固定参数

- **AppKey**：`32a417bee57a445a9a09e58405686927`
- **tradeUrl**：`https://steamcommunity.com/tradeoffer/new/?partner=838116584&token=ONOlXNTF`

### 业务实现 (Implementation)

- **集成准入**：只有在脚本验证通过，且明确了接口行为（包括正常响应和异常处理）后，才允许将逻辑集成到主工程中。
- **代码参考**：正式代码应参考验证脚本中的参数构造和解析逻辑，但需适配项目的工程规范（如使用项目封装好的 HTTP Client）。
- **安全保障**：涉及余额和下单的逻辑，必须参考 `backend_rules.md` 中的高并发保护规约。
