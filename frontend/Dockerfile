# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
RUN apk add --no-cache curl netcat-openbsd
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker/healthprobe.sh /healthprobe.sh
RUN chmod +x /healthprobe.sh \
    && mkdir -p /var/cache/nginx \
    && chown -R nginx:nginx /var/cache/nginx

# Defaults match docker-compose service DNS names (overridden on Railway/Render).
ENV NGINX_PORT=5173 \
    CARD_SERVICE_URL=http://card-service:8081 \
    CREDIT_SERVICE_URL=http://credit-service:8082 \
    COLLATERAL_SERVICE_URL=http://ai-collateral-service:8083 \
    SKIP_INFRA_PROBES=false

EXPOSE 5173
CMD ["/bin/sh", "-c", "export NGINX_PORT=\"${PORT:-${NGINX_PORT:-5173}}\"; sh /healthprobe.sh & exec /docker-entrypoint.sh nginx -g 'daemon off;'"]
