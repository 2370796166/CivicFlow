# syntax=docker/dockerfile:1
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /web
COPY civicflow-web/package.json civicflow-web/package-lock.json ./
RUN --mount=type=cache,id=civicflow-npm,target=/root/.npm npm ci --registry=https://registry.npmjs.org
COPY civicflow-web/ ./
RUN npm run build

FROM nginx:1.28-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist/ /usr/share/nginx/html/
EXPOSE 80
