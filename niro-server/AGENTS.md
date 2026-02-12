# niro-server AGENTS.md

## 项目概述

niro-server 是 Niro 项目的后端服务，基于 Spring Boot 3 + Java 21 构建，采用多模块 Maven 架构。

### 核心定位
- Buff 饰品交易平台的数据管理与自动化交易系统
- 支持多账号管理、扫货任务调度、订单同步等核心业务
- 提供 RESTful API 供前端和爬虫调用

## 项目架构

### 模块结构

```
niro-server/
├── pom.xml                      # 父 POM，统一管理依赖版本
├── niro-core/                   # 核心模块（公共组件、工具类、配置）
│   ├── src/main/java/com/niro/core/
│   │   ├── advice/              # 响应增强器
│   │   ├── aspect/              # AOP 切面
│   │   ├── config/              # 通用配置类
│   │   ├── constant/            # 常量定义
│   │   ├── exception/           # 异常定义
│   │   ├── filter/              # 过滤器
│   │   ├── handler/             # 全局处理器
│   │   ├── result/              # 响应结果封装
│   │   └── util/                # 工具类
│   └── pom.xml
├── niro-web/                    # Web 应用模块（业务逻辑）
│   ├── src/main/java/com/niro/web/
│   │   ├── aspect/              # 业务切面
│   │   ├── config/              # 业务配置
│   │   ├── constant/            # 业务常量
│   │   ├── controller/          # API 控制器
│   │   ├── dto/                 # 数据传输对象
│   │   ├── entity/              # 实体类（对应数据库表）
│   │   ├── enums/               # 枚举定义
│   │   ├── jobhandler/          # XXL-JOB 任务处理器
│   │   ├── mapper/              # MyBatis Mapper 接口
│   │   ├── service/             # 业务服务接口
│   │   └── service/impl/        # 业务服务实现
│   └── pom.xml
└── niro-sdk/                    # SDK 模块（第三方平台集成）
    └── src/main/java/com/niro/sdk/
        └── c5/                  # C5Game 平台 SDK
            ├── client/          # API 客户端
            ├── config/          # SDK 配置
            └── enums/           # SDK 枚举
```

### 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 基础框架 | Spring Boot | 3.5.6 | 核心框架 |
| JDK | Java | 21 | 运行环境 |
| ORM | MyBatis-Plus | 3.5.9 | 数据库访问 |
| 数据库 | PostgreSQL | 42.7.7 | 主数据库 |
| 连接池 | Druid | 1.2.20 | 数据库连接池 |
| 缓存 | Redis | 3.2.1 | 缓存与会话 |
| 分布式锁 | Redisson | 3.45.0 | 分布式锁实现 |
| 认证 | Sa-Token | 1.44.0 | 权限认证 |
| 文档 | Knife4j | 4.5.0 | API 文档 |
| 工具库 | Hutool | 5.8.40 | 常用工具 |
| 调度 | XXL-JOB | 3.3.2 | 分布式任务调度 |
| 消息队列 | RocketMQ | 2.3.1 | 异步消息 |
| 搜索 | Elasticsearch | 8.11.1 | 全文检索 |
| 构建 | Maven | - | 项目构建 |

## 编码规范

### 1. 依赖注入（强制）

**禁止**使用 `@Autowired` 字段注入，**必须**使用构造注入：

```java
@Service
@RequiredArgsConstructor  // Lombok 生成构造方法
public class UserService {
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    
    // 业务逻辑...
}
```

### 2. 响应封装（自动）

Controller **无需手动封装 Result**，已由 `ResponseAdvice` 自动处理：

```java
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    
    // ❌ 错误：手动封装 Result
    @GetMapping("/user/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }
    
    // ✅ 正确：直接返回数据对象
    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);
    }
    
    // ✅ 正确：无返回值时使用 void
    @PostMapping("/user")
    public void saveUser(@RequestBody UserDTO dto) {
        userService.save(dto);
    }
}
```

**特殊处理**：SSE 流（`SseEmitter`）会自动排除，不进行封装。

