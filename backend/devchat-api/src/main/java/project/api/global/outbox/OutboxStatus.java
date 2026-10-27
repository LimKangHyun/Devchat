package project.api.global.outbox;

public enum OutboxStatus {
    /** 보낼 차례를 기다림 (실패 후 재시도 대기 포함) */
    PENDING,
    /** 처리 완료 */
    SENT,
    /** 최대 시도 횟수를 넘겨 포기함 */
    FAILED
}
