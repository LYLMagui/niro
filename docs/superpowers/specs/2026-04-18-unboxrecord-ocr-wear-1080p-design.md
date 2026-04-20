# 开箱记录 OCR 磨损识别 1080p 固定模板优化设计

## 目标

在固定 `1920×1080`、固定 UI 布局的截图场景下，提升开箱记录 OCR 对 `wear` 的识别稳定性，重点解决“数字较小、轻微模糊、最后一位或最后几位不稳定”的问题。

本次优化的主目标是：

- `wear` 尽量稳定识别出 9 位及以上有效小数
- 保持 `price`、`name`、`exterior` 现有链路不明显回退
- 不通过整图超分辨率或重型图像处理换精度

## 范围

本次设计只覆盖现有 OCR Python 服务内部的磨损识别链路优化，主要涉及：

- `ocr_demo/service.py`
- `ocr_demo/ocr_client.py`
- `ocr_demo/parser.py`
- `ocr_demo/app.py`

如需补调用日志，可同步调整：

- `niro-server/niro-web/src/main/java/com/niro/web/service/impl/UnboxRecordOcrServiceImpl.java`

本次不改：

- 前端接口结构
- Java `UnboxRecordOcrResultDTO` 字段结构
- Python `/ocr/recognize` 返回结构
- 非固定模板截图的兼容策略

## 已确认前提

本方案建立在以下已确认约束上：

- 截图分辨率必须为 `1920×1080`
- UI 位置固定
- 不再兼容 `1600×900`
- 问题场景是武器详情页下方黑色磨损数字条

这意味着识别问题不再按“多分辨率适配”处理，而按“固定模板精度优化”处理。

## 核心判断

这个问题值得做，而且应该按固定 1080p 模板做专用优化。

原因：

1. 当前场景约束足够稳定，不需要为通用场景付出复杂度。
2. 当前 `wear` 已经接近正确结果，说明问题不是完全识别不到，而是最后几位稳定性不足。
3. 最合适的做法不是整图增强，而是对 `wear` 的固定 ROI 做局部清晰化与多候选选择。

## 总体方案

将 `wear` 识别链路收敛为：

- 固定 1080p 基准 ROI
- 局部裁剪后做轻量增强
- 多个 primary 候选并行采样
- 仅在 primary 候选都不足够可靠时触发 fallback

不做：

- 整图超分辨率
- 整图多轮 OCR 爆搜
- 复杂锚点定位
- 1600×900 兼容分支

## 字段级设计

### 1. 输入约束

OCR 链路只接受 `1920×1080` 截图作为目标输入。

建议在入口层增加明确约束：

- 如果图片尺寸不是 `1920×1080`
- 直接返回明确错误，提示用户上传规范截图

不要在 OCR 内部再做比例缩放兼容，否则会重新把问题带回“多分辨率漂移”。

### 2. wear 主识别链路

#### 2.1 固定 ROI

保留 `wear_primary` 作为主识别区域，继续只覆盖黑色数字条附近的磨损数字。

如果后续验证发现固定 ROI 仍有轻微偏移，可增加 1~2 个邻近 primary ROI 作为候选，例如：

- 稍微向左/向右偏移几个像素比例
- 稍微向上/向下收缩或扩展

这些候选仍属于 primary 范围，不属于 fallback。

#### 2.2 局部增强策略

针对 `wear_primary` 裁剪图，仅做轻量增强，不做重型图像处理。

推荐候选组合：

- `contrast + scale=3`
- `contrast + scale=4`
- `sharpen + scale=4`
- `sharpen + scale=5`

必要时可补一个：

- `gray + scale=4`

这些候选图都来自同一个固定 ROI，只是增强方式不同。

设计意图：

- 放大细小数字
- 增强数字边缘
- 避免整图增强带来的噪声放大

#### 2.3 主结果选择规则

先只在 primary 候选里选最优结果。

排序优先级：

1. 合法 `0.xxxxx...`
2. 小数位更多的优先
3. OCR 置信度更高的优先
4. 预处理结果更稳定的优先

高质量 primary 的判定目标：

- `0 < value < 1`
- 优先 9~12 位有效小数
- 候选字符串格式完整

### 3. fallback 策略

fallback 只作为兜底，不再作为主路径竞争者。

触发条件：

- primary 未识别出合法 wear
- primary 结果格式残缺
- primary 小数位明显不足

fallback 仍然允许：

