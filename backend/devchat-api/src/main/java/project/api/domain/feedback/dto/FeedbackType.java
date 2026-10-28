package project.api.domain.feedback.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedbackType {
    BUG("버그"),
    IDEA("제안"),
    OTHER("기타");

    private final String label;
}
