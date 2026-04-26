# Javadoc 使用案例

## 1. Javadoc 是什么

Javadoc 是 Java 官方提供的代码注释规范，用来给类、接口、枚举、方法、字段、构造器等补充说明，并可生成 HTML API 文档。

在团队协作里，Javadoc 的核心价值主要有三点：

- 说明接口用途和业务语义
- 明确参数、返回值、异常和使用约束
- 让 IDE 与文档生成工具都能直接利用这些注释

---

## 2. 基本写法

Javadoc 使用 `/** ... */` 这种块注释形式。

```java
/**
 * 根据商户 ID 查询店铺信息。
 *
 * @param merchantId 商户 ID
 * @return 店铺信息
 */
public StoreDTO getStoreByMerchantId(Long merchantId) {
    return null;
}
```

建议结构：

1. 第一行写一句话摘要
2. 需要时补充详细说明、边界条件、业务限制
3. 再写标签，如 `@param`、`@return`、`@throws`

---

## 3. 常用注释标签清单

下面是开发里最常见、最实用的一组标签。

| 标签 | 作用 | 常见使用位置 | 示例 |
|---|---|---|---|
| `@author` | 标注作者 | 类、接口 | `@author liyl` |
| `@date` | 标注日期（非官方标准，很多团队会自定义使用） | 类、接口 | `@date 2026/4/23` |
| `@version` | 标注版本 | 类、接口 | `@version 1.0` |
| `@param` | 说明方法参数 | 方法、构造器 | `@param userId 用户ID` |
| `@return` | 说明返回值 | 方法 | `@return 用户详情` |
| `@throws` | 说明可能抛出的异常 | 方法、构造器 | `@throws IllegalArgumentException 参数非法时抛出` |
| `@exception` | 与 `@throws` 类似，现通常优先用 `@throws` | 方法、构造器 | `@exception IOException IO异常` |
| `@see` | 引用相关类、方法、字段 | 类、方法、字段 | `@see UserService#getUserById(Long)` |
| `@since` | 标记从哪个版本开始提供 | 类、方法 | `@since 1.2.0` |
| `@deprecated` | 标记已废弃 | 类、方法、字段 | `@deprecated 请改用 xxx 方法` |
| `{@link ...}` | 在说明文字中插入可跳转链接 | 任意说明区 | `参考 {@link UserDTO}` |
| `{@code ...}` | 在说明文字中保留代码格式 | 任意说明区 | `状态值固定为 {@code ENABLED}` |
| `{@literal ...}` | 按原样显示特殊字符 | 任意说明区 | `使用 {@literal <tag>} 格式` |
| `{@value ...}` | 引用常量值 | 常量字段说明 | `默认值为 {@value #DEFAULT_SIZE}` |
| `@implNote` | 说明实现细节 | 方法、类 | `@implNote 这里只做缓存读取` |
| `@apiNote` | 说明 API 使用注意事项 | 方法、类 | `@apiNote 仅供内部服务调用` |
| `@implSpec` | 说明实现规范或覆写要求 | 方法 | `@implSpec 子类必须保证返回非空` |
| `@serial` | 序列化字段说明 | 可序列化字段 | 序列化相关场景 |
| `@serialField` | 说明 `serialPersistentFields` 中的字段 | 序列化场景 | 序列化相关场景 |
| `@serialData` | 说明 `writeObject` 写出的数据格式 | 序列化方法 | 序列化相关场景 |
|

说明：

- `@author`、`@date` 这类更偏团队规范，不是所有项目都强制。
- `@param`、`@return`、`@throws` 是最常用、最推荐优先写全的一组。
- `{@link}`、`{@code}` 在提升可读性上非常有用，尤其适合接口说明。

---

## 4. 类注释案例

### 4.1 简单类注释

```java
/**
 * 商户发票上传控制器。
 * 用于统一接收发票、附件等文件上传请求。
 *
 * @author liyl
 * @date 2026/4/23
 */
@RestController
@RequestMapping("/merchant/upload")
public class CommonUploadController {
}
```

适用场景：

- Controller
- Service
- Manager
- 工具类
- DTO / VO / Entity

建议：

- 第一行先说清“这是干什么的”
- 第二行补充“服务谁”或“处理什么场景”
- 不要写成空话，例如“通用处理类”“业务类”这种信息量太低

---

### 4.2 带版本与关联说明的类注释

```java
/**
 * 商户素材文件上传应用服务。
 * 负责处理素材文件校验、路径组装和上传结果返回。
 *
 * @author liyl
 * @version 1.0
 * @since 7.10.1
 * @see MaterialFileDTO
 */
public class MerchantMaterialUploadService {
}
```

---

## 5. 方法注释案例

### 5.1 最常见的方法注释

