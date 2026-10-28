package project.api.domain.push.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.api.auth.dto.MemberDetails;
import project.api.domain.push.app.PushNotificationService;
import project.api.domain.push.app.PushSender;
import project.api.domain.push.dto.PushSubscribeRequest;

@Tag(name = "Push", description = "휴대폰 알림(웹 푸시) 구독")
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {

    private final PushNotificationService pushNotificationService;
    private final PushSender pushSender;

    @Operation(summary = "푸시 공개키", description = "브라우저가 구독할 때 쓰는 VAPID 공개키. enabled가 false면 서버에 키가 없다.")
    @GetMapping("/public-key")
    public Map<String, Object> publicKey() {
        return Map.of("enabled", pushSender.isEnabled(), "publicKey", pushSender.publicKey());
    }

    @Operation(summary = "이 기기 푸시 구독")
    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal MemberDetails memberDetails,
                                          @Valid @RequestBody PushSubscribeRequest request) {
        pushNotificationService.subscribe(memberDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "이 기기 푸시 구독 해지")
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal MemberDetails memberDetails,
                                            @RequestBody Map<String, String> body) {
        String endpoint = body.get("endpoint");
        if (endpoint != null && !endpoint.isBlank()) {
            pushNotificationService.unsubscribe(memberDetails.getId(), endpoint);
        }
        return ResponseEntity.noContent().build();
    }
}
