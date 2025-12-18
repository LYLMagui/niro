# Buff Server 中间件集成方案

## 当前状态
- 基础Spring Boot Web服务器
- 无中间件依赖

## 后续中间件集成规划

### 1. 数据存储中间件

#### Elasticsearch（搜索和分析）
**集成时机**：爬虫数据量增大，需要全文搜索时
**模块**：buff-spider
**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```
**配置位置**：buff-spider模块的application.yml
**用途**：
- 爬取内容的索引和搜索
- 日志分析
- 数据统计

#### Redis（缓存和会话）
**集成时机**：需要缓存、分布式锁、会话管理时
**模块**：buff-core（公共配置）、各业务模块
**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```
**配置位置**：buff-core模块（公共配置）
**用途**：
- 接口缓存
- 分布式锁
- 会话存储
- 限流控制

### 2. 消息队列中间件

#### RabbitMQ（可靠消息传递）
**集成时机**：需要异步处理、削峰填谷时
**模块**：buff-core（配置）、各业务模块（使用）
**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```
**用途**：
- 爬虫任务分发
- 异步邮件发送
- 日志收集

#### Kafka（高吞吐量消息）
**集成时机**：大数据量处理、实时分析时
**模块**：buff-spider（主要使用）
**依赖**：
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```
**用途**：
- 爬虫数据流处理
- 实时数据分析

### 3. 数据库中间件

#### MyBatis Plus（ORM框架）
**集成时机**：需要复杂SQL、代码生成时
**模块**：buff-core（基础配置）、各业务模块
**依赖**：
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.5</version>
</dependency>
```

#### 数据库连接池（HikariCP）
**集成时机**：默认已集成，可优化配置
**配置优化**：
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### 4. 监控和运维中间件

#### Spring Boot Actuator（监控）
**集成时机**：需要应用监控时
**模块**：buff-web（启动模块）
**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

#### Micrometer + Prometheus（指标收集）
**集成时机**：需要性能监控时
**依赖**：
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 5. 外部接口对接方案

#### HTTP客户端
**Feign（声明式HTTP客户端）**
**集成时机**：需要调用外部API时
**模块**：buff-core（基础配置）
**依赖**：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

#### WebClient（响应式HTTP客户端）
**集成时机**：需要异步HTTP调用时
**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 6. 集成顺序建议

1. **第一阶段**：Redis（缓存）
2. **第二阶段**：MyBatis Plus（数据持久化）
3. **第三阶段**：RabbitMQ（异步处理）
4. **第四阶段**：Elasticsearch（搜索）
5. **第五阶段**：Kafka（大数据流）
6. **第六阶段**：监控和运维工具

### 7. 配置管理策略

#### 环境隔离
- application-dev.yml（开发环境）
- application-test.yml（测试环境）
- application-prod.yml（生产环境）

#### 敏感信息
- 使用环境变量
- Spring Cloud Config（后续）
- Kubernetes ConfigMap/Secret（容器化时）

### 8. 依赖管理

#### 版本统一管理
在父POM的dependencyManagement中统一管理所有中间件版本：

```xml
<dependencyManagement>
    <dependencies>
        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
        <!-- Elasticsearch -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
        <!-- 其他中间件... -->
    </dependencies>
</dependencyManagement>
```

### 9. 模块间中间件使用规范

- **buff-core**：只包含配置类，不包含业务使用
- **各业务模块**：按需引入依赖，避免循环依赖
- **配置集中**：中间件配置统一放在对应模块的config包下
- **接口隔离**：定义中间件操作的接口，实现与具体中间件解耦