package project.api.auth.app;

import java.util.List;

/** 세션이 끊겼다는 알림. 커밋 뒤 리스너가 웹소켓으로 해당 기기에 알린다 */
public record SessionRevokedEvent(String username, List<String> sessionIds) {
}
