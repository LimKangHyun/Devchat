package project.api.domain.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.dto.*;

@Tag(name = "Member", description = "회원 관리 API")
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "내 정보 조회")
    @GetMapping("/details")
    public MemberResponse getMemberDetails(Authentication authentication) {
        return memberService.getMemberDetails(authentication);
    }

    @Operation(summary = "회원 정보 수정")
    @PutMapping(value = "/info", consumes = "multipart/form-data")
    public MemberResponse updateMemberInfo(
        Authentication authentication,
        @RequestPart("request") @Valid MemberInfoUpdateRequest request,
        @RequestPart(value = "profileImage", required = false) MultipartFile profileImg,
        HttpServletResponse response
        ) {
        return memberService.updateMemberInfo(authentication, request, profileImg, response);
    }

    @Operation(summary = "비밀번호 변경 (Form 회원가입의 경우)")
    @PutMapping("/password")
    public void updatePassword(Authentication authentication,
        @RequestBody @Valid PasswordChangeRequest request) {
        memberService.updatePassword(authentication, request);
    }

    @Operation(summary = "유저 검색")
    @GetMapping("/search")
    public Page<MemberSearchResponse> searchUsers(
        Authentication auth,
        @RequestParam String nickname,
        Pageable pageable
    ) {
        return memberService.searchMembers(auth, nickname, pageable);
    }

}
