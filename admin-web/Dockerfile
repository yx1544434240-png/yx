FROM node:22-alpine AS build
WORKDIR /app
COPY admin-web/package*.json ./
RUN npm ci
COPY admin-web/ ./
RUN npm run build
FROM nginx:1.28-alpine
COPY admin-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
