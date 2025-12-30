FROM python:3.10-slim
WORKDIR /app

# 安装必要的系统依赖（如果是编译某些库需要）
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 设置环境变量，确保导入路径正确
ENV PYTHONPATH=/app

CMD ["python", "run_scanner.py"]
