package project.backend.domain.member.dto;

import lombok.Builder;
import lombok.Data;
import project.backend.domain.member.entity.ProviderType;

@Data
@Builder
public class MemberResponse {

	private Long id;
	private String username;
	private String email;
	private String nickname;
	private ProviderType provider;
	private String profileImg;
}
