# Ace-Mcp-Node Docker 镜像
# 构建: docker build -t acemcp-node:latest .

FROM node:18-alpine

# 设置工作目录
WORKDIR /app

# 全局安装 acemcp-node
RUN npm install -g acemcp-node

# 创建配置目录
RUN mkdir -p /root/.acemcp

# 暴露端口
# 8080: Web 管理界面
EXPOSE 8080

# 启动命令
# --host 0.0.0.0 允许外部访问
CMD ["sh", "-c", "acemcp-node --web-port 8080 --host 0.0.0.0"]
