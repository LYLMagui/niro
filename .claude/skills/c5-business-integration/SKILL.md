---
name: c5-business-integration
description: "Use when the user mentions C5, C5Game, 饰品买入, 订单同步, 余额查询, 库存抓取, 订单状态, or code under com.niro.sdk.c5. Handle C5 API integration, C5-specific business logic, and debugging of C5-related workflows."
tags: [c5, integration, trading, backend]
platforms: [Claude]
---

# C5 Business Integration

## When to use this skill
- 当需求涉及 C5 或 C5Game 平台接口对接。
- 当需求涉及 `com.niro.sdk.c5` 相关代码、SDK 封装或调用链。
- 当需求涉及饰品买入、订单同步、余额查询、库存抓取、订单状态流转。
- 当需要排查 C5 接口错误码、参数错误、频率限制或业务状态异常。
- 当需要结合数据库或缓存检查 C5 相关任务状态、订单状态或上下游数据一致性。

## Instructions

### Step 1: Confirm the business path
先确认这次改动属于哪条业务链：
- 下单/买入
- 订单同步
- 库存抓取
- 余额校验
- 状态查询/状态回写
- 风控/熔断

明确输入、输出、调用方、下游依赖，再动代码。

### Step 2: Read the relevant implementation first
优先阅读这些位置：
- `niro-sdk` 中 C5 SDK 的接口封装
- `niro-web` 中调用 C5 的 service / job / controller
- 相关 DTO、实体、枚举、错误码处理逻辑
- 如有 SQL / Redis 参与，先确认状态字段和缓存 key 的语义

不要跳过现有实现直接猜接口行为。

### Step 3: Verify the external contract
在改业务代码前，先确认外部协议：
- 请求 URL / Method / Headers / Params
- 响应字段结构
- 成功态与失败态
- 频率限制、幂等要求、签名或鉴权要求

如果项目里已有现成封装，优先以现有封装和当前代码行为为准；如信息冲突，再补查官方或平台文档。

### Step 4: Validate with minimal real data path
如果问题在接口协议、字段含义或状态流转上，先做最小验证：
- 用现有项目代码、测试、脚本或查询确认真实输入输出
- 必要时查询数据库 / Redis，验证订单、用户、商品、余额等关键字段
- 只验证当前问题所需的最小链路，不为了“保险”扩展范围

禁止把临时验证逻辑混进正式业务代码。

### Step 5: Implement with compatibility first
实现时遵守这些边界：
- 优先复用现有 HTTP Client、DTO、错误处理模式
- 不引入一次性抽象
- 不改变无关业务流转
- 涉及余额、下单、状态回写时，优先保证幂等和兼容性
- 如果现有分支很多，先想办法简化状态结构，不要继续堆特判

### Step 6: Verify the full business effect
至少验证以下内容：
- 接口请求参数正确
- 响应解析正确
- 状态流转没有破坏原路径
- 异常场景能落到现有错误处理逻辑
- 数据库 / Redis / 任务状态与预期一致

如果无法完成某项验证，要明确缺口和风险，不要装作验证过。

## Examples

### Example 1: C5 order sync bug
用户说："C5 订单同步状态不对，帮我查一下。"

你应该：
1. 找到订单同步入口和对应 service / job。
2. 阅读 C5 返回状态与本地状态映射。
3. 检查数据库订单记录和缓存状态。
4. 只修正状态映射或状态流转里的真实问题。

### Example 2: C5 inventory integration
用户说："给 C5 库存抓取补一个接口接入。"

你应该：
1. 先找现有 C5 SDK 封装和相似抓取流程。
2. 确认请求参数、分页、响应字段。
3. 复用现有调用模式接入。
4. 验证抓取结果能正确落到现有数据结构。

### Example 3: Balance check before buy
用户说："饰品买入前需要先校验 C5 余额。"

你应该：
1. 确认买入链路入口。
2. 找到余额查询能力和现有异常处理。
3. 把余额校验放到正确的业务边界。
4. 确保不会破坏原有下单流程和错误提示。

## Best practices
1. 先确认业务链路，再改代码，不要上来就 patch。
2. 优先复用现有 C5 封装、DTO 和错误处理模式。
3. 涉及余额、订单、库存时，先看状态结构和幂等性。
4. 只修当前问题，不顺手扩散改造。
5. 临时验证逻辑留在测试或脚本里，不进入正式实现。
6. 如果接口行为不明确，先验证真实输入输出，再写业务判断。

## References
- `D:\MySpace\niro\CLAUDE.md`
- `D:\MySpace\niro\niro-server`
- `D:\MySpace\niro\niro-sdk`
- `D:\MySpace\niro\niro-web`
- C5API文档：https://opendoc.c5game.com/llms.txt
