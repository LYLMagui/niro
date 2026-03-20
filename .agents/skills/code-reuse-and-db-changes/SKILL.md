---
name: code-reuse-and-db-changes
description: 创建新代码和新业务，以及处理数据库变更的规范
allowed-tools: "PostgreSQL,redis"
---

# 新业务开发与数据库变更规范

## 1. 代码复用 (Code Reuse)
- **检查现有资源**：在编写新代码前，必须先全局搜索项目中是否存在可复用的常量类、枚举类或工具类。
- **复用优先**：如果有合适的现有类，必须复用；只有在确信无可用资源时，才允许新建。

## 2. 数据库变更 SOP (Database Changes)
在修改或新增需求涉及数据结构或业务逻辑变更时，**必须**严格遵守以下流程：

1. **结构审计 (Schema Audit)**
   - **强制动作**：必须使用 `PostgreSQL` MCP工具查询当前相关表的结构。
   - **目的**：确保对现有数据模型有准确的理解，避免重复造轮子或破坏现有结构。

2. **变更决策 (Decision Making)**
   - 根据审计结果，判断是**新增字段 (ALTER TABLE)** 还是 **新建表 (CREATE TABLE)**。
   - 优先考虑扩展性与规范性。

3. **设计规范与验证 (Design & Verification)**
   - **强制动作**：在生成具体的 SQL 之前，必须调用 `postgresql-table-design` 技能。
   - **目的**：获取 PostgreSQL 专用的架构设计建议、最佳实践、数据类型选择以及索引策略，确保设计符合高性能与高可用标准。

4. **SQL 交付标准 (SQL Standard)**
   - **语法**：必须符合 PostgreSQL 语法规范。
   - **注释**：必须为每个表和字段添加详细的 `COMMENT`。
   - **索引**：必须为查询字段添加合适的索引 (`INDEX`)，并说明索引设计的理由。
