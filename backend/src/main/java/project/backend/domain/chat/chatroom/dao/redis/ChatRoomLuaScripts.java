package project.backend.domain.chat.chatroom.dao.redis;

public class ChatRoomLuaScripts {

    private ChatRoomLuaScripts() {}

    /**
     * 메시지 시퀀스 생성
     * 1. 시퀀스 INCR
     * 2. 시퀀스 키 TTL 초기화 (3일)
     * 3. 채팅방을 랭킹 ZSET 상단에 등록
     * 4. 랭킹 MAX 사이즈 초과 시 하위 항목 제거
     * 5. 업데이트된 채팅방 SET에 추가
     * 6. 키가 없었으면 (첫 메시지) -1 반환 → DB에서 복구 필요
     */
    public static final String GEN_MESSAGE_SEQ =
            "local prev = redis.call('GET', KEYS[1]); " +
                    "local seq = redis.call('INCR', KEYS[1]); " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[1]); " +
                    "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[3]); " +
                    "redis.call('ZREMRANGEBYRANK', KEYS[2], 0, ARGV[4]); " +
                    "redis.call('SADD', KEYS[3], ARGV[3]); " +
                    "if prev == false then return -1 end; " +
                    "return seq;";

    /**
     * Redis 시퀀스 복구 후 INCR
     * Redis miss 발생 시 DB 시퀀스로 복구(NX) 후 INCR
     * 1. DB 시퀀스로 SET (키 없을 때만 - NX)
     * 2. 시퀀스 INCR
     * 3. TTL 초기화, 랭킹/업데이트 SET 갱신
     */
    public static final String RECOVER_AND_INCR =
            "redis.call('SET', KEYS[1], ARGV[1], 'NX'); " +
                    "local seq = redis.call('INCR', KEYS[1]); " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[2]); " +
                    "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4]); " +
                    "redis.call('ZREMRANGEBYRANK', KEYS[2], 0, ARGV[5]); " +
                    "redis.call('SADD', KEYS[3], ARGV[4]); " +
                    "return seq;";

    /**
     * 업데이트된 채팅방 목록 조회 후 초기화
     * DB sync 배치에서 사용
     * SMEMBERS로 전체 조회 후 DEL로 초기화 (원자적 처리)
     */
    public static final String GET_AND_CLEAR_UPDATED_ROOMS =
            "local members = redis.call('SMEMBERS', KEYS[1]); " +
                    "redis.call('DEL', KEYS[1]); " +
                    "return members;";

    /**
     * 시퀀스 안전하게 SET
     * 현재 값보다 큰 경우에만 업데이트 (역행 방지)
     */
    public static final String SET_SEQUENCE =
            "local cur = redis.call('GET', KEYS[1]); " +
                    "if cur == false or tonumber(cur) < tonumber(ARGV[1]) then " +
                    "  redis.call('SET', KEYS[1], ARGV[1]); " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[2]); " +
                    "end;";
}