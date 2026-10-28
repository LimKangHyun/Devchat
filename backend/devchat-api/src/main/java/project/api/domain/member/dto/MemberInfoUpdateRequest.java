package project.api.domain.member.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import project.api.global.util.EmptyToNullDeserializer;

@Getter
public class MemberInfoUpdateRequest {

	@NotBlank(message = "닉네임을 공백으로 수정할 수 없습니다.")
	@Size(min = NicknameRule.MIN, max = NicknameRule.MAX, message = NicknameRule.SIZE_MESSAGE)
	@Pattern(regexp = NicknameRule.PATTERN, message = NicknameRule.MESSAGE)
	String nickname;

	@JsonDeserialize(using = EmptyToNullDeserializer.class)
	@Email(message = "올바른 이메일 형식이어야 합니다.")
	String email;

}
