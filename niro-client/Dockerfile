# Build stage
FROM node:20-slim AS build-stage
ARG ENV_TYPE=prod  # 默认值为 prod
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@latest --activate
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
ARG ENV_TYPE=prod  # 默认值为 prod
COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.${ENV_TYPE}.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
