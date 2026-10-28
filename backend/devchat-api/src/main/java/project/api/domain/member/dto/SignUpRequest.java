package project.api.domain.member.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import project.api.global.util.EmptyToNullDeserializer;


@Data
public class SignUpRequest {

	@NotBlank(message = "ID를 입력해주세요.")
	@Size(min = 5, max = 12, message = "ID는 최소 5자 이상, 12자 이하여야 합니다.")
	private String username;

	@NotBlank(message = "닉네임을 입력해주세요.")
	@Size(min = NicknameRule.MIN, max = NicknameRule.MAX, message = NicknameRule.SIZE_MESSAGE)
	@Pattern(regexp = NicknameRule.PATTERN, message = NicknameRule.MESSAGE)
	private String nickname;

	@JsonDeserialize(using = EmptyToNullDeserializer.class)
	@Email(message = "올바른 이메일 형식이어야 합니다.")
	private String email;

	@NotBlank(message = "비밀번호를 입력해주세요.")
	@Size(min = 4, message = "비밀번호는 최소 4자 이상이여야 합니다.")
	private String password;


}
