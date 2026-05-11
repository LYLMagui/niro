# Build stage
FROM node:20-slim AS build-stage
ARG ENV_TYPE=prod
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
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

# 安装时区数据并设置上海时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.conf nginx.test.conf /tmp/nginx/
RUN if [ "$ENV_TYPE" = "test" ]; then \
        cp /tmp/nginx/nginx.test.conf /etc/nginx/conf.d/default.conf; \
    else \
        cp /tmp/nginx/nginx.conf /etc/nginx/conf.d/default.conf; \
    fi \
    && rm -rf /tmp/nginx
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
