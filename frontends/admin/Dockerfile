# 保持原样，确认路径无误即可
FROM node:26.13-alpine AS builder
ARG APP_VERSION=3.4.6
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build && printf '{"version":"%s"}\n' "$APP_VERSION" > dist/version.json

FROM nginx:stable-alpine
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]