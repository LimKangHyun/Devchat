package project.backend.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class SignUpRequest {

	@NotBlank(message = "이메일은 필수입니다.")
	@Email(message = "올바른 이메일 형식이어야 합니다.")
	private String email;

	@NotBlank(message = "비밀번호는 필수입니다.")
	@Size(min = 4, message = "비밀번호는 최소 4자 이상이여야 합니다.")
	private String password;

	@NotBlank(message = "닉네임은 필수입니다.")
	@Size(min = 3, message = "닉네임은 최소 3자 이상이여야 합니다.")
	private String nickname;

}