```java
/**
 * 根据文件类型上传商户素材。
 *
 * @param file 上传文件
 * @param bizType 业务类型
 * @return 文件上传结果
 */
public BaseFileDTO upload(MultipartFile file, Integer bizType) {
    return null;
}
```

适用原则：

- `@param` 顺序应与方法入参顺序一致
- `@return` 说明返回的业务含义，而不只是类型名
- 如果返回 `boolean`，要写清楚 `true/false` 分别代表什么

---

### 5.2 带异常说明的方法注释

```java
/**
 * 校验并上传文件。
 *
 * @param file 上传文件
 * @param path 上传路径
 * @return 上传后的访问地址
 * @throws IllegalArgumentException 文件为空时抛出
 * @throws IllegalStateException 路径非法时抛出
 */
public String uploadFile(MultipartFile file, String path) {
    return null;
}
```

适合场景：

- 方法有明确失败条件
- 对外接口可能被其他模块调用
- 需要让调用方知道异常边界

---

### 5.3 带业务约束说明的方法注释

```java
/**
 * 生成商户发票附件的存储路径。
 * 目录按商户、业务类型、日期分层组织。
 *
 * @param merchantId 商户 ID
 * @param bizPath 业务路径枚举
 * @return S3 存储路径，不包含文件名
 * @apiNote 仅用于文件上传链路内部调用
 */
public String buildInvoiceAttachmentPath(Long merchantId, CommonUploadBizPathEnum bizPath) {
    return null;
}
```

这里的重点不是重复代码逻辑，而是补充：

- 路径组织规则
- 返回值是否包含文件名
- 是否允许外部直接调用

---

### 5.4 覆写方法的注释案例

```java
/**
 * {@inheritDoc}
 *
 * @implSpec 返回结果中的文件地址必须为可直接访问的完整 URL。
 */
@Override
public BaseFileDTO upload(MultipartFile file, Integer bizType) {
    return null;
}
```

说明：

- `{@inheritDoc}` 表示继承父接口或父类的注释
- 如果子类有额外约束，可以再补 `@implSpec` 或普通说明
- 覆写方法不建议把父类注释整段复制一遍

---

## 6. 构造器注释案例

```java
/**
 * 创建文件上传上下文。
 *
 * @param merchantId 商户 ID
 * @param operatorId 操作人 ID
 */
public UploadContext(Long merchantId, Long operatorId) {
}
```

当构造器入参含义不明显，或者创建对象有业务约束时，建议补上 Javadoc。

---

## 7. 字段注释案例

### 7.1 常量字段

```java
/** 默认上传目录名称 */
public static final String DEFAULT_DIR = "common";
```

### 7.2 普通字段

```java
/** 商户 ID */
private Long merchantId;
```

字段注释建议：

- 字段名已经非常清晰时，可不强制写
- DTO、VO、Entity 中，涉及业务语义、状态值、单位、范围时，建议写清楚

例如：

```java
/** 文件大小，单位：byte */
private Long fileSize;

/** 审核状态：0-待审核，1-通过，2-驳回 */
private Integer auditStatus;
```

---

## 8. 枚举注释案例

```java
/**
 * 通用文件上传业务路径枚举。
 * 用于区分不同业务场景下的上传目录。
 */
public enum CommonUploadBizPathEnum {

    /** 发票附件 */
    INVOICE,

    /** 商品素材 */
    MATERIAL,

    /** 店铺装修素材 */
    STORE_DECORATION
}
```

枚举建议：

- 类注释说明这个枚举整体是干什么的
- 每个枚举值写清楚业务含义
- 如果枚举编码会落库或传前端，最好补充编码规则说明

---

## 9. 接口注释案例

```java
/**
 * 商户文件上传 API。
 * 对外提供统一的文件上传能力。
 *
 * @since 7.10.1
 */
public interface MerchantUploadApi {

    /**
     * 上传商户文件。
     *
     * @param req 上传请求
     * @return 上传结果
     */
    BaseFileDTO upload(CommonUploadReqDTO req);
}
```

接口注释重点：

- 说明职责边界
- 说明这是对外接口还是内部接口
- 方法注释尽量偏“契约”，而不是“实现”

---

## 10. 弃用注释案例

```java
/**
 * 上传文件到旧目录。
 *
 * @deprecated 请改用 {@link #uploadToBizPath(MultipartFile, String)}，旧目录规则已停止扩展
 */
@Deprecated
public String uploadOld(MultipartFile file) {
    return null;
}
```

建议：

- `@Deprecated` 注解和 `@deprecated` Javadoc 最好同时写
- Javadoc 中要明确替代方案
- 最好说明为什么废弃，而不只是写“已废弃”

---

## 11. 进阶内联标签案例

### 11.1 `{@code}`

