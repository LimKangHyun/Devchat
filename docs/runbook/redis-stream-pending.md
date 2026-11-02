# Redis Stream 쌓인 메시지 확인·정리

AI 서버(devchat-ai)가 읽는 요청 스트림은 이제 **처리하면 ACK하고, 멈춘 메시지는 자동으로 다시 처리한다.**
평소에는 손댈 일이 없다. 이 문서는 자동 처리가 어떻게 도는지, 데드레터가 생겼을 때와 직접 들여다봐야 할 때 무엇을 보는지 적는다.

## 스트림과 그룹

| 스트림 | 그룹 | ACK 시점 | 재처리 담당 |
| --- | --- | --- | --- |
| `stream:ai-review:request` | `ai-review-group` | 리뷰 결과를 발행한 뒤 | `AiReviewPelReclaimScheduler` (Heartbeat로 처리 중인지 확인) |
| `stream:repo-index:request` | `ai-repo-index-group` | 인덱싱을 비동기 실행기에 넘긴 뒤 | `PendingMessageReclaimer` |
| `stream:file-reindex:request` | `ai-file-reindex-group` | 파일 재인덱싱이 끝난 뒤 | `PendingMessageReclaimer` |
| `stream:delete-index:request` | `ai-delete-index-group` | 인덱스를 지운 뒤 | `PendingMessageReclaimer` |
| `stream:git-summary:request` | `ai-git-summary-group` | 요약 결과를 발행한 뒤 | `PendingMessageReclaimer` |

## 자동 처리

### 재처리와 데드레터 (30초마다)

1. 마지막 전달 뒤 **5분**(`devchat.stream.reclaim.idle-threshold`) 넘게 ACK가 없는 메시지를 XCLAIM으로 가져와 다시 처리한다.
   처리하다 실패했거나, 응답을 잃어버려 한 번도 처리되지 않은 메시지(2026-11 Redis 시간 초과 장애)가 여기에 걸린다.
2. 처음 전달을 포함해 **3번**(`devchat.stream.reclaim.max-deliveries`) 시도해도 남아 있으면 더 시도하지 않는다.
   ERROR 로그(`최대 전달 횟수(3) 초과, 데드레터로 보냄`, 내용은 크기와 앞 200자만)를 남기고, API 서버의 `stream_dead_letter`
   테이블에 기록한 뒤 ACK한다. 기록이 실패하면(`데드레터 기록 실패`) ACK하지 않고 5분 뒤 다시 기록한다.
   한 주기에 스트림마다 최대 10개까지 다루고, 스트림마다 따로 돌아 한 스트림이 느려도 다른 스트림은 멈추지 않는다.

구독은 한 번에 한 메시지만 읽는다(batchSize=1). 여러 개를 한꺼번에 읽으면 기다리는 메시지까지 PEL에 올라가, 앞 메시지들이
오래 걸릴 때 아직 시작도 안 한 메시지가 5분을 넘겨 한 번 더 처리되기 때문이다.
3. 다시 처리해도 같은 결과가 되도록 스트림마다 막아 두었다.
   - 인덱싱: 이미 인덱싱 중(락)이면, 취소 플래그가 있으면(취소·채팅방 삭제), 재처리인데 요청 표시(`repo:indexing:requested:{roomId}`)가
     없으면(이미 끝났거나 10분이 지나 만료) 인덱싱하지 않고 ACK한다.
   - 파일 재인덱싱·인덱스 삭제: 같은 청크 ID로 덮어쓰거나 이미 지운 것을 다시 지울 뿐이다.
   - Git 요약: 결과가 두 번 발행되면 API 서버가 같은 채팅 메시지를 요약으로 한 번 더 덮어쓴다. 해가 없다.

인덱싱은 몇 분씩 걸려서 끝날 때까지 기다리지 않고, 실행기에 넘기면 바로 ACK한다. 그래서 오래 걸리는 인덱싱이 재처리로
두 번 돌지 않는다. 넘긴 뒤의 실패는 결과 스트림(실패·취소)과 인덱싱 락(서버가 죽으면 2분 안에 사라져 API 서버가 FAILED로 정리)이 맡는다.

### 처음 배포할 때 예전 PEL 정리 (한 번)

예전에는 AI 리뷰를 뺀 네 컨슈머가 ACK를 하지 않아, PEL에 이미 처리한 메시지와 잃어버린 메시지가 섞여 쌓였다.
그래서 이 기능이 처음 뜰 때 네 스트림에서 **만들어진 지 10분**(`devchat.stream.reclaim.legacy-cutoff`)이 지난 PEL 메시지를
다시 처리하지 않고 ACK만 한다. 그보다 새것은 위의 재처리로 넘긴다(대부분 이미 처리한 메시지라, 기준을 길게 잡을수록
처리한 요약·재인덱싱을 다시 돌린다). 다 정리하면 Redis에 `devchat:stream:legacy-pel-cleaned:<스트림>` 표시를 남기고,
그 뒤 시작할 때는 건너뛴다. 매번 정리하면 10분 넘게 재시도 중인 메시지를 데드레터 없이 버리게 되기 때문이다.
로그는 스트림마다 이렇게 남는다.

```
예전 PEL 정리 - stream=stream:repo-index:request, 기준=PT10M보다 오래된 2건 ACK(재처리 안 함), 남은 pending=0(자동 재처리 대상)
예전 PEL 정리는 이미 끝남 - stream=stream:repo-index:request        # 두 번째 시작부터
```

