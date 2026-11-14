# 1. 依赖安装阶段
FROM node:26.13-alpine AS deps
WORKDIR /app
# 复制依赖定义文件
COPY package.json package-lock.json* ./
# 安装依赖 (ci 安装更稳定)
RUN npm ci

# 2. 构建阶段
FROM node:26.13-alpine AS builder
ARG APP_VERSION=3.3.3
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# 禁用 Next.js 的遥测数据
ENV NEXT_TELEMETRY_DISABLED 1
# 构建期用于 ISR 预渲染的安全兜底；运行期由 Kubernetes 注入集群内 API_INTERNAL_URL。
ARG API_INTERNAL_URL=https://huawai.mnnu.eu.org/api/portal
ENV API_INTERNAL_URL=${API_INTERNAL_URL}

# 开始构建
RUN npm run build && printf '{"version":"%s"}\n' "$APP_VERSION" > public/version.json

# 3. 运行阶段 (生产环境)
FROM node:26.13-alpine AS runner
WORKDIR /app

ARG APP_VERSION=3.3.3
ENV NODE_ENV production \
    APP_VERSION=${APP_VERSION}
ENV NEXT_TELEMETRY_DISABLED 1

# 创建非 root 用户 (安全最佳实践)
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# 复制构建产物
# public 目录包含静态资源 (图片等)
COPY --from=builder /app/public ./public

# 自动生成的 standalone 文件夹包含了运行所需的所有最小依赖
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

# Next.js 默认端口是 3000
EXPOSE 3000

ENV PORT 3000
# 绑定 IPv6 wildcard；Node 默认同时接受 IPv4-mapped 连接。
ENV HOSTNAME "::"

# 启动命令 (注意这里运行的是 server.js)
CMD ["node", "server.js"]
