---
name: find-skills
description: 帮助用户发现并安装 Agent 技能。当用户询问“如何做 X”、“帮我找一个用于 X 的技能”、“有没有技能可以...”或表示有兴趣扩展能力时使用。当用户寻找的功能可能存在于某个可安装的技能中时，应使用此技能。
---

# 查找技能 (Find Skills)

此技能帮助你从开放 Agent 技能生态系统中发现并安装技能。

## 何时使用此技能

当用户出现以下情况时使用此技能：

- 询问“如何做 X”，其中 X 可能是已有技能支持的常见任务
- 说“帮我找一个用于 X 的技能”或“有没有用于 X 的技能”
- 询问“你能做 X 吗”，其中 X 是一项专业能力
- 表示有兴趣扩展 Agent 的能力
- 想要搜索工具、模板或工作流
- 提到希望在特定领域（设计、测试、部署等）获得帮助

## 什么是 Skills CLI？

Skills CLI (`npx skills`) 是开放 Agent 技能生态系统的包管理器。技能是模块化的包，通过专业知识、工作流和工具来扩展 Agent 的能力。

**关键命令：**

- `npx skills find [query]` - 以交互方式或通过关键字搜索技能
- `npx skills add <package>` - 从 GitHub 或其他来源安装技能
- `npx skills check` - 检查技能更新
- `npx skills update` - 更新所有已安装的技能

**浏览技能1：** https://skills.sh/
**浏览技能2：** https://skillsmp.com/zh

## 如何帮助用户查找技能

### 第一步：理解需求

当用户寻求帮助时，请明确：

1. 领域（例如：React, testing, design, deployment）
2. 具体任务（例如：writing tests, creating animations, reviewing PRs）
3. 这是否是一个足够常见、可能已存在对应技能的任务

### 第二步：搜索技能

使用相关查询运行 find 命令：

```bash
npx skills find [query]
```

例如：

- 用户问“如何让我的 React 应用更快？” → `npx skills find react performance`
- 用户问“你能帮我审查 PR 吗？” → `npx skills find pr review`
- 用户问“我需要创建一个更新日志” → `npx skills find changelog`

命令将返回如下结果：

```
Install with npx skills add <owner/repo@skill>

vercel-labs/agent-skills@vercel-react-best-practices
└ https://skills.sh/vercel-labs/agent-skills/vercel-react-best-practices
```

### 第三步：向用户展示选项

当你找到相关技能时，向用户展示：

1. 技能名称及其作用
2. 可运行的安装命令
3. skills.sh 上的详情链接

示例回复：

```
我找到了一个可能对你有帮助的技能！"vercel-react-best-practices" 技能提供了
来自 Vercel 工程团队的 React 和 Next.js 性能优化指南。

安装命令：
npx skills add vercel-labs/agent-skills@vercel-react-best-practices

了解更多：https://skills.sh/vercel-labs/agent-skills/vercel-react-best-practices
```

### 第四步：提供安装服务

如果用户希望继续，你可以为他们安装该技能：

```bash
npx skills add <owner/repo@skill> -g -y
```

`-g` 标志表示全局安装（用户级别），`-y` 跳过确认提示。

## 常见技能分类

搜索时，参考这些常见分类：

| 分类 (Category)  | 示例查询 (Example Queries)               |
| ---------------- | ---------------------------------------- |
| Web 开发         | react, nextjs, typescript, css, tailwind |
| 测试             | testing, jest, playwright, e2e           |
| DevOps           | deploy, docker, kubernetes, ci-cd        |
| 文档             | docs, readme, changelog, api-docs        |
| 代码质量         | review, lint, refactor, best-practices   |
| 设计             | ui, ux, design-system, accessibility     |
| 生产力           | workflow, automation, git                |

## 有效搜索技巧

1. **使用具体关键词**："react testing" 比单纯的 "testing" 更好
2. **尝试替代术语**：如果 "deploy" 没有结果，尝试 "deployment" 或 "ci-cd"
3. **检查热门来源**：许多技能来自 `vercel-labs/agent-skills` 或 `ComposioHQ/awesome-Codex-skills`

## 当未找到技能时

如果不存在相关技能：

1. 告知用户未找到现有技能
2. 提议使用你的通用能力直接协助完成任务
3. 建议用户可以使用 `npx skills init` 创建自己的技能

示例：

```
我搜索了与 "xyz" 相关的技能，但没有找到匹配项。
我仍然可以直接协助你完成此任务！你需要我继续吗？

如果这是你经常做的事情，你可以创建自己的技能：
npx skills init my-xyz-skill
```

## 当找到相关技能时

如果找到相关技能：
1. 确认技能是否符合用户需求
2. 确认技能的编写语言
3. 如果技能是用英文或其他语言编写的，直接帮用户翻译为中文
4. 翻译后的技能名称和描述应符合中文的语法和习惯，且语义不能与英文版本有显著差异

