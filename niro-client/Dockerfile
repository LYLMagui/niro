# Build stage
FROM node:20-slim AS build-stage
ARG ENV_TYPE=prod
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG ALL_PROXY
ARG NO_PROXY
WORKDIR /app
ENV http_proxy=${HTTP_PROXY} \
    https_proxy=${HTTPS_PROXY} \
    all_proxy=${ALL_PROXY} \
    no_proxy=${NO_PROXY} \
    HTTP_PROXY=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    ALL_PROXY=${ALL_PROXY} \
    NO_PROXY=${NO_PROXY}
RUN npm config set registry https://registry.npmmirror.com \
    && npm install -g pnpm@9
# 设置 pnpm 镜像源为国内腾讯云/淘宝镜像
RUN pnpm config set registry https://registry.npmmirror.com
COPY package.json pnpm-lock.yaml ./
# 暂时去掉 --frozen-lockfile 尝试修复依赖不一致问题
RUN pnpm install
COPY . .
# 增加 Node.js 内存限制，防止 Vite 构建时 OOM
ENV NODE_OPTIONS="--max-old-space-size=2048"
RUN pnpm build-only

# Production stage
FROM nginx:stable-alpine
ARG ENV_TYPE=prod
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG ALL_PROXY
ARG NO_PROXY

# 安装时区数据并设置上海时区
ENV TZ=Asia/Shanghai \
    http_proxy=${HTTP_PROXY} \
    https_proxy=${HTTPS_PROXY} \
    all_proxy=${ALL_PROXY} \
    no_proxy=${NO_PROXY} \
    HTTP_PROXY=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    ALL_PROXY=${ALL_PROXY} \
    NO_PROXY=${NO_PROXY}
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.${ENV_TYPE}.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
