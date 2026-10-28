package project.api.domain.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotNull(message = "피드백 종류를 골라 주세요.")
        FeedbackType type,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 80, message = "제목은 80자까지 쓸 수 있어요.")
        String title,

        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 3000, message = "내용은 3000자까지 쓸 수 있어요.")
        String content,

        // 어느 화면에서 보냈는지, 어떤 브라우저인지 (버그 재현용, 없어도 됨)
        @Size(max = 300) String pagePath,
        @Size(max = 400) String userAgent
) {
}
