package project.api.domain.chat.chatroom.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmailInviteRequest {

    /** 한 번에 보낼 수 있는 최대 주소 수. 스팸 발송 통로가 되지 않도록 제한한다. */
    public static final int MAX_EMAILS = 10;

    @NotEmpty(message = "초대할 이메일을 입력해주세요.")
    @Size(max = MAX_EMAILS, message = "한 번에 최대 10명까지 초대할 수 있습니다.")
    private List<@NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") String> emails;
}
