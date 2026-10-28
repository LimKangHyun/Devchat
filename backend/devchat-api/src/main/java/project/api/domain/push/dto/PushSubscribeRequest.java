package project.api.domain.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 브라우저 PushSubscription.toJSON() 그대로: { endpoint, keys: { p256dh, auth } } */
public record PushSubscribeRequest(
    @NotBlank
    @Size(max = 1000)
    @Pattern(regexp = "^https://.+", message = "푸시 주소가 올바르지 않습니다.")
    String endpoint,

    @NotNull @Valid
    Keys keys
) {
    public record Keys(
        @NotBlank @Size(max = 255) String p256dh,
        @NotBlank @Size(max = 255) String auth
    ) {
    }
}
