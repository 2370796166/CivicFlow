# syntax=docker/dockerfile:1
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /web
COPY civicflow-web/package.json civicflow-web/package-lock.json ./
RUN --mount=type=cache,id=civicflow-npm,target=/root/.npm npm ci --registry=https://registry.npmjs.org
COPY civicflow-web/ ./
RUN npm run build

FROM nginx:1.28-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
RUN addgroup -g 10001 -S civicflow && adduser -u 10001 -S -G civicflow civicflow \
    && chown -R 10001:10001 /var/cache/nginx /var/run /usr/share/nginx/html
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/docker/nginx-main.conf /etc/nginx/nginx.conf
COPY --from=build /web/dist/ /usr/share/nginx/html/
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz
ENTRYPOINT ["nginx", "-g", "daemon off;"]
