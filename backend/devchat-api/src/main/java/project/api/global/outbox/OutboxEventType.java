package project.api.global.outbox;

public enum OutboxEventType {
    /** AI 리뷰 준비: GitHub에서 대상 파일과 코드를 모아 파일별 요청(AI_REVIEW_REQUEST)을 만든다 */
    AI_REVIEW_PREPARE,
    /** 파일 하나의 AI 리뷰 요청. payload를 그대로 stream:ai-review:request로 보낸다 */
    AI_REVIEW_REQUEST
}
