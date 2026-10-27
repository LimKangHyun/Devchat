package project.common.message.aireview;

/**
 * AI 서버가 파일 리뷰를 시작하기 전에 API 서버에 처리 권한을 요청한 결과.
 * 같은 파일을 두 Worker가 동시에 리뷰하지 않게 한다.
 */
public enum FileClaimResult {
    /** 처리 권한을 얻음. 리뷰를 진행한다. */
    CLAIMED,
    /** 다른 Worker가 처리 중이거나 이미 끝남. 건너뛴다. */
    ALREADY_TAKEN,
    /** 그사이 새 커밋으로 리뷰가 다시 시작돼, 이 요청은 예전 커밋 것이다. 건너뛴다. */
    STALE_COMMIT
}
