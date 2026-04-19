# OCR 轻量 HTTP 服务设计

**目标：** 将现有 `ocr_demo` Python 脚本包装成一个轻量 HTTP 服务，返回结构化识别结果，复用当前 ROI 与解析逻辑，并为后续 Java 调用提供稳定接口。

**架构：** 保留现有 `ocr_client.py` 与 `parser.py` 作为核心识别层；新增一个可复用的服务层统一组织图片加载、ROI OCR、字段筛选；再在最外层增加一个极薄的 HTTP 接口层。前端不直连 Python，统一由 Java 接口接收上传并转发到 Python OCR 服务；对象存储仅作为备份/展示旁路，不进入 OCR 主识别链路。

**技术栈：** Python 3.11、PaddleOCR、OpenCV、Flask

---

## 设计背景

当前 `ocr_demo/main.py` 已经能在目标图片上稳定提取：

- 饰品名称
- 价格
- 磨损

并且价格与磨损已经收敛到专用 ROI。现阶段的问题不是识别逻辑，而是调用形态仍然是单机脚本，不适合被 Java 服务稳定调用。

目标不是把它做成通用 OCR 平台，而是做一个内部轻量能力服务。这个服务只解决一件事：接收一张图片，返回结构化识别结果。任何不直接服务于这个目标的内容都不进入当前范围。

## 设计决策

### 1. 服务形态

采用一个最小 HTTP 服务，对外暴露两个接口：

- `GET /healthz`
- `POST /ocr/recognize`

不引入任务系统、消息队列、异步处理、对象存储 SDK、鉴权模块或配置中心。当前场景是内部服务调用，先保证链路最短、实现最薄。

### 2. 入参方式

`POST /ocr/recognize` 使用 `multipart/form-data`，只接收一个表单字段：

- `file`: 图片文件，必填

不支持 URL、对象存储 key、本地路径等额外模式。原因很简单：当前主链路最快的是 Java 接收上传后直接把图片二进制转发给 Python；对象存储只是备份与展示旁路，不该进入 OCR 主链路。

### 3. 返回结构

Python 服务定义响应数据结构，并作为 Java 与前端的事实来源。成功响应直接返回扁平结构：

```json
{
  "name": "AUG|后发制人",
  "price": 3.87,
  "wear": 0.069131486
}
```

字段约束：

- `name`: `string | null`
- `price`: `number | null`
- `wear`: `number | null`

只要图片读取成功且 OCR 流程执行完成，就返回 `200`。单个字段识别失败时返回 `null`，不把整次请求判为失败。这比再包一层 `success/data/message` 更轻，也更符合内部服务调用场景。

### 4. 异常模型

异常响应只覆盖三类：

#### 400 `INVALID_REQUEST`
用于请求本身不合法，例如未上传文件、空文件。

```json
{
  "code": "INVALID_REQUEST",
  "message": "file is required"
}
```

#### 422 `INVALID_IMAGE`
用于文件存在但不是有效图片，或者 OpenCV 无法解码。

```json
{
  "code": "INVALID_IMAGE",
  "message": "unable to decode image"
}
```

#### 500 `INTERNAL_ERROR`
用于 PaddleOCR 执行异常或服务内部未处理异常。

```json
{
  "code": "INTERNAL_ERROR",
  "message": "ocr service failed"
}
```

不引入更复杂的错误分类。当前需求只需要让 Java 明确区分：请求错了、图片坏了、服务挂了。

### 5. Python 内部模块边界

在现有 `ocr_demo` 目录内保持最小拆分：

- `ocr_demo/ocr_client.py`
  - 保持现有 PaddleOCR 初始化与区域识别逻辑。
- `ocr_demo/parser.py`
  - 保持现有文本归一化、字段提取、价格/磨损候选筛选逻辑。
- `ocr_demo/service.py`
  - 新增统一识别入口，例如 `recognize_image(...)`。
  - 职责：组织整图 OCR、名称 ROI、价格 ROI、磨损 ROI、聚合最终结构。
- `ocr_demo/app.py`
  - 新增 HTTP 服务入口。
  - 职责：处理请求、校验文件、调用 `service.py`、返回 JSON。
