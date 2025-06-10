package project.backend.domain.member.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.dto.MemberResponse;
import project.backend.domain.member.dto.MemberInfoUpdateRequest;
import project.backend.domain.member.dto.PasswordChangeRequest;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/details")
	public MemberResponse getMemberDetails(Authentication authentication) {
		return memberService.getMemberDetails(authentication);
	}

	@PutMapping(value = "/info", consumes = "multipart/form-data")
	public MemberResponse updateMemberInfo(
		Authentication authentication,
		@RequestPart("request") @Valid MemberInfoUpdateRequest request,
		@RequestPart(value = "profileImg", required = false) MultipartFile profileImg
	) {
		return memberService.updateMemberInfo(authentication, request, profileImg);
	}

	@PutMapping("/password")
	public void updatePassword(Authentication authentication,
		@RequestBody @Valid PasswordChangeRequest request) {
		memberService.updatePassword(authentication, request);
	}

}
