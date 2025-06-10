package project.backend.auth.dto;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import project.backend.domain.member.dto.MemberResponse;
import project.backend.domain.member.entity.Member;

import java.util.Collection;
import java.util.List;
import project.backend.domain.member.entity.ProviderType;

@Getter
public class MemberDetails implements UserDetails {

	private final Long id;
	private final String username;
	private final String email;
	private final String password;
	private final String nickname;
	private final ProviderType provider;
	private final String profileImg;

	public MemberDetails(Member member) {
		this.id = member.getId();
		this.username = member.getUsername();
		this.email = member.getEmail();
		this.password = member.getPassword();
		this.nickname = member.getNickname();
		this.provider = member.getProvider();
		this.profileImg = member.getProfileImage();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getUsername() {
		return this.username;
	}

	public static MemberResponse toResponse(MemberDetails memberDetails) {
		return MemberResponse.builder()
			.id(memberDetails.getId())
			.username(memberDetails.getUsername())
			.email(memberDetails.getEmail())
			.nickname(memberDetails.getNickname())
			.provider(memberDetails.getProvider())
			.profileImg(memberDetails.getProfileImg())
			.build();
	}

}
