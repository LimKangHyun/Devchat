package project.backend.domain.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
public class MemberInfoUpdateRequest {

	@NotBlank(message = "닉네임을 공백으로 수정할 수 없습니다.")
	@Size(min = 3, message = "닉네임은 최소 3자 이상이여야 합니다.")
	String nickname;

	@Email(message = "올바른 이메일 형식이어야 합니다.")
	String email;

}
