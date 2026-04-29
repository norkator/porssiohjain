FROM node:22-alpine AS build
WORKDIR /app/hybrid-web

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY hybrid-web/package.json hybrid-web/package-lock.json ./
RUN npm ci

COPY hybrid-web ./
COPY devices/shelly/script.js ../devices/shelly/script.js
RUN npm run build

FROM nginx:1.27-alpine

COPY hybrid-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/hybrid-web/dist /usr/share/nginx/html

EXPOSE 80
