package project.ai.budget;

/** 채팅방의 하루 LLM 토큰 한도를 넘어 리뷰를 건너뛴다 */
public class LlmBudgetExceededException extends RuntimeException {

    public LlmBudgetExceededException(Long roomId) {
        super("오늘 이 채팅방의 AI 사용량 한도를 넘어 리뷰를 건너뛰었습니다 (내일 다시 리뷰됩니다). roomId=" + roomId);
    }

    /** 사용자에게 보여 줄 사유 */
    public static final String REASON = "오늘 AI 사용량 한도 초과";
}
