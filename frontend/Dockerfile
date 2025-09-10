# 1단계: 빌드 스테이지
FROM node:22-alpine AS build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install

COPY .env.production .env

COPY . .
RUN npm run build

# 2단계: Nginx에 빌드 결과물 복사하고 서빙
FROM nginx:stable-alpine
COPY --from=build-stage /app/build /usr/share/nginx/html

# 필요하면 nginx 설정파일 덮어쓰기 가능
# COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]