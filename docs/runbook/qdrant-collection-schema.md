# Qdrant 컬렉션 스키마 확인·교체

AI 서버는 청크를 dense(`dense`, 3072, Cosine) + sparse(`bm25`) 이름 있는 벡터로 저장한다(하이브리드 스키마).
이름 없는 단일 벡터로 만든 예전 컬렉션(`devchat`)에는 저장할 수 없고, 인덱싱 마지막 단계에서 이런 오류가 난다.

```
Qdrant 요청 거절. operation=upsert, collection=devchat, status=400, body={... Not existing vector name ...}
Qdrant 컬렉션이 예전 스키마(이름 없는 벡터)라 저장할 수 없습니다. QDRANT_COLLECTION을 새 이름 ...
```

기본 컬렉션 이름은 `devchat_hybrid`다(`QDRANT_COLLECTION`으로 바꾼다). 예전 스키마가 남아 있으면 알림 "Qdrant 컬렉션이 예전 스키마"가 Discord로 온다.

## 1. 스키마 확인

```bash
# schema: hybrid | legacy | missing | unknown. legacy면 DOWN(503)
docker exec dev-chat-ai curl -s http://localhost:8081/actuator/health/qdrant

# 지표 (legacy일 때만 0)
docker exec dev-chat-ai curl -s http://localhost:8081/actuator/prometheus | grep devchat_qdrant_schema_ok
```

결과는 5분 동안 캐시된다. 컬렉션을 바꾼 직후에는 AI 서버를 다시 띄우면 바로 반영된다.
Qdrant 콘솔에서 보려면 컬렉션 정보의 `config.params.vectors`가 `{dense: {...}}`처럼 이름을 가지면 하이브리드다. `{size, distance}`만 있으면 예전 스키마다.

## 2. 컬렉션 바꾸기

```bash
# 서버(/home/ubuntu/Devchat)의 .env
QDRANT_COLLECTION=devchat_hybrid     # 아직 없는 이름이면 AI 서버가 시작할 때 하이브리드 스키마로 만든다

docker-compose up -d --no-deps dev-chat-ai
docker logs dev-chat-ai 2>&1 | grep "Qdrant 컬렉션 생성\|Qdrant 연결 완료"
```

새 컬렉션은 비어 있으므로 RAG 검색 결과가 없다. 다음 단계로 다시 채운다.

## 3. 채팅방 재인덱싱

화면에서 채팅방마다 인덱싱을 다시 요청한다(저장소 연결 후 인덱싱과 같은 동작). 임베딩 비용이 드니 쓰는 방부터 한다.
로그에 `벡터 upsert 완료`가 찍히고 헬스가 `schema: hybrid`이면 정상이다.

## 4. 옛 컬렉션 삭제

새 컬렉션으로 검색과 리뷰가 정상인 것을 확인한 뒤에 지운다. 자동으로 지우지 않는다(데이터가 사라지므로).
Qdrant Cloud 콘솔 > Collections > `devchat` > Delete. 지우기 전에 `QDRANT_COLLECTION`이 가리키는 이름이 아닌지 한 번 더 본다.
