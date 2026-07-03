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

# CI에서 전달된 변경 감지 플래그. workflow_dispatch 등으로 값이 없이 들어오면
# 안전하게 "둘 다 배포"로 기본 처리한다.
export DEPLOY_BACKEND="${DEPLOY_BACKEND:-true}"
export DEPLOY_AI="${DEPLOY_AI:-true}"
echo "DEPLOY_BACKEND=$DEPLOY_BACKEND, DEPLOY_AI=$DEPLOY_AI"

set -a
source /home/ubuntu/Devchat/.env
set +a
echo "ENV 로드 완료"
cd /home/ubuntu/Devchat/deployment
chmod +x ./deploy.sh
./deploy.sh || { echo "❌ deploy.sh 실패"; exit 1; }
