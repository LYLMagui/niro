# niro-server CLAUDE.md

> Niro 项目后端模块专用规范
> 版本：1.0.0 | 更新日期：2026-02-24

---

## 1. 项目概述

### 1.1 核心定位
niro-server 是 Niro 项目的后端服务，基于 Spring Boot 3.5 + Java 21 构建，提供 Buff/CS2 饰品交易的数据管理与自动化交易能力。

### 1.2 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Spring Boot | 3.5.6 | 核心框架 |
| JDK | Java | 21 | 运行环境 |
| ORM | MyBatis-Plus | 3.5.9 | 数据库访问 |
| 数据库 | PostgreSQL | 42.7.7 | 主数据库 |
| 连接池 | Druid | 1.2.20 | 数据库连接池 |
| 缓存 | Redis | 3.2.1 | 缓存与会话 |
| 分布式锁 | Redisson | 3.45.0 | 分布式锁 |
| 认证 | Sa-Token | 1.44.0 | 权限认证 |
| 文档 | Knife4j | 4.5.0 | API 文档 |
| 工具库 | Hutool | 5.8.40 | 常用工具 |
| 调度 | XXL-JOB | 3.3.2 | 分布式任务调度 |
| 消息队列 | RocketMQ | 2.3.1 | 异步消息 |
| 搜索 | Elasticsearch | 8.11.1 | 全文检索 |
| 构建 | Maven | - | 项目构建 |

### 1.3 本地 JDK 规范

- 后端编译、测试、运行统一使用 JDK：`D:\Environment\JDK\jdk-21.0.2`
- 执行 Maven 相关命令前，默认按该 JDK 环境理解和验证

---

## 2. 项目结构

```
niro-server/
├── pom.xml                      # 父 POM，统一管理依赖版本
│
├── niro-core/                   # 核心模块（公共组件）
│   ├── src/main/java/com/niro/core/
│   │   ├── advice/              # 响应增强器
│   │   │   └── ResponseAdvice.java       # 统一响应封装
│   │   ├── aspect/              # AOP 切面
│   │   ├── config/              # 通用配置类
│   │   │   ├── MybatisPlusConfig.java
│   │   │   ├── RedisConfig.java
│   │   │   ├── WebConfig.java
│   │   │   └── XxlJobConfig.java
│   │   ├── constant/            # 常量定义
│   │   │   ├── GlobalConstant.java
│   │   │   ├── BuffConstant.java
│   │   │   └── MqConstant.java
│   │   ├── exception/           # 异常定义
│   │   │   └── BusinessException.java
│   │   ├── filter/              # 过滤器
│   │   │   └── TraceIdFilter.java       # 链路追踪
│   │   ├── handler/             # 全局处理器
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── result/              # 响应结果封装
│   │   │   ├── Result.java
│   │   │   └── StatusCode.java
│   │   └── util/                # 工具类
│   │       ├── RedisUtil.java
│   │       ├── MqTxSender.java
│   │       └── RocketMqHelper.java
│   └── pom.xml
│
├── niro-web/                    # Web 应用模块（业务逻辑）
│   ├── src/main/java/com/niro/web/
│   │   ├── aspect/              # 业务切面
│   │   ├── config/              # 业务配置
│   │   ├── constant/            # 业务常量
│   │   ├── controller/          # API 控制器
│   │   │   ├── BuffAccountController.java    # Buff 账号管理
│   │   │   ├── BuffGoodsController.java      # 商品管理
│   │   │   ├── BuffScanTaskController.java    # 扫描任务
│   │   │   ├── TradeOrderController.java     # 交易订单
│   │   │   ├── C5OrderSyncController.java    # C5 订单同步
│   │   │   ├── UserController.java           # 用户管理
│   │   │   ├── NotifyController.java        # 通知服务
│   │   │   ├── LogController.java           # 日志查询
│   │   │   └── HealthController.java        # 健康检查
│   │   ├── dto/                 # 数据传输对象
│   │   │   ├── request/         # 请求 DTO
│   │   │   └── response/        # 响应 DTO
│   │   ├── entity/              # 实体类（对应数据库表）
│   │   │   ├── BuffGoods.java
│   │   │   ├── BuffAccount.java
│   │   │   ├── BuffScanTask.java
│   │   │   ├── TradeOrderRecord.java
│   │   │   └── ...
│   │   ├── enums/               # 枚举定义
│   │   │   ├── BuffAccountStatusEnum.java
│   │   │   ├── TaskStatusEnum.java
│   │   │   └── OrderStatusEnum.java
│   │   ├── jobhandler/          # XXL-JOB 任务处理器
│   │   ├── mapper/              # MyBatis Mapper 接口
│   │   │   ├── BuffGoodsMapper.java
│   │   │   ├── BuffAccountMapper.java
│   │   │   └── ...
│   │   ├── service/             # 业务服务接口
│   │   │   ├── BuffGoodsService.java
│   │   │   ├── BuffAccountService.java
│   │   │   └── ...
│   │   └── service/impl/        # 业务服务实现
│   │       ├── BuffGoodsServiceImpl.java
│   │       └── ...
│   │
│   ├── src/main/resources/
│   │   ├── application.yml      # 应用配置
│   │   └── mapper/              # MyBatis XML 映射
│   │
│   └── pom.xml
│
└── niro-sdk/                    # SDK 模块（第三方平台集成）
    └── src/main/java/com/niro/sdk/
        └── c5/                  # C5Game 平台 SDK
            ├── client/          # API 客户端
            │   ├── C5MarketClient.java
            │   └── C5OrderClient.java
            ├── config/          # SDK 配置
            │   └── C5Config.java
            ├── dto/             # SDK DTO
            │   ├── request/
            │   └── response/
            └── enums/           # SDK 枚举
```

