package project.api.domain.chat.chatroom.dto;

/**
 * 저장소 인덱싱 상태. WebSocket(/topic/chat/{roomId})으로 보내는 진행 알림과 같은 모양이라
 * 화면은 조회 결과와 알림을 같은 방식으로 그린다.
 *
 * @param status NONE / RUNNING / COMPLETED / FAILED / CANCELLED (CANCELLED는 알림에서만 온다)
 * @param phase  RUNNING일 때 단계: CLONING / CHUNKING / EMBEDDING. 아직 모르면 null
 * @param done   현재 단계에서 처리한 개수
 * @param total  현재 단계의 전체 개수
 */
public record IndexingStatusResponse(String type, String status, String phase, int done, int total) {

    public static final String TYPE = "INDEXING_PROGRESS";

    public static IndexingStatusResponse of(String status, String phase, int done, int total) {
        return new IndexingStatusResponse(TYPE, status, phase, done, total);
    }

    public static IndexingStatusResponse finished(String status) {
        return new IndexingStatusResponse(TYPE, status, null, 0, 0);
    }
}
