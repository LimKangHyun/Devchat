#!/bin/bash
echo "deploy.sh 시작 - NEW_COLOR: $NEW_COLOR"
echo "기존 이미지 제거 중..."
docker rmi limkanghyun/dev-chat-backend:latest 2>/dev/null || true
echo "Docker Hub 이미지 반영 대기 중..."
sleep 10
# 최신 도커 이미지 내려받기 (pull)
echo "Start docker-compose pull..."
docker-compose pull dev-chat-backend-$NEW_COLOR
ACTIVE_COLOR_FILE="$(dirname "$0")/active_color.txt"
# active_color.txt 없으면 초기값 blue 생성
if [ ! -f "$ACTIVE_COLOR_FILE" ]; then
  echo "blue" > "$ACTIVE_COLOR_FILE"
fi
OLD_COLOR=$(cat "$ACTIVE_COLOR_FILE")
# 포트 할당을 NEW_COLOR 기준으로 변경
if [ "$NEW_COLOR" == "blue" ]; then
    NEW_BACKEND_PORT=8081
else 
    NEW_BACKEND_PORT=8082
fi
export JWT_SECRET=$JWT_SECRET
export RDS_USERNAME=$RDS_USERNAME
export RDS_PASSWORD=$RDS_PASSWORD
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
docker-compose up -d --build dev-chat-backend-$NEW_COLOR
# 새롭게 띄운 dev-chat-backend-$NEW_COLOR 컨테이너의 헬스체크 (정상작동 확인)
echo "새로운 백엔드 컨테이너 헬스체크 중..."
for i in {1..60}; do
    STATUS=$(curl -s http://localhost:$NEW_BACKEND_PORT/actuator/health | grep "UP")
    if [ "$STATUS" != "" ]; then
        echo "[$NEW_COLOR] 백엔드 헬스 체크 통과!"
        break;
    fi
    echo -n "."
    sleep 2
done
if [ "$STATUS" == "" ]; then
    echo " [$NEW_COLOR] 컨테이너 헬스 체크 실패! 롤백합니다..."
    docker-compose stop dev-chat-backend-$NEW_COLOR
    exit 1
fi
# nginx.conf 생성 (템플릿에서 ACTIVE_COLOR 바꿔서)
export ACTIVE_COLOR=$NEW_COLOR
envsubst '${ACTIVE_COLOR}' < ./nginx_proxy/nginx.conf.template > ./nginx_proxy/nginx.conf
# nginx 재시작
docker exec nginx_proxy nginx -s reload
# 기존 컨테이너 종료
docker-compose stop dev-chat-backend-$OLD_COLOR
# active_color.txt 업데이트
echo "$NEW_COLOR" > "$ACTIVE_COLOR_FILE"
echo "배포 완료: 현재 활성화된 서비스는 $NEW_COLOR 입니다."
