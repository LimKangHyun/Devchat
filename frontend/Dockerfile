# 1단계: 빌드
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

# GitHub Actions에서 전달받을 값들
ARG REACT_APP_BASE_API_URL
ARG REACT_APP_WEB_SOCKET_URL
ARG REACT_APP_PROFILE_IMAGE_URL
ARG REACT_APP_CHAT_IMAGE_URL
ARG REACT_APP_DEBUG
ARG CI

COPY . .

# 빌드 시 env 주입
RUN REACT_APP_BASE_API_URL=$REACT_APP_BASE_API_URL \
    REACT_APP_WEB_SOCKET_URL=$REACT_APP_WEB_SOCKET_URL \
    REACT_APP_PROFILE_IMAGE_URL=$REACT_APP_PROFILE_IMAGE_URL \
    REACT_APP_CHAT_IMAGE_URL=$REACT_APP_CHAT_IMAGE_URL \
    REACT_APP_DEBUG=$REACT_APP_DEBUG \
    CI=$CI \
    npm run build

# 2단계: nginx
FROM nginx:stable-alpine
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]