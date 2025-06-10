package project.backend.domain.member.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.backend.domain.imagefile.ImageFileService;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.dto.PasswordChangeRequest;
import project.backend.domain.member.dto.event.ProfileUpdateEvent;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.dto.MemberResponse;
import project.backend.domain.member.dto.MemberInfoUpdateRequest;
import project.backend.domain.member.dto.SignUpRequest;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.entity.ProviderType;
import project.backend.domain.member.mapper.MemberMapper;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.errorcode.MemberErrorCode;
import project.backend.global.exception.ex.AuthException;
import project.backend.global.exception.ex.MemberException;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final ImageFileService imageFileService;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${file.images.profile.default}")
	private String defaultProfileImg;

	public MemberResponse saveMember(SignUpRequest request) {

		if (checkUsernameAlreadyExists(request.getUsername())) {
			throw new MemberException(MemberErrorCode.USERNAME_ALREADY_EXISTS);
		}

		if (request.getEmail() != null && checkEmailAlreadyExists(request.getEmail())) {
			throw new MemberException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
		}

		String encryptedPassword = passwordEncoder.encode(request.getPassword());

		Member newMember = memberRepository.save(
			MemberMapper.toEntity(request, encryptedPassword, defaultProfileImg));

		return MemberMapper.toResponse(newMember);
	}

	public MemberResponse updateMemberInfo(Authentication auth, MemberInfoUpdateRequest request,
		MultipartFile file) {

		MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
		Member targetMember = getMemberById(memberDetails.getId());

		doUpdateMemberInfo(targetMember, request, file);

		eventPublisher.publishEvent(ProfileUpdateEvent.of(targetMember));

		return MemberMapper.toResponse(targetMember);
	}

	private void doUpdateMemberInfo(Member targetMember, MemberInfoUpdateRequest request,
		MultipartFile file) {
		if (request.getNickname() != null) {
			targetMember.updateNickname(request.getNickname());
		}

		if (request.getEmail() != null) {
			targetMember.updateEmail(request.getEmail());
		}

		if (file != null) {
			String profileImage = imageFileService.saveProfileImage(file);
			targetMember.updateProfileImage(profileImage);
		}
	}

	public void updatePassword(Authentication auth, PasswordChangeRequest request) {
		MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
		Member targetMember = getMemberById(memberDetails.getId());

		if (targetMember.getProvider() != ProviderType.LOCAL) {
			throw new AuthException(AuthErrorCode.WRONG_AUTH_TYPE_LOGIN);
		}

		String currentPassword = targetMember.getPassword();

		if (!passwordEncoder.matches(request.getCurrentPassword(),
			currentPassword)) {
			throw new MemberException(MemberErrorCode.WRONG_PASSWORD);
		}

		if (passwordEncoder.matches(request.getNewPassword(), currentPassword)) {
			throw new MemberException(MemberErrorCode.SAME_AS_OLD_PASSWORD);
		}

		targetMember.updatePassword(request.getNewPassword(), passwordEncoder);
	}


	// Spring Security에서 UsernameNotFoundException을 처리하도록 유도하는 메서드
	public Member getMemberForLogin(String username) {
		try {
			return getMemberByUsername(username);
		} catch (MemberException e) {
			log.info("존재하지 않는 username으로 로그인 시도: {}", username);
			throw new UsernameNotFoundException("존재하지 않는 유저입니다: " + username, e);
		}
	}

	public Member getMemberByUsername(String username) {
		return memberRepository.findByUsername(username)
			.orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
	}

	public Member getMemberById(Long id) {
		return memberRepository.findById(id)
			.orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
	}

	public MemberResponse getMemberDetails(Authentication auth) {
		MemberDetails loginMember = (MemberDetails) auth.getPrincipal();
		Long memberId = loginMember.getId();
		log.info("memberId = {}", memberId);
		Member member = getMemberById(memberId);
		return MemberMapper.toResponse(member);
	}

	private boolean checkUsernameAlreadyExists(String username) {
		return memberRepository.findByUsername(username).isPresent();
	}

	private boolean checkEmailAlreadyExists(String email) {
		return memberRepository.findByEmail(email).isPresent();
	}

}
