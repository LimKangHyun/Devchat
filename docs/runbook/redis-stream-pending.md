# Redis Stream 쌓인 메시지 확인·정리

AI 서버(devchat-ai)는 시작할 때 스트림마다 ACK 안 된 메시지(PEL)가 있으면 이런 WARN을 남긴다.

```
ACK 안 된 스트림 메시지가 남아 있음(자동 재처리 안 함) - stream=stream:repo-index:request, group=ai-repo-index-group, pending=2, oldestId=1784970427916-0
```

**자동으로 다시 처리하지 않는다.** 이유는 셋이다.
1. AI 리뷰를 뺀 컨슈머(인덱싱·파일 재인덱싱·인덱스 삭제·Git 요약)는 ACK를 하지 않는다. PEL에는 잃어버린 메시지와
   이미 처리한 메시지가 섞여 있어서 구분할 수 없다. 다시 돌리면 처리한 요청까지 모두 다시 처리한다.
2. 인덱싱(`RepoIndexingService.indexRepository`)은 채팅방이 아직 있는지, 인덱싱 상태가 RUNNING인지 보지 않는다.
   지워졌거나 취소된 채팅방의 오래된 요청을 다시 돌리면 그 방을 인덱싱하고 임베딩 비용을 쓴다.
3. Git 요약은 다시 돌리면 같은 요약 결과를 한 번 더 발행한다.

아래 순서로 직접 보고 정리한다.
(AI 리뷰 스트림 `stream:ai-review:request`는 `AiReviewPelReclaimScheduler`가 알아서 재처리·데드레터 처리하므로 손대지 않는다.)

## 스트림과 그룹

| 스트림 | 그룹 |
| --- | --- |
| `stream:ai-review:request` | `ai-review-group` |
| `stream:repo-index:request` | `ai-repo-index-group` |
| `stream:file-reindex:request` | `ai-file-reindex-group` |
| `stream:delete-index:request` | `ai-delete-index-group` |
| `stream:git-summary:request` | `ai-git-summary-group` |

## 1. 상태 보기

서버(/home/ubuntu/Devchat)에서 실행한다. redis.conf에 비밀번호(requirepass)가 있으면 `redis-cli` 뒤에 `-a <비밀번호>`를 붙인다.

```bash
S=stream:repo-index:request
G=ai-repo-index-group

# pending: ACK 안 된 수, lag: 아직 아무에게도 전달 안 된 수, last-delivered-id: 마지막으로 전달한 메시지
docker exec redis-stream redis-cli XINFO GROUPS $S

# PEL 요약 (총 개수, 가장 오래된/최근 id, 컨슈머별 개수)
docker exec redis-stream redis-cli XPENDING $S $G

# PEL 상세: id, 컨슈머, 마지막 전달 뒤 지난 시간(ms), 전달 횟수
docker exec redis-stream redis-cli XPENDING $S $G - + 20

# 메시지 내용 (roomId 등을 보고 아직 있는 채팅방인지 확인)
docker exec redis-stream redis-cli XRANGE $S <id> <id>
```

참고: 인덱싱·파일 재인덱싱·인덱스 삭제·Git 요약 컨슈머는 지금 ACK를 하지 않는다. 그래서 이 스트림들의 PEL에는
**이미 처리한 메시지도 남아 있다.** pending이 있다고 처리가 안 된 것은 아니니, 내용과 그 채팅방의 인덱싱 상태를 같이 본다.

## 2. PEL 정리 (XACK)

다시 처리할 필요가 없다고 확인한 메시지만 ACK해서 PEL에서 뺀다. 스트림의 메시지 자체는 지워지지 않는다.

```bash
# 하나씩
docker exec redis-stream redis-cli XACK $S $G <id> [<id> ...]

# 그 그룹의 PEL 전부 (최대 1000개). XPENDING 상세 출력은 메시지마다 4줄이라 첫 줄(id)만 고른다
docker exec redis-stream redis-cli XPENDING $S $G - + 1000 | awk 'NR % 4 == 1' \
  | xargs -r docker exec redis-stream redis-cli XACK $S $G

# 확인: pending 0
docker exec redis-stream redis-cli XPENDING $S $G
```

## 3. 아직 전달되지 않은 메시지(lag) 다루기

`lag`가 0이 아니면, 구독이 다시 살아나는 순간(AI 서버 재시작·배포 포함) 그 메시지들이 **바로 처리된다.**
배포 전에 내용을 보고, 처리하면 안 되는 요청이면 건너뛴다.

```bash
# last-delivered-id 다음부터 끝까지 (XINFO GROUPS에서 본 id를 넣는다)
docker exec redis-stream redis-cli XRANGE $S "(<last-delivered-id>" +

# 전부 건너뛰기: 그룹 위치를 스트림 끝으로 옮긴다. 이후 새로 들어오는 요청부터 읽는다
docker exec redis-stream redis-cli XGROUP SETID $S $G '$'
```

## 4. 시간 초과로 잃어버린 요청 되살리기

2026-11 전까지 운영의 Redis 명령 시간 제한(`spring.data.redis.timeout`)이 500ms였고, 스트림 폴링은 2초 동안 기다렸다
(`XREADGROUP ... BLOCK 2000`). 그래서 기다리는 사이 들어온 메시지는 Redis가 컨슈머에게 넘기고 PEL에 올렸지만,
클라이언트는 이미 시간 초과로 응답을 버렸다. 이런 메시지는 **전달 횟수 1, 한 번도 처리되지 않은 채** PEL에 남는다.
지금은 시간 제한을 5초로 올렸고, 설정이 다시 어긋나면 `StreamPollTimeout`이 폴링 대기를 줄이고 ERROR를 남긴다.

PEL에 남은 메시지는 같은 그룹이 다시 읽어 주지 않는다(구독은 새 메시지 `>`만 읽는다). 되살리는 방법은 두 가지다.

```bash
# 1) 내용 확인: roomId가 아직 있는 채팅방이고, 인덱싱이 끝나지 않은 방인지 본다
docker exec redis-stream redis-cli XRANGE $S <id> <id>

# 2-a) (권장) 화면에서 그 방의 인덱싱을 다시 요청한다. 새 메시지로 들어가 정상 처리된다. 그 뒤 옛 메시지를 ACK한다
docker exec redis-stream redis-cli XACK $S $G <id>

# 2-b) 같은 내용을 새 메시지로 다시 넣고 옛 메시지를 ACK한다 (화면에서 다시 요청할 수 없을 때)
#      XRANGE로 본 필드 이름(보통 payload)과 값을 그대로 쓴다
docker exec redis-stream redis-cli XADD $S '*' <field> '<value>'
docker exec redis-stream redis-cli XACK $S $G <id>
```

이미 지워졌거나 취소된 채팅방의 요청이면 다시 넣지 말고 XACK만 한다.

## 5. 구독이 끊겼는지 보기

```bash
# 스트림별 구독 상태. 하나라도 inactive면 DOWN
docker exec dev-chat-ai curl -s http://localhost:8081/actuator/health/streams
```

Grafana에서는 `devchat_stream_subscription_active{stream=...}`가 1이면 정상, 0이면 끊긴 것이다.
5분 넘게 0이면 "Redis Stream 구독 끊김" 알림이 Discord로 온다. 복구되지 않으면 `docker restart dev-chat-ai`로 다시 띄운다.
