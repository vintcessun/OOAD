FROM node:22-alpine AS build
WORKDIR /web
COPY web/package*.json ./
ARG NPM_REGISTRY=https://mirrors.huaweicloud.com/repository/npm
RUN sed -E -i "s#https://registry\.(npmjs\.org|npmmirror\.com)#${NPM_REGISTRY}#g" package-lock.json \
    && npm ci --no-audit --no-fund --registry="${NPM_REGISTRY}" --maxsockets=8
COPY web/ ./
ENV VITE_API_BASE_URL=/api VITE_IDENTITY_BASE_URL=/ VITE_MOCK_ENABLED=false NODE_OPTIONS=--max-old-space-size=512
RUN npm run test:run -- --maxWorkers=2 && npm run build -- --base=/course/

FROM nginx:1.27-alpine
COPY --from=build /web/dist /usr/share/nginx/html
COPY course-nginx.conf /etc/nginx/templates/default.conf.template
COPY LICENSE /usr/share/licenses/course-project-web/LICENSE
ENV RBAC_PORT=8081 COURSE_PORT=8080
EXPOSE 443
