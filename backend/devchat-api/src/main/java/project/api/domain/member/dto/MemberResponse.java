package project.api.domain.member.dto;

import lombok.Builder;
import lombok.Data;
import project.api.domain.member.entity.ProviderType;

@Data
@Builder
public class MemberResponse {

	private Long id;
	private String username;
	private String email;
	private String nickname;
	private ProviderType provider;
	private String profileImg;
	// 서비스 관리자인지 (내 정보 조회에서만 채운다)
	private boolean admin;
}
