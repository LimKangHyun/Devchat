package project.backend.auth.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.ProviderType;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.ex.AuthException;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LoginService implements UserDetailsService {

	private final MemberService memberService;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		Member foundMember = memberService.getMemberForLogin(email);

		if (foundMember.getProvider() == ProviderType.GITHUB) {
			throw new AuthException(AuthErrorCode.WRONG_AUTH_TYPE_LOGIN);
		}

		log.info("로그인 시도 = {}", foundMember);
		return new MemberDetails(foundMember);
	}
}
