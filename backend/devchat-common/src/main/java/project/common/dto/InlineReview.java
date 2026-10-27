package project.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InlineReview(
    @NotNull @Min(1) Integer lineNumber,
    Integer diffLine,
    @NotBlank String comment
) {
    /** Gemini 응답 역직렬화용 (lineNumber, comment만 반환됨) */
    @JsonCreator
    public InlineReview(
        @JsonProperty("lineNumber") Integer lineNumber,
        @JsonProperty("comment") String comment
    ) {
        this(lineNumber, null, comment);
    }

    /** 전체 코멘트로 전환 (유효 라인 매핑 실패 시) */
    public InlineReview asGeneralComment() {
        return new InlineReview(lineNumber, null, comment);
    }

    public boolean isGeneralComment() {
        return diffLine == null;
    }
}