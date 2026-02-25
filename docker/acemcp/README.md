# Ace-Mcp-Node Docker 部署

## 快速开始

### 1. 构建镜像

```bash
cd docker/acemcp
docker build -t acemcp-node:latest .
```

### 2. 使用 Docker Compose 启动

```bash
docker-compose up -d
```

### 3. 验证运行

```bash
# 查看容器状态
docker ps

# 查看日志
docker logs acemcp-server
```

访问 http://localhost:8080 查看 Web 管理界面。

## 配置说明

### 环境变量

复制 `.env.example` 为 `.env` 并修改：

```bash
cp .env.example .env
```

### 配置文件位置

容器内的配置文件位于：`/root/.acemcp/`

挂载的卷：`acemcp-config`

### 端口

| 端口 | 说明 |
|------|------|
| 8080 | Web 管理界面 |

## 网络

当前配置使用外部网络 `niro-network`。如果不需要，可以删除 `networks` 部分。

## 清理

```bash
# 停止服务
docker-compose down

# 删除卷（会清除索引数据）
docker-compose down -v
```