다시 정리해야 하면(예: ACK하지 않는 옛 버전으로 되돌렸다가 다시 배포) 표시를 지우고 AI 서버를 다시 띄운다.

```bash
docker exec redis-stream redis-cli DEL devchat:stream:legacy-pel-cleaned:stream:repo-index:request
```

## 지표와 알림

| 지표 | 뜻 |
| --- | --- |
| `devchat_stream_pending{stream}` | ACK 안 된 메시지 수 (30초마다 XPENDING 요약으로 갱신). 평소 0 근처 |
| `devchat_stream_reclaimed_total{stream}` | 멈춘 메시지를 가져와 다시 처리한 횟수 |
| `devchat_stream_dead_letter_total{stream}` | 데드레터로 보낸 수 |
| `devchat_stream_subscription_active{stream}` | 구독이 살아 있으면 1 |

- 15분 안에 데드레터가 하나라도 생기면 "Redis Stream 데드레터 발생" 알림이 온다.
- 5분 넘게 구독이 끊기면 "Redis Stream 구독 끊김" 알림이 온다.

## 데드레터가 생겼을 때

1. devchat-ai 로그에서 `데드레터로 보냄`을 찾는다. 바로 앞의 `재처리 실패` 로그에 실패 원인이 있다.
2. 내용(payload)은 로그와 API 서버 DB의 `stream_dead_letter` 테이블에 남아 있다. 스트림의 메시지 자체도 지워지지 않는다.
3. 원인을 고친 뒤 필요하면 다시 요청한다.
   - 인덱싱: 화면에서 그 채팅방의 인덱싱을 다시 시작한다.
   - 파일 재인덱싱: 다음 push 때 다시 반영되거나, 저장소 전체를 다시 인덱싱한다.
   - 인덱스 삭제: `XADD`로 같은 내용을 다시 넣는다(아래 "직접 다시 넣기").
   - Git 요약: 원본 메시지가 그대로 남아 있을 뿐이라 보통 그대로 둔다.

## 직접 들여다보기

서버(/home/ubuntu/Devchat)에서 실행한다. redis.conf에 비밀번호(requirepass)가 있으면 `redis-cli` 뒤에 `-a <비밀번호>`를 붙인다.

```bash
S=stream:repo-index:request
G=ai-repo-index-group

# pending: ACK 안 된 수, lag: 아직 아무에게도 전달 안 된 수, last-delivered-id: 마지막으로 전달한 메시지
docker exec redis-stream redis-cli XINFO GROUPS $S

# PEL 요약 (총 개수, 가장 오래된/최근 id, 컨슈머별 개수). 재처리한 메시지는 <그룹>-reclaimer 컨슈머로 옮겨 간다
docker exec redis-stream redis-cli XPENDING $S $G

# PEL 상세: id, 컨슈머, 마지막 전달 뒤 지난 시간(ms), 전달 횟수
docker exec redis-stream redis-cli XPENDING $S $G - + 20

# 메시지 내용
docker exec redis-stream redis-cli XRANGE $S <id> <id>
```

pending이 몇 분 넘게 줄지 않으면 재처리가 실패하고 있는 것이다. 로그에서 `재처리 실패`·`PEL 재처리 실패`를 찾는다.

### 자동 처리를 기다리지 않고 버리기 (XACK)

다시 처리하면 안 되는 메시지만 ACK해서 PEL에서 뺀다. 스트림의 메시지 자체는 지워지지 않는다.

```bash
docker exec redis-stream redis-cli XACK $S $G <id> [<id> ...]
```

### 아직 전달되지 않은 메시지(lag) 다루기

`lag`가 0이 아니면, 구독이 다시 살아나는 순간(AI 서버 재시작·배포 포함) 그 메시지들이 **바로 처리된다.**
배포 전에 내용을 보고, 처리하면 안 되는 요청이면 건너뛴다.

```bash
# last-delivered-id 다음부터 끝까지 (XINFO GROUPS에서 본 id를 넣는다)
docker exec redis-stream redis-cli XRANGE $S "(<last-delivered-id>" +

# 전부 건너뛰기: 그룹 위치를 스트림 끝으로 옮긴다. 이후 새로 들어오는 요청부터 읽는다
docker exec redis-stream redis-cli XGROUP SETID $S $G '$'
```

### 직접 다시 넣기

데드레터가 됐거나 예전 PEL 정리로 버려진 요청을 다시 처리해야 하면, 같은 내용을 새 메시지로 넣는다.
인덱싱은 화면에서 다시 시작하는 편이 낫다(요청 표시와 상태가 함께 맞춰진다).

```bash
# XRANGE로 본 필드 이름(보통 payload)과 값을 그대로 쓴다
docker exec redis-stream redis-cli XADD $S '*' <field> '<value>'
```

## 구독이 끊겼는지 보기

```bash
# 스트림별 구독 상태. 하나라도 inactive면 DOWN
docker exec dev-chat-ai curl -s http://localhost:8081/actuator/health/streams
```

`devchat_stream_subscription_active{stream=...}`가 0이면 끊긴 것이다. 복구되지 않으면 `docker restart dev-chat-ai`로 다시 띄운다.
끊긴 동안 들어온 요청은 lag로 남아 있다가 구독이 살아나면 처리된다.