- `ocr_demo/main.py`
  - 保留为本地调试脚本。
  - 改为复用 `service.py`，不再自己承载主业务逻辑。

这样拆分后，每层职责清楚：

- `ocr_client.py` 只关心 OCR 引擎和 ROI 识别
- `parser.py` 只关心文本解析和候选选择
- `service.py` 只关心识别流程编排
- `app.py` 只关心 HTTP 协议层

### 6. 模型生命周期

`OcrClient` 必须在服务启动时初始化一次，并在进程内复用。

不能每次请求都重新创建 `PaddleOCR`。那样启动成本太高，完全违背“轻量服务”的目标。正确做法是把模型实例作为全局单例或应用级单例，HTTP 请求只调用识别函数。

### 7. Java 与前端调用边界

本次不设计 Java 和前端的具体实现，但调用方式要固定：

#### 前端
前端只调用 Java 接口，不直接访问 Python OCR 服务。

#### Java
Java 作为总入口，负责：

1. 接收前端上传图片
2. 同步转发图片二进制到 Python `/ocr/recognize`
3. 按业务需要将原图备份到对象存储
4. 将 Python 返回结果传回前端或做最小封装后返回

推荐链路：

```text
Frontend -> Java -> Python OCR
                -> Object Storage (backup/display only)
```

对象存储不进入 OCR 主识别链路，因为那会额外增加一次上传和一次下载，直接拉长识别时间。

## 接口定义

### `GET /healthz`

用于存活检查。

成功返回：

```json
{
  "status": "ok"
}
```

### `POST /ocr/recognize`

请求头：

```text
Content-Type: multipart/form-data
```

表单字段：

- `file`: 图片文件，必填

成功返回：

```json
{
  "name": "AUG|后发制人",
  "price": 3.87,
  "wear": 0.069131486
}
```

## 受影响文件

### 修改文件

- `ocr_demo/main.py`
  - 从脚本入口改为复用服务层能力，继续保留本地调试用途。
- `ocr_demo/requirements.txt`
  - 增加 HTTP 服务所需最小依赖。

### 新增文件

- `ocr_demo/service.py`
  - 统一封装识别流程。
- `ocr_demo/app.py`
  - 暴露轻量 HTTP 服务。

### 保持不动的核心逻辑文件

- `ocr_demo/ocr_client.py`
- `ocr_demo/parser.py`

除非实现时发现必须修正的 bug，否则不主动改动 ROI 和解析规则。

## 验收标准

- Python 服务可以启动并稳定提供 HTTP 接口。
- `GET /healthz` 返回 `200` 和 `{\"status\": \"ok\"}`。
- `POST /ocr/recognize` 能接收一张图片并返回结构化结果。
- 在当前目标样例图上，返回结果应为：
  - `name = "AUG|后发制人"`
  - `price = 3.87`
  - `wear = 0.069131486`
- 某字段识别不到时返回 `null`，不因为单字段失败导致整次请求失败。
- 缺少文件、非法图片、内部异常三类错误能返回明确状态码和错误结构。
- 服务启动后 OCR 模型只初始化一次，不为每个请求重复初始化。

## 风险与兼容性

- **风险：** PaddleOCR 首次初始化仍然较慢，服务冷启动会有明显耗时。
- **控制方式：** 将模型初始化前置到进程启动阶段，而不是请求阶段。
- **风险：** 当前 ROI 基于既定界面布局，分辨率同比例缩放通常可用，但布局变化时识别可能漂移。
- **控制方式：** 当前版本不扩展动态 ROI 校准，先保持已验证模板；后续如果业务截图源发生变化，再单独设计升级方案。
- **兼容性：** 本设计不改变现有解析逻辑，只改变调用入口，因此对已验证样例的结果兼容性风险较低。

## 验证方式

- 本地启动 Python HTTP 服务。
- 使用样例图片通过 HTTP 上传调用 `/ocr/recognize`。
- 校验返回 JSON 是否符合约定结构。
- 校验目标图片返回值是否与当前已验证结果一致。
- 校验缺文件和非法图片请求是否返回正确错误码。
