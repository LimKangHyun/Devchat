package project.api.global.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import project.api.global.security.internal.InternalJwtValidator;

import java.io.IOException;
import java.util.List;

/**
 * 서비스 간 내부 호출(/internal/**)을 인증한다.
 *
 * 기존에는 경로를 permitAll로 열어두고 컨트롤러에서 직접 JWT를 검증했는데,
 * 내부 API가 늘어날 때마다 검증 코드를 반복해야 하고 하나라도 빠뜨리면
 * 그대로 무인증 노출이 된다. 필터 체인에서 경로 단위로 처리하면
 * 새 엔드포인트가 추가돼도 자동으로 보호된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String INTERNAL_ROLE = "ROLE_INTERNAL_SERVICE";

    private final InternalJwtValidator internalJwtValidator;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 내부 경로가 아니면 이 필터는 관여하지 않는다.
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length());

            if (internalJwtValidator.validate(token)) {
                // 인증 성공 시 권한을 부여한다. 실패해도 여기서 응답을 내리지 않고
                // 인증 없이 통과시키면, authorizeHttpRequests의 hasRole 검사에서
                // 403으로 걸러진다 — 인증 실패 응답을 한 곳(EntryPoint)에서 일관되게 처리하기 위함.
                var authentication = new UsernamePasswordAuthenticationToken(
                        "INTERNAL_SERVICE", null,
                        List.of(new SimpleGrantedAuthority(INTERNAL_ROLE)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.warn("내부 JWT 검증 실패 - uri={}", request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}