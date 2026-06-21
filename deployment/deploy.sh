#!/bin/bash
set -ex

echo "deploy.sh 시작 - NEW_COLOR: $NEW_COLOR"

PROJECT_DIR="/home/ubuntu/Devchat"
DEPLOY_DIR="$PROJECT_DIR/deployment"
ACTIVE_COLOR_FILE="$DEPLOY_DIR/active_color.txt"

cd "$PROJECT_DIR"

sleep 5

if [ ! -f "$ACTIVE_COLOR_FILE" ]; then
  echo "blue" > "$ACTIVE_COLOR_FILE"
fi

OLD_COLOR=$(cat "$ACTIVE_COLOR_FILE")

# 🔥 핵심: docker-compose 환경변수 로드 보장
set -a
source /home/ubuntu/Devchat/.env
set +a

# nginx 항상 실행
docker ps | grep nginx_proxy || docker-compose up -d nginx_proxy

# 기존 NEW 컨테이너 제거 (충돌 방지)
docker rm -f dev-chat-backend-$NEW_COLOR || true

# 새 컨테이너 실행
docker-compose up -d --no-deps dev-chat-backend-$NEW_COLOR

echo "헬스체크 중..."

STATUS=""
for i in {1..60}; do
  CONTAINER_ID=$(docker ps -qf "name=dev-chat-backend-$NEW_COLOR")

  if [ -n "$CONTAINER_ID" ]; then
    STATUS=$(docker exec $CONTAINER_ID curl -s http://localhost:8080/actuator/health | grep UP || true)
  fi

  if [ -n "$STATUS" ]; then
    echo "[$NEW_COLOR] 헬스 체크 통과"
    break
  fi

  sleep 2
done

# 실패 시 롤백
if [ -z "$STATUS" ]; then
  echo "헬스체크 실패 → 롤백"
  docker-compose stop dev-chat-backend-$NEW_COLOR
  exit 1
fi

# nginx 설정 생성
export ACTIVE_COLOR=$NEW_COLOR
envsubst '${ACTIVE_COLOR}' < "$DEPLOY_DIR/nginx_proxy/nginx.conf.template" > "$DEPLOY_DIR/nginx_proxy/nginx.conf"

# 🔥 DNS 안정화 (이거 없으면 니 에러 그대로 터짐)
echo "DNS 대기..."
for i in {1..15}; do
  docker exec nginx_proxy getent hosts dev-chat-backend-$NEW_COLOR && break
  sleep 2
done

# nginx reload
docker exec nginx_proxy nginx -s reload

# 구버전 종료 (유예)
sleep 10
docker-compose stop dev-chat-backend-$OLD_COLOR

echo "$NEW_COLOR" > "$ACTIVE_COLOR_FILE"

echo "배포 완료: $NEW_COLOR 활성화"

docker image prune -f
