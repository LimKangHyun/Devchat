package project.ai.budget;

/** 채팅방의 오늘 LLM 사용량 상태 */
public enum BudgetLevel {
    /** 여유 있음 */
    OK,
    /** 한도에 가까움. 리뷰 맥락(RAG, 함께 바뀐 코드)을 빼서 프롬프트를 줄인다 */
    LOW,
    /** 한도를 넘음. 리뷰와 요약을 건너뛴다 */
    EXCEEDED
}