### 3. 业务断言（强制）

**禁止**使用 `if + throw` 模式，**必须**使用 `Assert` 工具类：

```java
// ❌ 错误：手动判断并抛出异常
if (user == null) {
    throw new BusinessException("用户不存在");
}

// ✅ 正确：使用 Assert 工具
Assert.notNull(user, "用户不存在");
Assert.notBlank(username, "用户名不能为空");
Assert.notEmpty(userList, "用户列表不能为空");
Assert.isTrue(amount > 0, "金额必须大于0");
```

可用断言方法：
- `Assert.notNull(Object, String)` - 对象非空
- `Assert.isNull(Object, String)` - 对象为空
- `Assert.notBlank(String, String)` - 字符串非空白
- `Assert.notEmpty(Collection, String)` - 集合非空
- `Assert.isTrue(boolean, String)` - 表达式为 true
- `Assert.isFalse(boolean, String)` - 表达式为 false

### 4. 数据库查询（MyBatis-Plus）

**禁止**使用 `QueryWrapper`，**必须**使用 Lambda 链式查询：

```java
// ❌ 错误：使用 QueryWrapper
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.eq("username", username);
User user = userMapper.selectOne(wrapper);

// ✅ 正确：使用 Lambda 链式查询
User user = userService.lambdaQuery()
    .eq(User::getUsername, username)
    .one();

// ✅ 正确：列表查询
List<User> list = userService.lambdaQuery()
    .eq(User::getStatus, 1)
    .like(StrUtil.isNotBlank(keyword), User::getUsername, keyword)
    .orderByDesc(User::getCreateTime)
    .list();

// ✅ 正确：分页查询
Page<User> page = userService.lambdaQuery()
    .eq(User::getStatus, 1)
    .page(new Page<>(current, size));
```

### 5. 软删除处理

所有实体类使用 `@TableLogic` 注解实现逻辑删除：

```java
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    
    @TableLogic
    private Integer deleted;  // 0=未删除, 1=已删除
}
```

删除操作**必须**断言影响行数：

```java
public void deleteUser(Long id) {
    int rows = userMapper.deleteById(id);
    Assert.isTrue(rows > 0, "删除失败，用户不存在");
}
```

### 6. 异常处理

**禁止**在业务代码中捕获 `Exception`，由 `@RestControllerAdvice` 统一处理：

```java
// ❌ 错误：在业务代码中捕获异常
try {
    userService.save(user);
} catch (Exception e) {
    log.error("保存失败", e);
    throw new BusinessException("保存失败");
}

// ✅ 正确：直接执行业务逻辑，异常由全局处理器捕获
userService.save(user);
```

### 7. 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.niro.web.service` |
| 类名 | PascalCase | `UserService`, `BuffAccountController` |
| 方法名 | camelCase | `getUserById`, `saveOrUpdate` |
| 变量名 | camelCase | `userId`, `buffAccount` |
| 常量名 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` |
| 接口名 | 以 Service 结尾 | `UserService` |
| 实现类 | Impl 后缀 | `UserServiceImpl` |
| Controller | 以 Controller 结尾 | `UserController` |
| Mapper | 以 Mapper 结尾 | `UserMapper` |

## 核心组件使用

### 1. Result 响应结果

```java
// 成功响应（带数据）
Result.success(data);

// 成功响应（无数据）
Result.success();

// 失败响应（默认错误码）
Result.failure("错误消息");

// 失败响应（自定义错误码）
Result.failure(500, "服务器内部错误");
```

### 2. RedisUtil 工具类

```java
@Service
@RequiredArgsConstructor
public class CacheService {
    private final RedisUtil redisUtil;
    
