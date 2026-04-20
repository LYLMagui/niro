---
name: solo-pm-workflow
description: 面向独立开发者的产品经理分流 skill。先识别用户当前处于需求梳理、竞品分析还是原型展示阶段，再路由到对应参考 skill；当需求尚未成型时优先引导到 PRD，当需要市场判断时引导到竞品分析，当需求已明确且需要可视化页面时引导到 HTML 原型生成。
tags: [product-management, prd, competitive-analysis, prototype, solo-founder]
platforms: [Claude]
---

# Solo PM Workflow

## When to use this skill
- 独立开发者有产品想法，但不知道该先写需求、先看竞品，还是先出原型
- 用户希望把“想法 → PRD → 竞品 → 原型”串成一个统一入口
- 用户描述比较模糊，需要先判断当前最合适的下一步
- 用户明确提到产品设计、需求梳理、竞品判断、页面原型中的任一项，但不确定该用哪个 skill

## Instructions

### Step 1: 判断用户当前阶段
先根据用户意图把请求分到下面三类之一，不要一上来把所有流程都跑一遍。

1. **需求澄清 / PRD 阶段**
   触发信号：
   - “我有个想法，帮我梳理一下”
   - “帮我写需求 / 写 PRD / 拆功能”
   - “我不知道用户是谁、痛点是什么、需求怎么落地”
   - “帮我把产品想法整理成文档”

   路由到：`./references/prd-generator/SKILL.md`

2. **竞品 / 定位判断阶段**
   触发信号：
   - “帮我分析竞品”
   - “看看别人怎么做”
   - “这个方向有没有机会”
   - “帮我对比 A、B、C 产品”
   - “我想知道该怎么定位差异化”

   路由到：`./references/competitive-analysis/SKILL.md`

3. **原型 / 页面可视化阶段**
   触发信号：
   - “帮我快速出一个页面原型”
   - “我已经有需求了，想先看页面效果”
   - “帮我做高保真 HTML 原型”
   - “我想做一个可演示的 Web 页面”

   路由到：`./references/html-style-generator/SKILL.md`

### Step 2: 用最小判断规则分流
按下面规则处理：

1. **想法模糊、需求未定** → 优先去 `prd-generator`
2. **需求大致明确，但不知道市场位置或差异化** → 去 `competitive-analysis`
3. **需求已明确，主要想看交互或视觉效果** → 去 `html-style-generator`
4. **用户同时想要完整流程** → 按顺序执行：
   - 先 `prd-generator`
   - 再 `competitive-analysis`
   - 最后 `html-style-generator`

### Step 3: 处理模糊请求
如果一句话里同时混有多个目标，先判断用户此刻最缺的是什么：

- 缺“问题定义” → 先 PRD
- 缺“市场参照” → 先竞品
- 缺“可展示结果” → 先原型

如果仍不明确，先只问一个问题：

```text
你现在最想先解决哪一类问题？
1. 把想法梳理成清晰需求
2. 看竞品和定位差异
3. 直接做一个可展示的页面原型
```

### Step 4: 路由时明确说明原因
分流后，直接告诉用户为什么走这个 skill，不要只报 skill 名。

示例：
- 你的问题还停留在“做什么、给谁做、为什么做”，先走 `prd-generator` 更合适。
- 你的需求已经成型，但缺少市场参照，先走 `competitive-analysis`。
- 你的需求已经比较清晰，现在更需要一个能展示和评审的结果，走 `html-style-generator`。

### Step 5: 当用户要完整闭环时给出标准顺序
如果用户说“你帮我从头带到尾”，使用这条默认工作流：

1. `./references/prd-generator/SKILL.md`
2. `./references/competitive-analysis/SKILL.md`
3. `./references/html-style-generator/SKILL.md`

并向用户说明：
- 第一步解决“要做什么”
- 第二步解决“别人怎么做、我们怎么切”
- 第三步解决“怎么展示出来”

## Examples

### Example 1: 模糊产品想法
用户：
```text
我想做一个给独立开发者用的 AI 工具，但现在脑子很乱，帮我整理一下。
```

处理：
- 判定为需求澄清阶段
- 路由到 `./references/prd-generator/SKILL.md`
- 说明原因：当前缺的是需求结构，不是页面样式

### Example 2: 已有方向，想看市场机会
用户：
```text
我想做一个 AI 会议纪要工具，帮我看看和 Otter、飞书妙记比有没有机会。
```

处理：
- 判定为竞品分析阶段
- 路由到 `./references/competitive-analysis/SKILL.md`
- 说明原因：核心问题是市场定位和机会判断

### Example 3: 需求已经确定，想出原型
用户：
```text
我已经把 SaaS 首页结构想清楚了，帮我直接做一个高保真 HTML 原型。
```

处理：
- 判定为原型阶段
- 路由到 `./references/html-style-generator/SKILL.md`
- 说明原因：需求基本确定，下一步是可视化展示

### Example 4: 想走完整流程
用户：
```text
我没有产品经理，想让你带我把一个点子一步步做出来。
```

处理：
- 判定为完整闭环
- 顺序使用：
  1. `./references/prd-generator/SKILL.md`
  2. `./references/competitive-analysis/SKILL.md`
  3. `./references/html-style-generator/SKILL.md`

## Best practices
1. 不要把原型生成当成需求澄清的替代品；需求模糊时优先走 PRD。
2. 不要替用户强行做完整流程；如果用户只想解决当前一步，就只路由当前一步。
3. 分流时始终解释“为什么现在用这个”，而不只是告诉用户 skill 名称。
4. 当用户已经有明确 PRD，再做竞品和原型，避免顺序颠倒。
5. 引用参考 skill 时使用相对路径，保持此 skill 可迁移。

## References
- `./references/prd-generator/SKILL.md`
- `./references/competitive-analysis/SKILL.md`
- `./references/html-style-generator/SKILL.md`
