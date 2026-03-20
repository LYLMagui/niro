---
name: api-consistency-and-safe-build
description: 负责前后端接口一致性维护、编译检查及防止私自运行的守卫
allowed-tools: "RunCommand"
---

# 前后端接口一致性与编译守卫

## 1. 核心职责 (Core Responsibilities)
- **接口变更管理**：处理所有涉及后端接口修改或前端调用变更的任务。
- **全栈一致性**：确保前后端代码在接口定义、参数传递、响应结构上保持严格一致。

## 2. 操作规范 (Operational SOP)

### 2.1 全局同步修改 (Global Synchronization)
- **双端联动**：当修改前端或后端任一端的接口定义时，**必须**全局搜索并同步修改另一端的对应代码。
- **防止脱节**：严禁只改一端。必须确保 `Request/Response` 模型在前后端完全一致。
  - **后端**：Controller/DTO 变更。
  - **前端**：API 调用/Type 定义变更。

### 2.2 编译与验证 (Compilation & Verification)
- **后端编译**：后端代码修改完成后，**必须**执行编译指令（如 Maven Compile）进行静态检查。
- **修复 Bug**：必须解决所有编译阶段暴露的语法错误、类型不匹配或引用丢失问题，确保代码可构建。

## 3. 铁律与约束 (Strict Constraints)
- **仅编译，不运行 (Compile Only)**：
  - ✅ **允许**：执行编译、构建、静态分析命令。
  - ❌ **禁止**：私自启动后端服务（Boot Run）、执行 `main` 方法或触发任何实际的业务运行逻辑。
  - **理由**：防止在未确认环境下触发风控、污染数据或导致账号被封禁 (Anti-Ban)。
