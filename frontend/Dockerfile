FROM node:22.23.1-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30.4-alpine3.24
USER root
RUN apk upgrade --no-cache
COPY --from=build --chown=101:101 /workspace/dist /usr/share/nginx/html
USER 101:101
EXPOSE 8080 8443
