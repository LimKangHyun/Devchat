#!/bin/bash
echo "deploy.sh 시작 - NEW_COLOR: $NEW_COLOR"

echo "기존 이미지 제거 중..."
docker rmi limkanghyun/dev-chat-frontend:$NEW_COLOR 2>/dev/null || true
docker rmi limkanghyun/dev-chat-backend:$NEW_COLOR 2>/dev/null || true

echo "Docker Hub 이미지 반영 대기 중..."
sleep 10

# 최신 도커 이미지 내려받기 (pull)
echo "Start docker-compose pull..."
docker pull limkanghyun/dev-chat-frontend:$NEW_COLOR
docker pull limkanghyun/dev-chat-backend:$NEW_COLOR

ACTIVE_COLOR_FILE="$(dirname "$0")/../active_color.txt"

# active_color.txt 없으면 초기값 blue 생성
if [ ! -f "$ACTIVE_COLOR_FILE" ]; then
  echo "blue" > "$ACTIVE_COLOR_FILE"
fi

ACTIVE_COLOR=$(cat "$ACTIVE_COLOR_FILE")

# 포트 할당을 NEW_COLOR 기준으로 변경
if [ "$NEW_COLOR" == "blue" ]; then
    NEW_BACKEND_PORT=8081
    NEW_FRONTEND_PORT=3000
else 
    NEW_BACKEND_PORT=8082
    NEW_FRONTEND_PORT=3001
fi

# 새롭게 띄울 컨테이너 실행
docker-compose up -d dev-chat-backend-$NEW_COLOR
docker-compose up -d dev-chat-frontend-$NEW_COLOR

# 새롭게 띄운 dev-chat-backend-$NEW_COLOR 컨테이너의 헬스체크 (정상작동 확인)
echo "새로운 컨테이너 헬스체크 중..."
for i in {1..60}; do
    STATUS=$(curl -s http://localhost:$NEW_BACKEND_PORT/health | grep "OK")
    if [ "$STATUS" != "" ]; then
        echo "[$NEW_COLOR] 컨테이너 헬스 체크 통과!"
        break;
    fi
    echo -n "."
    sleep 2
done

if [ "$STATUS" == "" ]; then
    echo " [$NEW_COLOR] 컨테이너 헬스 체크 실패! 롤백합니다..."
    docker-compose stop dev-chat-backend-$NEW_COLOR
    docker-compose stop dev-chat-frontend-$NEW_COLOR
    exit 1
fi

# 프론트엔드 헬스체크
echo "새로운 프론트엔드 컨테이너 헬스체크 중..."
for i in {1..60}; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$NEW_FRONTEND_PORT/index.html)
    if [ "$HTTP_CODE" == "200" ]; then
        echo "[$NEW_COLOR] 프론트엔드 헬스 체크 통과!"
        break
    fi
    echo -n "."
    sleep 2
done

if [ "$HTTP_CODE" != "200" ]; then
    echo "[$NEW_COLOR] 프론트엔드 헬스 체크 실패! 롤백합니다..."
    docker-compose stop dev-chat-backend-$NEW_COLOR
    docker-compose stop dev-chat-frontend-$NEW_COLOR
    exit 1
fi

# nginx upstream 설정 파일 변경 (green -> blue / blue -> green)
sed -i "s/dev-chat-backend-$ACTIVE_COLOR/dev-chat-backend-$NEW_COLOR/g" ./nginx.conf
sed -i "s/dev-chat-frontend-$ACTIVE_COLOR/dev-chat-frontend-$NEW_COLOR/g" ./nginx.conf

# nginx reload
docker exec nginx_proxy nginx -s reload

# 기존 컨테이너 종료
docker-compose stop dev-chat-backend-$ACTIVE_COLOR
docker-compose stop dev-chat-frontend-$ACTIVE_COLOR

# active_color.txt 업데이트
echo $NEW_COLOR > "$(dirname "$0")/../active_color.txt"

echo "배포 완료: 현재 활성화된 서비스는 $NEW_COLOR 입니다."