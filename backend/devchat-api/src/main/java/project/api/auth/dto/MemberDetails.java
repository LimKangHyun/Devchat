package project.api.auth.dto;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import project.api.domain.member.dto.MemberResponse;
import project.api.domain.member.entity.Member;

import java.util.Collection;
import java.util.List;
import project.api.domain.member.entity.ProviderType;

@Getter
public class MemberDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final String nickname;
    private final ProviderType provider;
    private final String profileImg;
    // 로그인 세션 id (토큰의 sid). 옛 토큰이면 null
    private final String sessionId;

    public MemberDetails(Member member) {
        this.id = member.getId();
        this.username = member.getUsername();
        this.email = member.getEmail();
        this.password = member.getPassword();
        this.nickname = member.getNickname();
        this.provider = member.getProvider();
        this.profileImg = member.getProfileImage();
        this.sessionId = null;
    }

    public MemberDetails(Long id, String username, String nickname, String profileImg) {
        this(id, username, nickname, profileImg, null);
    }

    public MemberDetails(Long id, String username, String nickname, String profileImg,
                         String sessionId) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.profileImg = profileImg;
        this.sessionId = sessionId;
        this.email = null;
        this.password = null;
        this.provider = null;
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
