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

set -a
source /home/ubuntu/Devchat/.env
set +a

# 기존 NEW 컨테이너 제거 (충돌 방지)
docker rm -f dev-chat-backend-$NEW_COLOR || true

# 최신 이미지 pull
docker-compose pull dev-chat-backend-$NEW_COLOR

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

if [ -z "$STATUS" ]; then
  echo "헬스체크 실패 → 롤백"
  docker-compose stop dev-chat-backend-$NEW_COLOR
  exit 1
fi

# nginx.conf 먼저 생성
rm -rf "$DEPLOY_DIR/nginx_proxy/nginx.conf"
export ACTIVE_COLOR=$NEW_COLOR
envsubst '${ACTIVE_COLOR}' < "$DEPLOY_DIR/nginx_proxy/nginx.conf.template" > "$DEPLOY_DIR/nginx_proxy/nginx.conf"

sudo chown -R 65534:65534 $PROJECT_DIR/prometheus-data
sudo chown -R 472:472 $PROJECT_DIR/grafana-data

# nginx 재시작
docker rm -f nginx_proxy || true
docker-compose up -d nginx_proxy

# 구버전 종료
sleep 10
docker-compose stop dev-chat-backend-$OLD_COLOR

echo "$NEW_COLOR" > "$ACTIVE_COLOR_FILE"

echo "배포 완료: $NEW_COLOR 활성화"

docker image prune -f
