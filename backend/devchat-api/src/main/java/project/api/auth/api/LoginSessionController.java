package project.api.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.api.auth.app.LoginSessionService;
import project.api.auth.dto.LoginSessionResponse;
import project.api.auth.dto.MemberDetails;

import java.util.List;

@Tag(name = "Auth", description = "로그인 기기 API")
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class LoginSessionController {

    private final LoginSessionService loginSessionService;

    @Operation(summary = "로그인 기기 목록")
    @GetMapping
    public List<LoginSessionResponse> list(@AuthenticationPrincipal MemberDetails me) {
        return loginSessionService.list(me.getId(), me.getSessionId());
    }

    @Operation(summary = "다른 기기 로그아웃")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal MemberDetails me, @PathVariable String id) {
        loginSessionService.revoke(me.getId(), id, me.getSessionId());
    }

    @Operation(summary = "다른 모든 기기 로그아웃")
    @PostMapping("/logout-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutOthers(@AuthenticationPrincipal MemberDetails me) {
        loginSessionService.revokeOthers(me.getId(), me.getSessionId());
    }
}
