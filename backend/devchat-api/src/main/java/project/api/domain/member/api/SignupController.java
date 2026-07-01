package project.api.domain.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.dto.MemberResponse;
import project.api.domain.member.dto.SignUpRequest;

@Tag(name = "Auth", description = "회원가입 API")
@Slf4j
@RestController
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignupController {

	private final MemberService memberService;

	@Operation(summary = "회원가입")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponse signup(@RequestBody @Valid SignUpRequest request) {
		log.info("request = {}", request);
		return memberService.saveMember(request);
	}
}
