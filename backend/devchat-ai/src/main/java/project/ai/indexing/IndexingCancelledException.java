package project.ai.indexing;

/** 인덱싱 도중 취소 요청을 받아 멈췄을 때. 호출한 쪽이 저장한 청크를 지우고 "취소됨"으로 알린다. */
public class IndexingCancelledException extends RuntimeException {

    public IndexingCancelledException(Long roomId) {
        super("인덱싱 취소됨. roomId=" + roomId);
    }
}
