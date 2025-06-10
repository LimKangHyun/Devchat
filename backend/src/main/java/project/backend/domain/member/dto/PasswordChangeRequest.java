package project.backend.domain.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PasswordChangeRequest {

	@NotBlank(message = "현재 비밀번호를 입력해주세요.")
	String currentPassword;

	@NotBlank(message = "새로운 비밀번호를 입력해주세요.")
	@Size(min = 4, message = "비밀번호는 최소 4자 이상이여야 합니다.")
	String newPassword;

	@NotBlank(message = "새로운 비밀번호 확인을 입력해주세요.")
	@Size(min = 4, message = "비밀번호는 최소 4자 이상이여야 합니다.")
	String confirmPassword;

	@AssertTrue(message = "비밀번호와 확인값이 일치하지 않습니다.")
	boolean isPasswordMatch() {
		return newPassword.equals(confirmPassword);
	}

}
