# Release Notes

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
