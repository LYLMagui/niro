FROM python:3.10-slim

WORKDIR /app

# 替换为清华大学 Debian 镜像源以加速 apt-get install
RUN sed -i 's/deb.debian.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list.d/debian.sources || \
    sed -i 's/deb.debian.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list

# 安装必要的系统依赖和时区数据
# 移除了 libpq-dev (PostgreSQL依赖)，因为项目已移除 DB 直连模块
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    tzdata \
    && ln -fs /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

# 设置时区环境变量
ENV TZ=Asia/Shanghai

# 设置日志目录环境变量
ENV LOG_DIR=/app/logs

COPY requirements.txt .

# 使用清华源加速 pip 安装
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

COPY . .

# 设置环境变量，确保导入路径正确
ENV PYTHONPATH=/app

# 创建日志目录
RUN mkdir -p $LOG_DIR

# 挂载日志目录，方便宿主机查看
VOLUME $LOG_DIR

# 启动入口更改为 main.py (异步消息驱动架构)
CMD ["python", "main.py"]