    public void demo() {
        // String 操作
        redisUtil.set("key", value);
        redisUtil.set("key", value, 3600);  // 带过期时间（秒）
        Object value = redisUtil.get("key");
        
        // Hash 操作
        redisUtil.hSet("hashKey", "field", value);
        Object hashValue = redisUtil.hGet("hashKey", "field");
        
        // 分布式锁
        boolean locked = redisUtil.tryLock("lockKey", "requestId", 30);
        if (locked) {
            try {
                // 执行业务逻辑
            } finally {
                redisUtil.unlock("lockKey", "requestId");
            }
        }
        
        // 删除
        redisUtil.delete("key");
    }
}
```

### 3. 日志记录

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

### 4. API 文档注解

```java
@Tag(name = "用户管理")                    // 控制器标签
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    
    @Operation(summary = "获取用户详情")   // 接口描述
    @GetMapping("/{id}")
    public User getUser(
        @Parameter(description = "用户ID") // 参数描述
        @PathVariable Long id
    ) {
        return userService.getById(id);
    }
}
```

### 5. DTO 定义

```java
@Data
@Schema(description = "用户DTO")
public class UserDTO {
    
    @Schema(description = "用户ID", example = "1")
    private Long id;
    
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @Schema(description = "状态", example = "1")
    private Integer status;
}
```

## 业务专项规范

### 1. Buff 账号管理

涉及 `BuffAccount` 的操作**必须**包含异常计数逻辑：

```java
@Service
@RequiredArgsConstructor
public class BuffAccountServiceImpl implements BuffAccountService {
    
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
        // 更新账号状态，增加失败次数
        BuffAccount account = new BuffAccount();
        account.setId(accountId);
        account.setFailCount(account.getFailCount() + 1);
        account.setLastError(errorMsg);
        account.setUpdateTime(LocalDateTime.now());
        buffAccountMapper.updateById(account);
    }
}
```

### 2. 高并发保护

涉及余额变更、订单创建等操作**必须**使用分布式锁：

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final RedisUtil redisUtil;
    private final BuffAccountService accountService;
    
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
            // ...
        } finally {
            redisUtil.unlock(lockKey, requestId);
        }
    }
}
```

### 3. 多账号策略

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

## 构建与运行

### Maven 命令

```bash
# 编译整个项目
cd niro-server
mvn clean install

# 编译并跳过测试
mvn clean install -DskipTests

# 运行特定模块测试
mvn test -Dtest=ResponseAdviceTest
mvn test -Dtest=ResponseAdviceTest#testSuccessResponse

# 运行 Web 模块
mvn spring-boot:run -pl niro-web
```

### 配置文件

```yaml
# application.yml 核心配置
server:
  port: 8080

spring:
  profiles:
    active: @spring.profiles.active@  # 由 Maven 注入
  datasource:
    url: jdbc:postgresql://localhost:5432/niro
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    type: com.alibaba.druid.pool.DruidDataSource
  
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
      logic-delete-value: 1
      logic-not-delete-value: 0

# XXL-JOB 配置
xxl:
  job:
    admin:
      addresses: http://localhost:8080/xxl-job-admin
    executor:
      appname: niro-server
      ip:
      port: 9999
      logpath: ./logs/xxl-job
      logretentiondays: 30
```

### 环境变量

```bash
# .env 文件
DB_USERNAME=postgres
DB_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_password
```

## 测试规范

### 单元测试示例

```java
@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    void shouldReturnUserWhenExists() throws Exception {
        // Given
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        when(userService.getById(1L)).thenReturn(user);
        
        // When & Then
        mockMvc.perform(get("/user/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.username").value("test"));
    }
    
    @Test
    void shouldReturn404WhenUserNotExists() throws Exception {
        when(userService.getById(999L)).thenReturn(null);
        
        mockMvc.perform(get("/user/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isEmpty());
    }
}
```

## 安全规范

### 1. 接口权限

使用 Sa-Token 进行权限控制：

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
    
    // 需要特定角色
    @SaCheckRole("super-admin")
    @PostMapping("/admin/config")
    public void updateConfig(@RequestBody ConfigDTO dto) {
        configService.update(dto);
    }
}
```

### 2. 参数校验

使用 Bean Validation 进行参数校验：

```java
@Data
public class CreateOrderDTO {
    @NotNull(message = "账号ID不能为空")
    private Long accountId;
    
