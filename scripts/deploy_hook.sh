#!/bin/bash
set -e

# 현재 active color 읽어서 NEW_COLOR 결정
ACTIVE_COLOR_FILE="/home/ubuntu/NBE5-7-2-TEAM08/deployment/active_color.txt"
CURRENT_COLOR=$(cat "$ACTIVE_COLOR_FILE" 2>/dev/null || echo "blue")

if [ "$CURRENT_COLOR" == "blue" ]; then
    export NEW_COLOR="green"
else
    export NEW_COLOR="blue"
fi

# 환경변수 로드
set -a
source /home/ubuntu/.env
set +a

cd /home/ubuntu/NBE5-7-2-TEAM08/deployment
chmod +x ./deploy.sh
./deploy.sh