```java
/**
 * 文件状态只允许为 {@code INIT}、{@code SUCCESS} 或 {@code FAIL}。
 */
private String status;
```

用途：

- 表示固定代码值
- 表示方法名、字段名、枚举值
- 避免普通文本里代码样式不清楚

### 11.2 `{@link}`

```java
/**
 * 上传结果定义见 {@link BaseFileDTO}。
 */
public BaseFileDTO upload(CommonUploadReqDTO req) {
    return null;
}
```

用途：

- 引用相关类
- 引用相关方法
- 引用上下游对象

### 11.3 `{@literal}`

```java
/**
 * 路径格式固定为 {@literal merchantId/yyyy/MM/dd/}。
 */
public String buildPath() {
    return null;
}
```

用途：

- 展示 `<`、`>`、`/` 等不希望被解释的字符
- 展示模板格式

---

## 12. 结合业务场景的完整示例

下面是一个更接近实际项目的完整示例。

```java
/**
 * 文件上传通用入口。
 * 统一处理各类业务文件上传到对象存储的流程。
 *
 * @author liyl
 * @date 2026/4/23
 */
@RestController
@RequestMapping("/common/upload")
public class CommonUploadController {

    /**
     * 上传业务文件。
     * 根据业务路径类型将文件保存到对应目录，并返回文件访问信息。
     *
     * @param file 上传文件
     * @param bizPath 业务路径类型
     * @return 文件信息，包含文件名、访问地址和存储路径
     * @throws IllegalArgumentException 文件为空时抛出
     * @apiNote 仅允许登录态用户调用
     */
    @PostMapping("/file")
    public BaseFileDTO upload(@RequestParam("file") MultipartFile file,
                              @RequestParam("bizPath") String bizPath) {
        return null;
    }
}
```

这个示例里：

- 类注释负责说明控制器整体职责
- 方法注释负责说明接口行为、入参、出参、异常和调用约束
- 这样生成的 API 文档会更完整，也方便后来人快速接手

---

## 13. 实际编写建议

### 13.1 推荐写法

推荐优先写清楚以下内容：

- 这个类或方法是干什么的
- 参数在业务上的含义是什么
- 返回值表示什么
- 哪些情况下会失败
- 是否有调用限制、边界条件或特殊约束

### 13.2 不推荐写法

不推荐下面这类注释：

```java
/**
 * 上传
 */
public void upload() {
}
```

问题是：

- 信息量过低
- 看了等于没看
- 无法体现业务上下文

也不推荐纯重复代码名：

```java
/**
 * 获取用户ID
 *
 * @return 用户ID
 */
public Long getUserId() {
    return userId;
}
```

如果是简单 getter/setter，通常没有必要强行补 Javadoc，除非有额外业务语义。

---

## 14. 适合写 Javadoc 的场景

更建议重点补 Javadoc 的地方：

- 对外 API 接口
- Controller 公共接口方法
- Service 核心业务方法
- 复杂工具类
- 有明显业务约束的 DTO / VO / Enum
- 会被多人复用的公共能力
- 有废弃计划的旧接口

可少写或不写的场景：

- 非公共的简单私有方法
- 语义已经极其明确的字段
- 普通 getter/setter
- 临时性、一次性、极短生命周期代码

---

## 15. 一个适合团队统一的模板

### 15.1 类模板

```java
/**
 * 这里写类的一句话摘要。
 * 这里补充类的职责边界或适用场景。
 *
 * @author 作者名
 * @date 2026/4/23
 */
public class DemoClass {
}
```

### 15.2 方法模板

```java
/**
 * 这里写方法的一句话摘要。
 * 这里补充边界条件、业务约束或特殊说明。
 *
 * @param param1 参数1说明
 * @param param2 参数2说明
 * @return 返回值说明
 * @throws ExceptionType 异常触发条件说明
 */
public ReturnType demoMethod(Type1 param1, Type2 param2) {
    return null;
}
```

### 15.3 枚举模板

```java
/**
 * 这里写枚举整体用途。
 */
public enum DemoEnum {

    /** 含义一 */
    TYPE_ONE,

    /** 含义二 */
    TYPE_TWO
}
```

---

## 16. 总结

Javadoc 最重要的不是“写满标签”，而是写出真正有用的信息。

优先级建议如下：

1. 先把摘要写清楚
2. 再补 `@param`、`@return`、`@throws`
3. 有必要时补 `@apiNote`、`@implNote`、`@deprecated`
4. 对类、接口、枚举、公共方法优先保证质量

如果用于你当前项目中的 Controller 或 Service，比较实用的写法通常是：

- 类注释：职责 + 适用场景 + 作者/日期
- 方法注释：功能摘要 + 参数 + 返回值 + 异常 + 业务约束

这样既不会过度冗长，也足够支持后续维护和文档生成。
