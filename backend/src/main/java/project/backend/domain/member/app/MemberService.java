package project.backend.domain.member.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileService;
import project.backend.domain.imagefile.ImageType;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.dto.event.ProfileUpdateEvent;
import project.backend.global.security.dto.MemberDetails;
import project.backend.domain.member.dto.MemberResponse;
import project.backend.domain.member.dto.MemberUpdateRequest;
import project.backend.domain.member.dto.SignUpRequest;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.mapper.MemberMapper;
import project.backend.global.exception.errorcode.MemberErrorCode;
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
	private String defaultProfile;

	public MemberResponse saveMember(SignUpRequest request) {

		if (checkIfMemberExists(request.getEmail())) {
			log.info("예외 = {}", "이미 존재하는 email");
			throw new MemberException(MemberErrorCode.MEMBER_ALREADY_EXISTS);
		}

		ImageFile defaultProfileImg = imageFileService.getProfileImageByStoreFileName(
			defaultProfile);

		String encryptedPassword = passwordEncoder.encode(request.getPassword());

		Member newMember = memberRepository.save(
			MemberMapper.toEntity(request, encryptedPassword, defaultProfileImg));

		return MemberMapper.toResponse(newMember);
	}


	public MemberResponse updateMember(Authentication auth, MemberUpdateRequest request) {
		MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
		Member targetMember = getMemberById(memberDetails.getId());

		boolean isUpdated = updateMemberFields(targetMember, request);

		if (isUpdated) {
			eventPublisher.publishEvent(
				ProfileUpdateEvent.of(targetMember)
			);
		}

		return MemberMapper.toResponse(targetMember);
	}

	// 로직 변경 필요 (setter -> update 메서드로 엔티티에 책임 위임)
	private boolean updateMemberFields(Member targetMember, MemberUpdateRequest request) {
		boolean isUpdated = false;

		if (request.getNickname() != null) {
			targetMember.setNickname(request.getNickname());
			isUpdated = true;
		}

		if (request.getPassword() != null) {
			targetMember.setPassword(passwordEncoder.encode(request.getPassword()));
		}

		if (request.getProfileImg() != null) {
			ImageFile newProfile = imageFileService.saveImageFile(request.getProfileImg(),
				ImageType.PROFILE_IMAGE);
			targetMember.setProfileImage(newProfile);
			isUpdated = true;
		}

		return isUpdated;
	}

	// Spring Security에서 UsernameNotFoundException을 처리하도록 유도하는 메서드
	public Member loginByEmail(String email) {
		try {
			return getMemberByEmail(email);
		} catch (MemberException e) {
			log.info("존재하지 않는 이메일로 로그인 시도: {}", email);
			throw new UsernameNotFoundException("존재하지 않는 유저입니다: " + email, e);
		}
	}

	public Member getMemberByEmail(String email) {
		return memberRepository.findByEmail(email)
			.orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
	}

	public Member getMemberById(Long id) {
		return memberRepository.findById(id)
			.orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
	}

	public MemberResponse getMemberResponseById(Long memberId) {
		Member member = getMemberById(memberId);
		return MemberMapper.toResponse(member);
	}

	public boolean checkIfMemberExists(String email) {
		return memberRepository.findByEmail(email).isPresent();
	}

	public MemberResponse getMemberDetails(Authentication auth) {
		MemberDetails loginMember = (MemberDetails) auth.getPrincipal();
		Long memberId = loginMember.getId();
		log.info("memberId = {}", memberId);
		Member member = getMemberById(memberId);
		return MemberMapper.toResponse(member);
	}

}
