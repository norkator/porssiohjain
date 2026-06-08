ARG NODE_IMAGE=public.ecr.aws/docker/library/node:22-alpine
ARG NGINX_IMAGE=public.ecr.aws/docker/library/nginx:1.30.2-alpine

FROM ${NODE_IMAGE} AS build
WORKDIR /app/hybrid-web

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY hybrid-web/package.json hybrid-web/package-lock.json ./
RUN npm ci

COPY hybrid-web ./
COPY devices/shelly/script.js ../devices/shelly/script.js
RUN npm run build

FROM ${NGINX_IMAGE}

COPY hybrid-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/hybrid-web/dist /usr/share/nginx/html

EXPOSE 80
