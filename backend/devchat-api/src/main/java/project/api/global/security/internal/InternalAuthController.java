package project.api.global.security.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import project.api.auth.app.AuthTokenService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalAuthController {

    private final InternalJwtValidator internalJwtValidator;
    private final AuthTokenService authTokenService;

    @GetMapping("/auth/github-token/{memberId}")
    public String getGithubToken(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long memberId) {

        String token = authHeader.replace("Bearer ", "");
        if (!internalJwtValidator.validate(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return authTokenService.getGithubAccessToken(memberId);
    }
}