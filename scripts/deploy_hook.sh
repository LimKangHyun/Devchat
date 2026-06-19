#!/bin/bash
set -e

ACTIVE_COLOR_FILE="/home/ubuntu/NBE5-7-2-TEAM08/deployment/active_color.txt"

# 파일 없으면 초기화
if [ ! -f "$ACTIVE_COLOR_FILE" ]; then
    echo "blue" > "$ACTIVE_COLOR_FILE"
fi

CURRENT_COLOR=$(cat "$ACTIVE_COLOR_FILE")

if [ "$CURRENT_COLOR" == "blue" ]; then
    export NEW_COLOR="green"
else
    export NEW_COLOR="blue"
fi

echo "현재 색상: $CURRENT_COLOR → 새 배포 색상: $NEW_COLOR"

# env 로드
set -a
source /home/ubuntu/.env
set +a

echo "ENV 로드 완료"
echo "DOMAIN_URL=$DOMAIN_URL"

cd /home/ubuntu/Devchat/deployment
chmod +x ./deploy.sh

./deploy.sh || { echo "❌ deploy.sh 실패"; exit 1; }
