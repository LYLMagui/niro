# Ace-Mcp-Node Docker 部署

## 概述

Ace-Mcp-Node 是一个 MCP 服务器，提供代码库语义搜索和索引功能。

## 快速开始

### 1. 配置环境变量

```bash
# 复制环境变量文件
cp .env.example .env

# 编辑 .env 文件，配置以下必填项：
# - BASE_URL: 索引服务器地址
# - TOKEN: 访问令牌
```

### 2. 构建并启动

```bash
# 构建镜像
docker build -t acemcp-node:latest .

# 启动服务
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

| 变量 | 必填 | 说明 |
|------|------|------|
| BASE_URL | 是 | 索引服务器地址 |
| TOKEN | 是 | 访问令牌 |
| PROJECT_PATH | 否 | 要索引的项目路径，默认 `../..` |
| WEB_PORT | 否 | Web 端口，默认 8080 |

### 索引服务器

Ace-Mcp-Node 需要连接到索引服务器才能工作。你有以下选择：

1. **自行部署索引服务器**：需要部署 Ace-Mcp 后端服务
2. **使用公共服务**：如果有公开的 API 服务，配置 BASE_URL 和 TOKEN

### 配置文件位置

容器内的配置文件位于：`/root/.acemcp/`

- `settings.toml` - 主配置文件
- `data/projects.json` - 项目索引数据

### 端口

| 端口 | 说明 |
|------|------|
| 8080 | Web 管理界面 |

## Claude Code 集成

MCP 配置已添加到 `.claude/settings.local.json`：

```json
{
  "mcpServers": {
    "acemcp": {
      "command": "npx",
      "args": [
        "-y",
        "acemcp-node",
        "--project-path",
        "D:/MySpace/niro"
      ],
      "type": "stdio"
    }
  }
}
```

**注意**：MCP 客户端连接前，需要确保：
1. Docker 容器正在运行
2. BASE_URL 和 TOKEN 已正确配置

## 网络

当前配置使用外部网络 `niro-network`。如果不存在，可以创建：

```bash
docker network create niro-network
```

或者移除 `networks` 部分。

## 清理

```bash
# 停止服务
docker-compose down

# 删除卷（会清除索引数据）
docker-compose down -v
```

## 故障排除

### 连接失败

```bash
# 检查容器日志
docker logs acemcp-server

# 检查配置
docker exec acemcp-server cat /root/.acemcp/settings.toml
```

### 索引不更新

```bash
# 进入容器手动触发索引
docker exec -it acemcp-server acemcp-node --reindex
```