---

## 3. 编码规范

### 3.1 依赖注入（强制）

**禁止**使用 `@Autowired` 字段注入，**必须**使用构造注入：

```java
// ✅ 正确
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
}

// ❌ 错误
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
}
```

### 3.2 响应封装（自动）

Controller **无需手动封装 Result**，已由 `ResponseAdvice` 自动处理：

```java
// ✅ 正确：直接返回数据对象
@GetMapping("/user/{id}")
public User getUser(@PathVariable Long id) {
    return userService.getById(id);
}

// ❌ 错误：手动封装 Result
@GetMapping("/user/{id}")
public Result<User> getUser(@PathVariable Long id) {
    return Result.success(userService.getById(id));
}
```

### 3.3 业务断言（强制）

**禁止**使用 `if + throw` 模式，**必须**使用 `Assert` 工具类：

```java
// ✅ 正确：使用 Assert
Assert.notNull(user, "用户不存在");
Assert.notBlank(username, "用户名不能为空");
Assert.isTrue(amount > 0, "金额必须大于0");

// ❌ 错误
if (user == null) {
    throw new BusinessException("用户不存在");
}
```

### 3.4 数据库查询（MyBatis-Plus）

**禁止**使用 `QueryWrapper`，**必须**使用 Lambda 链式查询：

```java
// ✅ 正确：Lambda 链式查询
User user = userService.lambdaQuery()
    .eq(User::getUsername, username)
    .one();

List<User> list = userService.lambdaQuery()
    .eq(User::getStatus, 1)
    .like(StrUtil.isNotBlank(keyword), User::getUsername, keyword)
    .orderByDesc(User::getCreateTime)
    .list();

Page<User> page = userService.lambdaQuery()
    .eq(User::getStatus, 1)
    .page(new Page<>(current, size));

// ❌ 错误
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.eq("username", username);
```

### 3.5 软删除处理

所有实体类使用 `@TableLogic` 注解，删除**必须**断言影响行数：

```java
public void deleteUser(Long id) {
    int rows = userMapper.deleteById(id);
    Assert.isTrue(rows > 0, "删除失败，用户不存在");
}
```

### 3.6 异常处理

**禁止**在业务代码中捕获 `Exception`，由全局处理器统一处理：

```java
// ✅ 正确：直接执行业务逻辑
userService.save(user);

// ❌ 错误
try {
    userService.save(user);
} catch (Exception e) {
    log.error("保存失败", e);
    throw new BusinessException("保存失败");
}
```

### 3.7 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写 | `com.niro.web.service` |
| 类名 | PascalCase | `UserService`, `BuffAccountController` |
| 方法名 | camelCase | `getUserById`, `saveOrUpdate` |
| 变量名 | camelCase | `userId`, `buffAccount` |
| 常量名 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 接口名 | 以 Service 结尾 | `UserService` |
| Controller | 以 Controller 结尾 | `UserController` |
| Mapper | 以 Mapper 结尾 | `UserMapper` |

---

## 4. 核心组件

### 4.1 Result 响应结果

```java
// 成功响应（带数据）
Result.success(data);

// 成功响应（无数据）
Result.success();

// 失败响应
Result.failure("错误消息");
Result.failure(500, "服务器内部错误");
```

### 4.2 RedisUtil

```java
@Service
@RequiredArgsConstructor
public class CacheService {
    private final RedisUtil redisUtil;

    public void demo() {
        // String 操作
        redisUtil.set("key", value);
        redisUtil.set("key", value, 3600);  // 带过期时间
        Object value = redisUtil.get("key");

        // Hash 操作
        redisUtil.hSet("hashKey", "field", value);

        // 分布式锁（必须用于余额/订单/库存操作）
        boolean locked = redisUtil.tryLock("lockKey", "requestId", 30);
        if (locked) {
            try {
                // 执行业务逻辑
            } finally {
                redisUtil.unlock("lockKey", "requestId");
            }
        }
    }
}
```

### 4.3 日志记录

```java
@Slf4j
@Service
public class UserService {
    public void doSomething() {
        log.debug("调试信息: {}", param);
        log.info("业务信息: 用户{}执行了操作", userId);
        log.warn("警告信息: {}", warningMsg);
        log.error("错误信息: {}", errorMsg, exception);
    }
}
```

