#!/bin/bash

ACTIVE_COLOR=$(cat "$(dirname "$0")/../active_color.txt")

if [ "ACTIVE_COLOR" == "blue" ]; then
    NEW_COLOR="green"
    NEW_PORT=8082
else 
    NEW_COLOR="blue"
    NEW_PORT=8081
fi

# 새롭게 띄울 컨테이너 실행
docker-compose up -d dev-chat-backend-$NEW_COLOR
docker-compose up -d dev-chat-frontend-$NEW_COLOR

# 새롭게 띄운 dev-chat-backend-$NEW_COLOR 컨테이너의 헬스체크 (정상작동 확인)
echo "새로운 컨테이너 헬스체크 중..."
for i in {1..30}; do
    STATUS=$(curl -s http://localhost:$NEW_PORT/health | grep "OK")
    if [ "STATUS" != "" ]; then
        echo "[$NEW_COLOR] 컨테이너 헬스 체크 통과!"
        break;
    fi
    echo -n "."
    sleep 1
done

if [ "$STATUS" == "" ]; then
    echo " [$NEW_COLOR] 컨테이너 헬스 체크 실패! 롤백합니다..."
    docker-compose stop dev-chat-backend-$NEW_COLOR
    docker-compose stop dev-chat-frontend-$NEW_COLOR
    exit 1
done

# 프론트엔드 헬스체크
echo "새로운 프론트엔드 컨테이너 헬스체크 중..."
for i in {1..30}; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$NEW_FRONTEND_PORT/index.html)
    if [ "$HTTP_CODE" == "200" ]; then
        echo "[$NEW_COLOR] 프론트엔드 헬스 체크 통과!"
        break
    fi
    echo -n "."
    sleep 1
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