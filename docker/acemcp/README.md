# Ace-Mcp-Node Docker 部署

## 概述

Ace-Mcp-Node 是一个 MCP 服务器，提供代码库语义搜索和索引功能。

## 目录结构

```
acemcp/
├── config/
│   └── settings.toml    # 配置文件（宿主机可编辑）
├── data/                # 索引数据目录（自动创建）
├── Dockerfile           # Docker 镜像构建文件
├── docker-compose.yml   # Docker Compose 编排文件
├── .env.example         # 环境变量示例
└── README.md            # 使用说明
```

## 快速开始

### 1. 配置 settings.toml

编辑 `config/settings.toml`，配置以下必填项：

```toml
# API 配置（必填）
BASE_URL = "https://api.example.com"  # 索引服务器地址
TOKEN = "your-token-here"              # 访问令牌
```

### 2. 启动服务

```bash
# 构建并启动
docker-compose up -d

# 或只启动
docker start acemcp-server
```

### 3. 验证运行

```bash
# 查看容器状态
docker ps

# 查看日志
docker logs acemcp-server
```

访问 http://localhost:8080 查看 Web 管理界面。

## 服务器部署

### 部署到远程服务器

1. **上传文件到服务器**

```bash
# 打包项目
cd docker/acemcp
tar -czvf acemcp.tar.gz config/ Dockerfile docker-compose.yml .env.example README.md

# 上传到服务器
scp acemcp.tar.gz user@your-server:/path/to/acemcp/
```

2. **在服务器上配置**

```bash
# 解压
tar -xzvf acemcp.tar.gz
cd acemcp

# 复制并编辑配置
cp .env.example .env
nano config/settings.toml  # 修改 BASE_URL 和 TOKEN

# 启动服务
docker-compose up -d
```

3. **配置防火墙**

```bash
# 开放端口
sudo ufw allow 8080/tcp

# 或使用 iptables
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

### 使用 Docker Compose 管理

```bash
# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 停止服务
docker-compose down
```

## 配置说明

### settings.toml

| 配置项 | 必填 | 说明 | 默认值 |
|--------|------|------|--------|
| BASE_URL | 是 | 索引服务器地址 | - |
| TOKEN | 是 | 访问令牌 | - |
| BATCH_SIZE | 否 | 批量上传数量 | 10 |
| MAX_LINES_PER_BLOB | 否 | 单个代码块最大行数 | 800 |
| LOG_LEVEL | 否 | 日志级别 | info |

### 文件映射

| 宿主机路径 | 容器内路径 | 说明 |
|------------|------------|------|
| `./config/settings.toml` | `/root/.acemcp/settings.toml` | 配置文件（只读） |
| `./data/` | `/root/.acemcp/` | 索引数据 |
| `${PROJECT_PATH}` | `/workspace` | 要索引的项目目录 |

### 端口

| 端口 | 说明 |
|------|------|
| 8080 | Web 管理界面 |

## 索引服务器部署

Ace-Mcp-Node 需要连接到索引服务器。你需要部署后端服务：

### 方案一：部署 Ace-Mcp-Server (Python)

```bash
# 克隆后端服务
git clone https://github.com/yeuxuan/Ace-Mcp.git
cd Ace-Mcp

# 使用 Docker 部署
docker-compose up -d
```

然后将 `BASE_URL` 配置为你的服务器地址。

### 方案二：使用公共服务

如果有公开的 API 服务，直接配置 `BASE_URL` 和 `TOKEN`。

## 验证索引

### Web 界面

访问 http://localhost:8080 或 http://your-server-ip:8080

### CLI 命令

```bash
# 进入容器
docker exec -it acemcp-server sh

# 手动触发索引
acemcp-node --reindex

# 查看项目列表
acemcp-node --list-projects
```

## 故障排除

### 连接失败

```bash
# 检查容器日志
docker logs acemcp-server

# 检查配置是否正确挂载
docker exec acemcp-server cat /root/.acemcp/settings.toml
```

### 索引不更新

```bash
# 手动触发索引
docker exec acemcp-server acemcp-node --reindex

# 查看索引状态
docker exec acemcp-server acemcp-node --status
```

### 权限问题

```bash
# 修复配置文件权限
chmod 644 config/settings.toml

# 修复数据目录权限
chmod -R 777 data/
```

## 网络

当前配置使用外部网络 `niro-network`。如果不存在，可以创建：

```bash
docker network create niro-network
```

或者从 `docker-compose.yml` 中删除 `networks` 部分。

## 清理

```bash
# 停止服务
docker-compose down

# 删除卷（会清除索引数据）
docker-compose down -v

# 删除镜像
docker rmi acemcp-node:latest
```
