package project.api.domain.chat.chatroom.dto;

import jakarta.validation.constraints.NotBlank;

public record ConnectRepositoryRequest(
        @NotBlank(message = "레포지토리 주소를 입력해주세요")
        String repositoryUrl
) {
}
