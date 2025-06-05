package project.backend.global.security.dto;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import project.backend.domain.member.dto.MemberResponse;
import project.backend.domain.member.entity.Member;

import java.util.Collection;
import java.util.List;

@Getter
public class MemberDetails implements UserDetails {

	private final Long id;
	private final String email;
	private final String password;
	private final String nickname;
	private final String profileImg;

	public MemberDetails(Member member) {
		this.id = member.getId();
		this.email = member.getEmail();
		this.password = member.getPassword();
		this.nickname = member.getNickname();
		this.profileImg = member.getProfileImage().getStoreFileName();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	public static MemberResponse toResponse(MemberDetails memberDetails) {
		return MemberResponse.builder()
			.id(memberDetails.getId())
			.email(memberDetails.getEmail())
			.nickname(memberDetails.getNickname())
			.profileImg(memberDetails.getProfileImg())
			.build();
	}

}
