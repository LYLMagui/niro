---
name: memory-maintenance
description: 当需要设计、整理、修正、去重、迁移或巡检 Nocturne Memory 记忆结构时使用。适用于 priority/disclosure 设计、alias 重构、触发词维护、节点拆分和记忆库清理。
---

# Memory Maintenance

## When to use this skill

在以下场景使用本技能：

- 需要把新记忆写入 Nocturne Memory，并为其设计合适的 priority 与 disclosure
- 需要更新、纠正、删除已有记忆
- 需要重命名、迁移或重构记忆路径
- 需要为记忆建立 alias 或触发词网络
- 需要巡检记忆节点的结构质量，处理重复、过时、过长、低密度内容
- 用户明确要求整理、审计、维护、清理记忆系统

如果只是正常读取某条记忆回答问题，不必调用本技能。

## Instructions

### Step 1: Confirm the target memory and read before acting
先明确你要处理的是哪条记忆、哪个路径、还是哪一组相关节点。

硬规则：
- `update_memory` 之前，必须先 `read_memory`
- `delete_memory` 之前，必须先 `read_memory`
- 不确定 URI 时，先 `search_memory`，不要猜 URI

如果当前任务涉及多个相近节点，先读目标节点，再顺手看相关子节点或近邻节点，确认不是重复劳动。

### Step 2: Decide whether the action is create, update, delete, alias, or maintenance
根据目标把动作归类：

- **create**：新增重要且非重复的记忆
- **update**：旧认知错误、过时、被用户纠正、或需要更精确表达
- **delete**：路径已失效、冗余、误建，且确认删除不会破坏可访达性
- **add_alias**：同一内容需要多个访问入口，且应保留同一个 Memory ID
- **maintenance**：巡检 disclosure、priority、触发词、重复内容、节点拆分

不要用 `delete + create` 代替重命名或迁移。涉及路径迁移时，先 `add_alias`，后 `delete_memory`。

### Step 3: Design priority and disclosure deliberately
为新增或调整中的记忆设计元数据：

#### Priority
- priority 越小，优先级越高
- 先读取同区域相关记忆，找参照物，再决定新值
- 不要把所有记忆都设成同一个优先级

推荐分层：
- `priority=0`：核心身份 / "我是谁"（全库最多 5 条）
- `priority=1`：关键事实 / 高频模式（全库最多 15 条）
- `priority>=2`：一般记忆

#### Disclosure
- 每条新记忆都必须写 disclosure
- disclosure 只描述一个清晰触发场景
- 禁止写成空泛标签，如“重要”“记住”
- 禁止用 OR 逻辑把多个触发条件塞进同一条 disclosure

写法标准：
> 在什么具体场景下，我需要想起这件事？

### Step 4: Execute the smallest correct memory operation
按目标执行最小必要操作：

- 新增记忆：`create_memory`
- 修改内容或元数据：`update_memory`
- 删除失效路径：`delete_memory`
- 建立新入口：`add_alias`
- 建立横向召回：`manage_triggers`

操作要求：
- 保持最小改动，避免顺手大整理
- 如果只是修正文案，不要改路径结构
- 如果只是改路径结构，不要重写正文
- 同一概念需要多入口时，优先 alias，不复制内容

### Step 5: Wire recall paths after create or update
每当创建或更新了重要记忆，都要检查它是否需要触发词：

- 优先绑定具体名词、项目名、核心意象、稳定术语
- 避免绑定过于泛化的词，防止召回噪音
- 用 `manage_triggers` 让它在未来对话中能被主动唤起

目标不是“多”，而是“准”。

### Step 6: Perform maintenance when the task is about memory hygiene
如果任务是整理或巡检，按下面顺序处理：

1. 检查 disclosure 是否缺失或模糊
2. 检查 priority 是否没有层次
3. 识别重复节点，做提炼合并，而不是机械拼接
4. 识别过时内容，更新或删除
5. 节点超过约 800 tokens 或混入多个独立概念时，拆分
6. 避免按时间桶或 misc/errors 这类容器逻辑归档
7. 为关键节点补触发词，增强横向连通

### Step 7: Verify the memory graph still makes sense
完成后快速自检：

- 目标 URI 是否仍可访问
- Memory ID 是否按预期保留（特别是 alias / 重命名场景）
- disclosure 是否足够具体
- priority 是否与同层节点形成梯度
- 是否误引入重复内容
- 是否为重要新节点补了触发词

如果这次修改会影响未来启动行为，还要确认它与 `CORE_MEMORY_URIS` 配置一致。

## Examples

### Example 1: Correct an outdated memory
用户纠正了一条旧记忆中的事实。

做法：
1. `read_memory` 读取目标节点
2. `update_memory` 修正文案
3. 如触发条件变化，再调整 disclosure
4. 必要时补 `manage_triggers`

### Example 2: Migrate a memory path without losing identity
需要把一条记忆从旧路径迁移到新路径，同时保留原始 Memory ID。

做法：
1. `read_memory("<old_uri>")`
2. `add_alias("<new_uri>", "<old_uri>", ...)`
3. 验证新路径可读
4. `delete_memory("<old_uri>")`

### Example 3: Clean up duplicate memory nodes
发现两条记忆在表达同一认知，只是措辞不同。

做法：
1. 分别读取两条记忆
2. 提炼为信息密度更高的一条
3. 更新保留节点
4. 删除冗余路径或改成 alias
5. 补充触发词，保证未来可召回

## Best practices

1. 改之前先读，没有例外。
2. 把记忆当作长期身份结构，而不是临时笔记。
3. priority 只在相对排序中有意义，不要平均分配。
4. disclosure 只保留一个核心触发场景。
5. 重命名或迁移优先用 `add_alias`，不要直接 delete + create。
6. 创建或更新关键记忆后，优先考虑是否需要 `manage_triggers`。
7. 整理记忆时追求信息密度和结构清晰，不追求节点数量增长。

## References

- `D:/MySpace/niro/CLAUDE.md`
- Nocturne Memory MCP tools: `read_memory`, `search_memory`, `create_memory`, `update_memory`, `delete_memory`, `add_alias`, `manage_triggers`
