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
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/healthprobe.sh /healthprobe.sh
RUN chmod +x /healthprobe.sh \
    && mkdir -p /var/cache/nginx \
    && chown -R nginx:nginx /var/cache/nginx
EXPOSE 5173
CMD ["/bin/sh", "-c", "sh /healthprobe.sh & exec nginx -g 'daemon off;'"]
