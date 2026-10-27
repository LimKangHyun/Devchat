# Grafana 설정

대시보드·데이터 소스·알림은 `provisioning/` 아래 파일로 관리한다. 서버에서 Grafana를 다시 띄우면 반영된다.

```bash
docker restart grafana            # 파일 내용만 바뀐 경우
docker-compose up -d grafana      # docker-compose.yml의 grafana 설정(환경 변수 등)이 바뀐 경우
```

## 대시보드 수정
`provisioning/dashboards/json/devchat-ai-rag.json`은 직접 고치지 않고 `build-dashboard.js`를 고친 뒤 다시 생성한다.

```bash
cd deployment/grafana && node build-dashboard.js
```

## 리뷰 품질 패널용 MySQL 읽기 전용 계정
리뷰 품질 패널은 MySQL을 SQL로 직접 조회한다. Grafana에는 필요한 테이블의 SELECT 권한만 준다.

1. MySQL 접속: `docker exec -it dev-chat-mysql mysql -u root -p devchat`
2. 계정 생성 (비밀번호는 바꿔서 사용)
   ```sql
   CREATE USER 'grafana_ro'@'%' IDENTIFIED BY '바꿀_비밀번호';
   GRANT SELECT ON devchat.ai_review_comment TO 'grafana_ro'@'%';
   GRANT SELECT ON devchat.ai_comment_mod TO 'grafana_ro'@'%';
   GRANT SELECT ON devchat.ai_review TO 'grafana_ro'@'%';
   ```
3. 서버 `.env`에 추가
   ```
   GRAFANA_MYSQL_USER=grafana_ro
   GRAFANA_MYSQL_PASSWORD=바꿀_비밀번호
   ```
4. `docker-compose up -d grafana`

## 필요한 환경 변수 (서버 .env)
| 변수 | 용도 |
|---|---|
| `DISCORD_WEBHOOK_URL` | 알림을 보낼 Discord 웹훅 |
| `GRAFANA_MYSQL_USER`, `GRAFANA_MYSQL_PASSWORD` | 리뷰 품질 패널용 MySQL 읽기 전용 계정 |
