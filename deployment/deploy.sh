#!/bin/bash
set -ex
echo "deploy.sh 시작 - NEW_COLOR: $NEW_COLOR"

# docker compose 인식 문제 해결
export PATH=$PATH:/usr/libexec/docker/cli-plugins

PROJECT_DIR="/home/ubuntu/Devchat"
DEPLOY_DIR="$PROJECT_DIR/deployment"
ACTIVE_COLOR_FILE="$DEPLOY_DIR/active_color.txt"

cd "$PROJECT_DIR"

echo "Docker Hub 이미지 반영 대기 중..."
sleep 10

if [ ! -f "$ACTIVE_COLOR_FILE" ]; then
  echo "blue" > "$ACTIVE_COLOR_FILE"
fi
OLD_COLOR=$(cat "$ACTIVE_COLOR_FILE")

export JWT_SECRET=$JWT_SECRET
export MYSQL_USERNAME=$MYSQL_USERNAME
export MYSQL_PASSWORD=$MYSQL_PASSWORD
export RDS_ENDPOINT=$RDS_ENDPOINT
export REDIS_HOST=$REDIS_HOST
export REDIS_PORT=$REDIS_PORT
export OAUTH_GITHUB_CLIENT_ID=$OAUTH_GITHUB_CLIENT_ID
export OAUTH_GITHUB_SECRET=$OAUTH_GITHUB_SECRET
export DOMAIN_URL=$DOMAIN_URL
export IMAGE_URL=$IMAGE_URL
export WEBHOOK_URL=$WEBHOOK_URL
export AWS_ACCESS_KEY=$AWS_ACCESS_KEY
export AWS_SECRET_KEY=$AWS_SECRET_KEY
export ENCRYPT_SECRET=$ENCRYPT_SECRET
export BOT_GITHUB_BOT_PAT=$BOT_GITHUB_BOT_PAT
export GEMINI_API_KEY=$GEMINI_API_KEY
export PINECONE_API_KEY=$PINECONE_API_KEY
export GEMINI_REVIEW_KEY_1=$GEMINI_REVIEW_KEY_1
export GEMINI_REVIEW_KEY_2=$GEMINI_REVIEW_KEY_2
export GEMINI_EMBEDDING_KEY_1=$GEMINI_EMBEDDING_KEY_1
export GEMINI_EMBEDDING_KEY_2=$GEMINI_EMBEDDING_KEY_2
export GEMINI_EMBEDDING_KEY_3=$GEMINI_EMBEDDING_KEY_3
export GEMINI_EMBEDDING_KEY_4=$GEMINI_EMBEDDING_KEY_4
export GEMINI_EMBEDDING_KEY_5=$GEMINI_EMBEDDING_KEY_5

# 디버그
echo "DEBUG HOME=$HOME"
echo "DEBUG PATH=$PATH"
which docker
docker --version
docker compose version || { echo "❌ docker compose 없음"; exit 1; }

docker compose up -d --no-deps dev-chat-backend-$NEW_COLOR

echo "새로운 백엔드 컨테이너 헬스체크 중..."
STATUS=""
for i in {1..60}; do
  STATUS=$(docker exec dev-chat-backend-$NEW_COLOR curl -s http://localhost:8080/actuator/health | grep "UP" || true)
  if [ -n "$STATUS" ]; then
    echo "[$NEW_COLOR] 백엔드 헬스 체크 통과!"
    break
  fi
  echo -n "."
  sleep 2
done

if [ -z "$STATUS" ]; then
  echo "[$NEW_COLOR] 컨테이너 헬스 체크 실패! 롤백합니다..."
  docker compose stop dev-chat-backend-$NEW_COLOR
  exit 1
fi

# nginx.conf 생성
export ACTIVE_COLOR=$NEW_COLOR
envsubst '${ACTIVE_COLOR}' < "$DEPLOY_DIR/nginx_proxy/nginx.conf.template" > "$DEPLOY_DIR/nginx_proxy/nginx.conf"

docker exec nginx_proxy nginx -s reload

docker compose stop dev-chat-backend-$OLD_COLOR

echo "$NEW_COLOR" > "$ACTIVE_COLOR_FILE"
echo "배포 완료: 현재 활성화된 서비스는 $NEW_COLOR 입니다."

docker image prune -f
