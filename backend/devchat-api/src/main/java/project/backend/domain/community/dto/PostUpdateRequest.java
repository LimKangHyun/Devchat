package project.backend.domain.community.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class PostUpdateRequest {

    @Size(min = 2, max = 100, message = "제목은 2자 이상 100자 이하로 입력해주세요.")
    private String title;

    @Size(min = 10, max = 5000, message = "내용은 10자 이상 5000자 이하로 입력해주세요.")
    private String content;

    @Min(value = 2, message = "모집 인원은 2명 이상이어야 합니다.")
    @Max(value = 20, message = "모집 인원은 20명 이하여야 합니다.")
    private Integer maxCount;

    @Size(max = 20, message = "태그는 20자 이하로 입력해주세요.")
    private String tag;

    @Size(max = 10, message = "진행 방식은 10자 이하로 입력해주세요.")
    private String mode;

    @Future(message = "마감일은 오늘 이후 날짜여야 합니다.")
    private LocalDate deadline;

    @Size(max = 10, message = "기술 스택은 최대 10개까지 선택 가능합니다.")
    private List<@Size(max = 30, message = "기술 스택 이름은 30자 이하여야 합니다.") String> techStacks;
}