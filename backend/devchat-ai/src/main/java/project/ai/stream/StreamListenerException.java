package project.ai.stream;

import org.springframework.data.redis.connection.stream.RecordId;

/** 리스너가 던진 예외에 메시지 id를 붙인다. 에러 로그에서 어떤 메시지였는지 찾을 수 있게 한다. */
public class StreamListenerException extends RuntimeException {

    private final transient RecordId recordId;

    public StreamListenerException(RecordId recordId, RuntimeException cause) {
        super("스트림 리스너 처리 실패: recordId=" + recordId, cause);
        this.recordId = recordId;
    }

    public RecordId getRecordId() {
        return recordId;
    }
}
