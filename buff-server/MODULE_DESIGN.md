# Buff Server 多模块拆分方案

## 当前模块结构

### 1. buff-core（核心模块）
- **职责**：公共工具类、常量、基础实体、异常定义、**中间件配置**
- **依赖**：Spring Boot基础依赖
- **后续扩展**：
  - 公共配置类（@Configuration）
  - 工具类（日期、字符串、加密等）
  - 基础实体类（BaseEntity、PageRequest等）
  - 常量定义（业务常量、错误码等）
  - **中间件配置类（Redis、Elasticsearch、RabbitMQ等）**

### 2. buff-web（Web模块）
- **职责**：HTTP接口、控制器、启动类
- **依赖**：buff-core、spring-boot-starter-web
- **后续扩展**：
  - RESTful API接口
  - 控制器层逻辑
  - 启动配置
  - 基础健康检查接口

### 3. buff-spider（爬虫模块）
- **职责**：爬虫定时任务、数据抓取逻辑
- **依赖**：buff-core、spring-boot-starter
- **后续扩展**：
  - 定时任务配置
  - 爬虫业务逻辑
  - 数据解析服务

## 中间件集成架构设计

### 为什么不需要单独中间件模块？

1. **避免过度工程化**：当前项目规模不需要额外的抽象层
2. **保持高内聚**：中间件配置与业务逻辑紧密相关
3. **灵活扩展**：各业务模块可按需引入中间件依赖
4. **微服务友好**：后续拆分时每个服务可独立选择中间件

### 中间件集成策略

#### 方案：配置集中化 + 业务模块按需依赖

**配置集中化（buff-core）**:
```
buff-core/src/main/java/com/buff/core/config/
├── RedisConfig.java
├── ElasticsearchConfig.java
├── RabbitMQConfig.java
├── KafkaConfig.java
└── DataSourceConfig.java
```

**业务模块按需依赖**:
- buff-spider需要Redis缓存 → 引入spring-boot-starter-data-redis
- buff-ai需要Elasticsearch → 引入spring-boot-starter-data-elasticsearch
- buff-web需要RabbitMQ → 引入spring-boot-starter-amqp

### 中间件配置管理

#### 1. 配置类设计（buff-core）
```java
@Configuration
@ConditionalOnClass(RedisTemplate.class)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {
    // Redis配置，可被各模块引用
}
```

#### 2. 接口解耦（buff-core）
```java
public interface CacheService {
    void set(String key, Object value);
    Object get(String key);
}

@Service
@ConditionalOnProperty(name = "cache.type", havingValue = "redis")
public class RedisCacheService implements CacheService {
    // Redis实现
}
```

#### 3. 配置优先级
1. **环境变量**（最高优先级）
2. **application-{env}.yml**（环境配置）
3. **application.yml**（默认配置）
4. **配置类默认值**（最低优先级）

## 后续模块拆分建议

### 4. buff-ai（AI模块）- 后续添加
- **职责**：AI大模型集成、智能处理逻辑
- **依赖**：buff-core
- **中间件需求**：
  - Redis（缓存AI结果）
  - Elasticsearch（存储对话历史）
- **集成内容**：
  - LLM客户端封装
  - 提示词管理
  - AI服务接口

### 5. buff-personal（个人网站模块）- 后续添加
- **职责**：个人网站相关功能
- **依赖**：buff-core、buff-web
- **中间件需求**：
  - MySQL/PostgreSQL（文章存储）
  - Redis（页面缓存）
  - Elasticsearch（文章搜索）
- **功能**：
  - 博客管理
  - 个人作品展示
  - 简历管理

### 6. buff-tool（工具模块）- 后续添加
- **职责**：各种实用工具功能
- **依赖**：buff-core
- **中间件需求**：
  - Redis（任务队列）
  - 文件存储（MinIO/OSS）
- **功能**：
  - 文件处理工具
  - 数据转换工具
  - 系统监控工具

### 7. buff-lab（技术试验田模块）- 后续添加
- **职责**：新技术试验、原型开发
- **依赖**：buff-core
- **中间件需求**：按需动态引入
- **用途**：
  - 新技术验证
  - 原型功能开发
  - 性能测试

## 微服务拆分策略

### 阶段一：单体多模块（当前）
- 所有模块打包在一个JAR中
- 共享数据库连接
- 适合快速开发和测试
- **中间件共享**：所有中间件配置在buff-core，各模块按需使用

### 阶段二：服务拆分（后续）
- buff-web：API网关 + 聚合服务
- buff-spider：独立爬虫服务（需要Redis、Elasticsearch）
- buff-ai：AI处理服务（需要Redis、Elasticsearch）
- buff-personal：个人网站服务（需要数据库、Redis、Elasticsearch）
- buff-tool：工具服务（需要Redis、文件存储）
- buff-core：公共库（不单独部署）

## 模块间依赖关系

```
buff-web (启动模块)
├── buff-core (公共依赖 + 中间件配置)
├── buff-spider (业务依赖 + 按需引入中间件)
├── buff-ai (后续 + 按需引入中间件)
├── buff-personal (后续 + 按需引入中间件)
└── buff-tool (后续 + 按需引入中间件)
```

## 中间件依赖引入示例

### buff-spider模块添加Redis支持
```xml
<!-- buff-spider/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
# buff-spider/src/main/resources/application.yml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

```java
// buff-spider中使用
@Service
public class SpiderService {
    @Autowired
    private CacheService cacheService; // 来自buff-core的接口
    
    public void crawl(String url) {
        // 使用缓存服务
        cacheService.set("spider:last_url", url);
    }
}
```

## 打包策略

1. **开发阶段**：所有模块打包成一个可执行JAR
2. **测试阶段**：可选择性打包特定模块组合
3. **生产阶段**：
   - 单体部署：打包所有模块，共享中间件实例
   - 微服务部署：各模块独立打包，独立配置中间件

## 优势总结

1. **简洁性**：避免过度设计，保持架构简单
2. **灵活性**：各模块可按需选择中间件
3. **可维护性**：配置集中，业务清晰
4. **扩展性**：支持从单体到微服务的平滑演进
5. **成本效益**：减少不必要的抽象层，降低开发成本