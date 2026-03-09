#!/bin/bash

WORK_DIR="/home/mihomo/config"
LOG_FILE="$WORK_DIR/update.log"
SUBSCRIPTION_URL="https://subscription.riolu.link/RioLU/system/api/v1/client/subscribe?token=d67d2cc41ed94c8cd6ac3edc1f515845"

cd "$WORK_DIR" || exit 1

log_msg() {
    echo "[$(date "+%Y-%m-%d %H:%M:%S")] $1" | tee -a "$LOG_FILE"
}

log_msg "🚀 开始获取订阅..."

# 1. 下载：增加 -f 参数，如果服务器返回 404 等错误会直接失败
curl -L -f -s -H "User-Agent: ClashMeta" -o config_temp.yaml "$SUBSCRIPTION_URL"

if [ $? -ne 0 ] || [ ! -s config_temp.yaml ]; then
    log_msg "❌ 下载失败，请检查 URL 或网络"
    exit 1
fi

# 检查下载的是不是 HTML（有时候机场会返回人机验证页面）
if grep -q "<html" config_temp.yaml; then
    log_msg "❌ 严重错误：下载到了网页而不是配置文件，可能是触发了机场防爬"
    exit 1
fi

# 2. 修正管理项
log_msg "修正控制器设置..."
sed -i "/external-controller:/d" config_temp.yaml
sed -i "/secret:/d" config_temp.yaml
sed -i "1a secret: 'Lyl010430.'" config_temp.yaml
sed -i "1a external-controller: :9090" config_temp.yaml

# 3. 校验并覆盖
if grep -q "proxies:" config_temp.yaml; then
    mv config_temp.yaml config.yaml
    log_msg "✅ 配置已物理更新"
    docker restart mihomo
else
    log_msg "❌ 格式校验失败，保留旧文件"
    rm config_temp.yaml
fi