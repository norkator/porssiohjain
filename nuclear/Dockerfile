ARG NODE_IMAGE=public.ecr.aws/docker/library/node:22-alpine
ARG NGINX_IMAGE=public.ecr.aws/docker/library/nginx:1.30.2-alpine

# Build static assets natively for both runtime architectures.
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS build
WORKDIR /app/nuclear

COPY nuclear/package.json nuclear/package-lock.json ./
RUN npm ci

COPY nuclear ./
RUN npm run build

FROM ${NGINX_IMAGE}
COPY nuclear/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/nuclear/dist /usr/share/nginx/html
EXPOSE 80