    @NotBlank(message = "商品ID不能为空")
    private String goodsId;
    
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
    
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;
}
```

### 3. 敏感数据处理

```java
@Service
public class BuffAccountService {
    
    // 日志中脱敏处理
    public void saveAccount(BuffAccountDTO dto) {
        log.info("保存账号: id={}, nickname={}", 
            dto.getId(), 
            dto.getNickname());
        // Cookie 等敏感信息不记录到日志
    }
    
    // 返回前脱敏
    public BuffAccountDTO getAccount(Long id) {
        BuffAccount account = buffAccountMapper.selectById(id);
        BuffAccountDTO dto = new BuffAccountDTO();
        BeanUtil.copyProperties(account, dto);
        dto.setCookie(null);  // 敏感字段不返回
        return dto;
    }
}
```

## 常用代码片段

### 1. 分页查询

```java
public PageResult<User> listUsers(UserQueryParam param) {
    Page<User> page = userService.lambdaQuery()
        .like(StrUtil.isNotBlank(param.getUsername()), User::getUsername, param.getUsername())
        .eq(param.getStatus() != null, User::getStatus, param.getStatus())
        .orderByDesc(User::getCreateTime)
        .page(new Page<>(param.getPageNum(), param.getPageSize()));
    
    return PageResult.of(page);
}
```

### 2. 事务管理

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    @Transactional(rollbackFor = Exception.class)
    public void createOrderWithItems(OrderDTO dto) {
        // 1. 保存订单主表
        Order order = new Order();
        BeanUtil.copyProperties(dto, order);
        orderMapper.insert(order);
        
        // 2. 保存订单明细
        for (OrderItemDTO item : dto.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            BeanUtil.copyProperties(item, orderItem);
            orderItemMapper.insert(orderItem);
        }
        
        // 3. 更新库存
        inventoryService.decrease(dto.getItems());
    }
}
```

### 3. 异步处理

```java
@Service
@RequiredArgsConstructor
public class NotifyService {
    
    @Async("taskExecutor")
    public CompletableFuture<Void> sendEmailAsync(String email, String content) {
        emailService.send(email, content);
        return CompletableFuture.completedFuture(null);
    }
    
    public void batchNotify(List<String> emails, String content) {
        List<CompletableFuture<Void>> futures = emails.stream()
            .map(email -> sendEmailAsync(email, content))
            .collect(Collectors.toList());
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
```

### 4. 枚举定义

```java
@Getter
@AllArgsConstructor
public enum BuffAccountStatusEnum {
    NORMAL(1, "正常"),
    DISABLED(2, "禁用"),
    EXPIRED(3, "Cookie过期"),
    FAILED(4, "异常");
    
    private final int code;
    private final String desc;
    
    public static BuffAccountStatusEnum of(int code) {
        for (BuffAccountStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
```

## 调试与排错

### 1. API 文档访问

启动后访问：`http://localhost:8080/doc.html`

### 2. 日志级别调整

```bash
# 运行时调整日志级别（Actuator）
curl -X POST http://localhost:8080/actuator/loggers/com.niro.web \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

### 3. 常见错误

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| `NullPointerException` | 对象未初始化 | 检查依赖注入、Assert 断言 |
| `BusinessException` | 业务逻辑错误 | 检查业务规则、参数校验 |
| `NotLoginException` | 未登录或Token过期 | 重新登录，检查 Sa-Token 配置 |
| `QueryTimeoutException` | SQL 执行超时 | 检查 SQL 性能，添加索引 |
| `RedisConnectionFailure` | Redis 连接失败 | 检查 Redis 配置和网络 |

## 扩展阅读

- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [MyBatis-Plus 文档](https://baomidou.com/)
- [Sa-Token 文档](http://sa-token.dev33.cn/)
- [Knife4j 文档](https://doc.xiaominfo.com/)
- [Hutool 文档](https://hutool.cn/)

---

**最后更新**：2026-02-12

**维护者**：Niro 开发团队
