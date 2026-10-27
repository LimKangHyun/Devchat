package project.api.domain.chat.chatroom.dto;

import jakarta.validation.constraints.Size;

/**
 * @param conventions 팀 리뷰 규칙. 비우면 규칙을 지운다.
 *                    파일마다 프롬프트에 들어가므로 토큰 비용과 LLM이 규칙을 놓치는 문제를 고려해 길이를 제한한다.
 */
public record ReviewConventionsRequest(
    @Size(max = ReviewConventionsRequest.MAX_LENGTH, message = "팀 리뷰 규칙은 2000자까지 작성할 수 있습니다.")
    String conventions
) {
    public static final int MAX_LENGTH = 2000;
}
