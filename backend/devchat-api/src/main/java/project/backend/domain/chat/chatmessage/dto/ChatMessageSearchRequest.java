package project.backend.domain.chat.chatmessage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatMessageSearchRequest {

    @NotBlank(message = "검색어를 입력해주세요.")
    private String keyword;
    private Long lastMessageId;
    @Min(value = 1, message = "페이지 사이즈는 1 이상이어야 합니다.")
    private Integer pageSize;

}

