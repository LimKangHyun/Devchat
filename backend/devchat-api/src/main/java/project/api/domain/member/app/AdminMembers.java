package project.api.domain.member.app;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서비스 관리자. 서버 .env의 ADMIN_MEMBER_IDS(쉼표로 구분한 회원 번호)로 정한다.
 * 지금은 앱 메뉴에 Grafana 바로가기를 보여 줄지만 정한다. Grafana는 따로 로그인해야 들어갈 수 있다.
 */
@Slf4j
@Component
public class AdminMembers {

    private final Set<Long> ids;

    public AdminMembers(@Value("${ADMIN_MEMBER_IDS:}") String raw) {
        this.ids = parse(raw);
    }

    public boolean isAdmin(Long memberId) {
        return memberId != null && ids.contains(memberId);
    }

    private static Set<Long> parse(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .filter(s -> {
                    boolean number = s.chars().allMatch(Character::isDigit);
                    if (!number) log.warn("ADMIN_MEMBER_IDS에 숫자가 아닌 값이 있어 건너뜀: {}", s);
                    return number;
                })
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
