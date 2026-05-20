# Niro

Niro 是一个 Buff/CS2 饰品交易自动化平台，当前仓库同时承载前端应用、后端服务、第三方平台 SDK、部署配置和需求文档。

## 项目概述

- `niro-client`：前端应用仓库，负责页面、交互、路由、状态管理与 API 调用。
- `niro-server`：后端服务仓库，负责业务接口、任务调度、消息链路、数据库访问与第三方平台集成。
- `docker`：部署与基础设施相关配置目录。
- `docs`：需求、设计和规格文档目录。
- `backup`：备份文件目录。

## 常用命令

### 前端（niro-client）

```bash
cd niro-client
pnpm dev
pnpm lint
pnpm type-check
pnpm build
```

### 后端（niro-server）

```bash
cd niro-server
mvn clean install -DskipTests -Dmaven.compiler.fork=true -Dmaven.compiler.executable="D:\Environment\JDK\jdk-21.0.2\bin\javac.exe"
mvn spring-boot:run -pl niro-web -Dspring-boot.run.jvmArguments="-Djava.home=D:\Environment\JDK\jdk-21.0.2"

mvn test -Dtest=ResponseAdviceTest#testSuccessResponse -Dmaven.compiler.fork=true -Dmaven.compiler.executable="D:\Environment\JDK\jdk-21.0.2\bin\javac.exe"
mvn -pl niro-web test -Dtest=RocketMQProducerTest -Dmaven.compiler.fork=true -Dmaven.compiler.executable="D:\Environment\JDK\jdk-21.0.2\bin\javac.exe"
```

## 项目结构

```text
niro/
├── niro-client/                 # 前端应用
│   └── src/
│       ├── api/                 # API 请求
│       ├── components/          # 通用组件
│       ├── views/               # 页面视图
│       ├── stores/              # Pinia 状态管理
│       └── router/              # 路由配置
├── niro-server/                 # 后端服务
│   ├── niro-core/               # 公共组件与基础能力
│   ├── niro-web/                # Web 业务模块
│   ├── niro-sdk/                # 第三方平台 SDK
│   └── docs/                    # 后端补充文档
├── docker/                      # Docker 配置
├── docs/                        # 需求与设计文档
├── docker-compose.yml           # 生产环境编排
├── docker-compose.test.yml      # 测试环境编排
└── backup/                      # 备份文件
```
