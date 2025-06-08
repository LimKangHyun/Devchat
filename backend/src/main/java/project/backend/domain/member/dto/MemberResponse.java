package project.backend.domain.member.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberResponse {

	private Long id;
	private String username;
	private String email;
	private String nickname;
	private String profileImg;
}
