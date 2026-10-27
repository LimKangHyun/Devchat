package project.common.message.index;

/**
 * 저장소 인덱싱에서 API 서버와 AI 서버가 함께 쓰는 Redis 키·채널·스트림 이름.
 * 두 서버가 같은 이름을 써야 동작하므로 한곳에 둔다. 예전에는 결과 스트림 이름이 서로 달라
 * (repo-index / repo-indexing) 인덱싱이 끝나도 API 서버가 결과를 받지 못했다.
 */
public final class IndexingKeys {

    private IndexingKeys() {}

    /** AI 서버 → API 서버: 인덱싱 결과(성공·실패·취소). */
    public static final String RESULT_STREAM = "stream:repo-index:result";

    /** API 서버 → AI 서버: 취소 요청 플래그. 값이 있으면 진행 중인 인덱싱이 멈추고 저장한 청크를 지운다. */
    public static final String CANCEL_KEY_PREFIX = "repo:indexing:cancel:";

    /** AI 서버가 기록하는 현재 진행률 (Hash). 채팅방에 나중에 들어온 사람이 현재 상태를 읽는다. */
    public static final String PROGRESS_KEY_PREFIX = "repo:indexing:progress:";

    /** AI 서버 → API 서버: 진행률 변경 알림 (Pub/Sub). API 서버가 받아 채팅방 WebSocket으로 전달한다. */
    public static final String PROGRESS_CHANNEL = "repo-index:progress";

    /**
     * AI 서버가 인덱싱하는 동안 잡는 락. 대기 중(다른 저장소 인덱싱이 끝나기를 기다림)일 때도 잡혀 있다.
     * API 서버는 이 락으로 DB의 RUNNING이 실제로 진행 중인지, 비정상 종료로 남은 상태인지 구분한다.
     */
    public static String lockKey(Long roomId) {
        return "repo:indexing:" + roomId;
    }

    /**
     * API 서버가 인덱싱 요청을 보냈다는 표시. AI 서버가 요청을 받아 락을 잡기 전까지의 틈 동안,
     * 락이 없다고 비정상 종료로 오판하지 않게 한다. AI 서버는 인덱싱이 끝나면 지운다.
     */
    public static String requestedKey(Long roomId) {
        return "repo:indexing:requested:" + roomId;
    }

    public static String cancelKey(Long roomId) {
        return CANCEL_KEY_PREFIX + roomId;
    }

    public static String progressKey(Long roomId) {
        return PROGRESS_KEY_PREFIX + roomId;
    }
}