### 4.4 API 文档注解

```java
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public User getUser(
        @Parameter(description = "用户ID")
        @PathVariable Long id
    ) {
        return userService.getById(id);
    }
}
```

---

## 5. 业务专项规范

### 5.1 Buff 账号管理

涉及 `BuffAccount` 的操作**必须**包含异常计数逻辑：

```java
@Override
public void checkCookie(Long userId, Long id) {
    try {
        // 执行检测逻辑...
    } catch (Exception e) {
        // 失败时必须增加异常计数
        this.markFailed(id, e.getMessage());
        throw new BusinessException("Cookie检测失败: " + e.getMessage());
    }
}

private void markFailed(Long accountId, String errorMsg) {
    BuffAccount account = new BuffAccount();
    account.setId(accountId);
    account.setFailCount(account.getFailCount() + 1);
    account.setLastError(errorMsg);
    buffAccountMapper.updateById(account);
}
```

### 5.2 高并发保护

涉及余额变更、订单创建等操作**必须**使用分布式锁：

```java
public void createOrder(OrderDTO dto) {
    String lockKey = "order:create:" + dto.getAccountId();
    String requestId = UUID.fastUUID().toString();

    boolean locked = redisUtil.tryLock(lockKey, requestId, 10);
    Assert.isTrue(locked, "系统繁忙，请稍后重试");

    try {
        // 1. 检查余额
        BuffAccount account = accountService.getById(dto.getAccountId());
        Assert.notNull(account, "账号不存在");
        Assert.isTrue(account.getBalance().compareTo(dto.getAmount()) >= 0, "余额不足");

        // 2. 扣减余额
        account.setBalance(account.getBalance().subtract(dto.getAmount()));
        accountService.updateById(account);

        // 3. 创建订单
    } finally {
        redisUtil.unlock(lockKey, requestId);
    }
}
```

### 5.3 多账号策略

支持多账号并发操作时使用线程池：

```java
@Service
@RequiredArgsConstructor
public class BuffScanService {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4, 8, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100)
    );

    public void scanAllAccounts() {
        List<BuffAccount> accounts = accountService.lambdaQuery()
            .eq(BuffAccount::getStatus, BuffAccountStatusEnum.NORMAL)
            .list();

        CountDownLatch latch = new CountDownLatch(accounts.size());

        for (BuffAccount account : accounts) {
            executor.execute(() -> {
                try {
                    scanWithAccount(account);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("扫描任务被中断");
        }
    }
}
```

---

## 6. 安全规范

### 6.1 接口权限

```java
@RestController
@RequiredArgsConstructor
public class AdminController {

    // 需要登录
    @GetMapping("/admin/info")
    public AdminInfo getInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        return adminService.getInfo(userId);
    }

    // 需要特定权限
    @SaCheckPermission("admin:delete")
    @DeleteMapping("/admin/{id}")
    public void delete(@PathVariable Long id) {
        adminService.delete(id);
    }
}
```

### 6.2 参数校验

```java
@Data
public class CreateOrderDTO {
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    @NotBlank(message = "商品ID不能为空")
    private String goodsId;

    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}
```

### 6.3 敏感数据处理

```java
// 日志中脱敏处理
log.info("保存账号: id={}, nickname={}", dto.getId(), dto.getNickname());
// Cookie 等敏感信息不记录到日志

// 返回前脱敏
dto.setCookie(null);  // 敏感字段不返回
```

---

## 7. 常用命令

### 7.1 Maven 命令

```bash
# 编译整个项目
cd niro-server
mvn clean install

# 编译并跳过测试
mvn clean install -DskipTests

# 运行 Web 模块
mvn spring-boot:run -pl niro-web

# 运行单测
mvn test -Dtest=ResponseAdviceTest
mvn test -Dtest=ResponseAdviceTest#testSuccessResponse

# 运行模块测试
mvn -pl niro-web test -Dtest=RocketMQProducerTest
```

### 7.2 配置文件

```yaml
# application.yml 核心配置
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/niro
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
    password: ${REDIS_PASSWORD}

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deleted
```

### 7.3 API 文档

启动后访问：`http://localhost:8080/doc.html`

---

## 8. 常见错误

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| `NullPointerException` | 对象未初始化 | 检查依赖注入、Assert 断言 |
| `BusinessException` | 业务逻辑错误 | 检查业务规则、参数校验 |
| `NotLoginException` | 未登录或Token过期 | 重新登录 |
| `QueryTimeoutException` | SQL 执行超时 | 检查 SQL 性能，添加索引 |
| `RedisConnectionFailure` | Redis 连接失败 | 检查 Redis 配置 |

---

## 9. 参考资料

- [Spring Boot 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [MyBatis-Plus 文档](https://baomidou.com/)
- [Sa-Token 文档](http://sa-token.dev33.cn/)
- [Knife4j 文档](https://doc.xiaominfo.com/)
- [Hutool 文档](https://hutool.cn/)

---

**维护者**：Niro 后端开发团队
