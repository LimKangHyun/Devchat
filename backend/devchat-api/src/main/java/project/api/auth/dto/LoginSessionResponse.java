package project.api.auth.dto;

import project.api.auth.entity.LoginSession;

import java.time.LocalDateTime;

public record LoginSessionResponse(String id, String deviceName, LocalDateTime createdAt,
                                   LocalDateTime lastUsedAt, boolean current) {

    public static LoginSessionResponse of(LoginSession s, boolean current) {
        return new LoginSessionResponse(s.getId(), s.getDeviceName(), s.getCreatedAt(),
                s.getLastUsedAt(), current);
    }
}
