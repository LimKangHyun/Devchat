package project.api.domain.aireview.entity;

public enum FileReviewStatus {
    /** AI 서버가 처리 권한을 얻어 리뷰 중. 결과가 오면 COMPLETED/FAILED로 한 번만 바뀐다. */
    PROCESSING,
    COMPLETED,
    FAILED
}
