#!/bin/bash
set -e

ACTIVE_COLOR_FILE="/home/ubuntu/Devchat/deployment/active_color.txt"

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

set -a
source /home/ubuntu/Devchat/.env
set +a

echo "ENV 로드 완료"

cd /home/ubuntu/Devchat/deployment
chmod +x ./deploy.sh

./deploy.sh || { echo "❌ deploy.sh 실패"; exit 1; }