- 更宽一点的 `wear_fallback` ROI
- `binary` / `binary_inv` 等更激进的预处理

但控制边界必须明确：

- `wear` 最多补 1 次 fallback OCR
- fallback 只在 primary 低质量时运行
- 不允许演化成 region / scale / preprocess 的大范围穷举

### 4. 日志与诊断增强

为了避免后续继续靠猜调参，磨损链路需要输出更细的诊断信息。

建议至少增加：

- `wear_value_text`
- `wear_score`
- `wear_preprocess`
- `wear_scale`
- `wear_region`

保留现有：

- `wear_source`
- `wear_decimals`
- `wear_fallback_used`

这样每次问题图都能直接判断：

- 原始候选字符串到底识别成了什么
- 是哪种增强方式命中的
- 选中结果为什么赢过其他候选

### 5. 与其他字段的关系

本次核心优化只聚焦 `wear`。

但必须确保以下边界：

- `price` 不因磨损优化而回退
- `name` 不因主链路调整被错误改动
- `exterior` 保持现有规则推断与识别能力

也就是说，本次不是重新设计整个 OCR，而是在现有结构内集中修正 `wear` 这条最脆弱的链路。

## 模块职责

### `ocr_demo/service.py`

主改文件，负责：

- 定义固定 1080p 下的 `wear` 主 ROI / fallback ROI
- 编排 `wear` 多 primary 候选采样
- 控制 fallback 触发条件
- 汇总磨损诊断日志

### `ocr_demo/ocr_client.py`

负责补充和复用轻量图像预处理，例如：

- `contrast`
- `sharpen`
- 必要时补 `gray`

不引入重型超分辨率或复杂视觉模型。

### `ocr_demo/parser.py`

负责：

- 强化 `wear` 候选排序逻辑
- 保留候选原始字符串
- 避免低质量短小数压过高质量 primary 结果

### `ocr_demo/app.py`

负责输出 `/ocr/recognize` 请求级日志，并附带增强后的 diagnostics。

### `UnboxRecordOcrServiceImpl.java`

继续只做：

- 图片转发
- 响应解析
- Java 侧调用日志

不承担图像增强逻辑。

## 风险与控制

### 风险 1：增强过重把噪声一起放大

**控制：** 只对固定 `wear` ROI 局部增强，不做整图增强；优先 `contrast` / `sharpen`，不默认上二值化。

### 风险 2：primary 候选过多导致耗时抬升

**控制：** primary 候选控制在 3~5 组以内；fallback 最多 1 次。

### 风险 3：ROI 固定过死，轻微偏移时仍丢尾位

**控制：** 如果单 ROI 不稳，优先增加 1~2 个邻近 primary ROI，而不是引入复杂定位逻辑。

### 风险 4：非 1080p 图片被误传后结果不可控

**控制：** 明确拒绝非 `1920×1080` 输入，不再做兼容补丁。

## 验收标准

### 功能验收

- 问题样例图的 `wear` 比当前结果更接近原图
- `wear` 优先达到 9 位及以上有效小数
- `wear_source` 应尽量命中 `primary`
- `wear_fallback_used` 仅在确有必要时为 `true`

### 稳定性验收

- 同一类 1080p 固定模板截图上，`wear` 的最后一位或最后几位不再频繁抖动
- `price` / `name` / `exterior` 不明显回退

### 性能验收

- 单次识别时间保持在当前量级
- 不因为多候选增强演化成明显慢请求

## 验证方式

1. 使用当前问题图重新识别
2. 记录：
   - `wear`
   - `wear_value_text`
   - `wear_score`
   - `wear_preprocess`
   - `wear_scale`
   - `wear_source`
   - `wear_fallback_used`
3. 再补 2~3 张同模板 1080p 图回归
4. 确认 `price` / `name` / `exterior` 没有明显回退

## 实施顺序

1. 在 `service.py` 收敛 1080p 固定前提
2. 为 `wear_primary` 增加多种局部增强候选
3. 在 `parser.py` 强化 `wear` 候选排序与原始字符串保留
4. 补 diagnostics
5. 用问题图和同模板样例图回归

## 结论

这次优化的正确方向不是去兼容更多分辨率，也不是做整图清晰化，而是利用你已经能控制的前提——固定 1080p、固定 UI——把 `wear` OCR 收敛成“固定 ROI + 局部增强 + 多 primary 候选 + 受控 fallback”的简单高精度方案。

这是足够小、但足够有效的工程解。