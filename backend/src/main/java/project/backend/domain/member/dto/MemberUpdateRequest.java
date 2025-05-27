package project.backend.domain.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MemberUpdateRequest {

	@NotBlank(message = "비밀번호는 필수입니다.")
	@Size(min = 12, message = "비밀번호는 최소 12자 이상이여야 합니다.")
	private String password;

	@NotBlank(message = "비밀번호 확인은 필수입니다.")
	@Size(min = 4, message = "비밀번호는 최소 4자 이상이여야 합니다.")
	private String confirmPassword;

	@AssertTrue(message = "비밀번호와 확인값이 일치하지 않습니다.")
	public boolean isPasswordMatch() {
		return password.equals(confirmPassword);
	}

	@NotBlank(message = "닉네임은 필수입니다.")
	@Size(min = 3, message = "닉네임은 최소 3자 이상이여야 합니다.")
	private String nickname;

	private MultipartFile profileImg;

}
