package project.api.global.security.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import project.api.auth.app.AuthTokenService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalAuthController {

    private final AuthTokenService authTokenService;

    @GetMapping("/auth/github-token")
    public String getGithubToken(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return authTokenService.getGithubAccessToken(memberId);
    }
}