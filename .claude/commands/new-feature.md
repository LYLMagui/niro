---
description: 创建新的业务功能模块
---

# 新功能开发流程

请按照以下步骤开发新功能：

## 1. 需求理解
确认功能需求，明确：
- 业务目标
- 涉及模块（前端/后端/爬虫）
- 数据流向

## 2. 架构设计
- 使用 arch-guard skill 检查架构边界
- 确定 Controller/Service/Mapper 职责
- 设计数据库表结构（如需要）

## 3. 代码实现
必须使用的 skills：
- coding-standards
- api-consistency-and-safe-build
- code-reuse-and-db-changes

## 4. 验证
- 后端：`mvn compile`
- 前端：`pnpm type-check && pnpm lint`

## 5. 输出
记录：
- 改动的文件
- 改动原因
- 验证结果
- 潜在风险
