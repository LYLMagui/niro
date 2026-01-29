---
name: c5-business-integration
description: C5平台业务集成开发专家，负责阅读API文档并编写Python脚本验证接口
allowed-tools: "Read,Write,RunCommand,PostGreSQL,redis,tdesign-mcp-server"
---

# 何时使用

- 如果当前的代码涉及到C5平台的相关业务

## 前置步骤
- **文档研读**：在编写任何 C5 相关业务代码前，必须先阅读并理解相关的 API 接口文档。
- **接口验证**：通过编写独立的 Python 脚本来测试接口连通性、参数正确性及响应结构。

## 操作规范 (Operational SOP)

### 文档优先 (Documentation First)
- **定位文档**：根据需求关键词搜索并阅读本地或在线的 C5 接口文档。
- **理解协议**：明确接口的 URL、Method、Headers、Params 以及 Response 结构。

### 脚本验证 (Script Verification)
- **编写脚本**：在编写正式业务代码前，**必须**创建一个临时的 Python 脚本，位置在测试目录（例如 `niro-spider\tests\temp_c5_test.py`）。
- **参数构造**：根据接口文档，构造正确的请求参数，如果有必要，调用PostGreSQL工具从数据库中获取动态参数（如用户ID、订单号、商品表的markHashNam等）。
- **真实调用**：使用 `requests` 等库发起真实的 API 调用，获取实际的响应数据。
- **分析响应**：打印并分析返回的 JSON 数据，确保后端的响应实体类字段含义与接口返回的JSON一致。

#### 可使用的固定参数
- **AppKey**：32a417bee57a445a9a09e58405686927
- **tradeUrl**：https://steamcommunity.com/tradeoffer/new/?partner=838116584&token=ONOlXNTF

### 业务实现 (Implementation)
- **集成准入**：只有在脚本验证通过，且明确了接口行为（包括正常响应和异常处理）后，才允许将逻辑集成到主工程中。
- **代码参考**：正式代码应参考验证脚本中的参数构造和解析逻辑，但需适配项目的工程规范（如使用项目封装好的 HTTP Client）。
