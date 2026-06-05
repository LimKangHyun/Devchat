package project.backend.domain.github.dto;

import jakarta.validation.constraints.Size;
import project.backend.domain.github.entity.InactiveReason;

public record ToggleReviewRequest(
        InactiveReason reason,
        @Size(max = 100, message = "기타 사유는 100자 이내로 입력해주세요.")
        String otherReason
) {}