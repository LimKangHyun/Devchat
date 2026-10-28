package project.api.domain.push.app;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.api.domain.push.entity.PushSubscription;

/**
 * 웹 푸시 표준(RFC 8030·8291·8292) 발송. 내용은 기기 키로 암호화하고 VAPID로 서명한다.
 * 키가 없으면(로컬 등) 꺼진 상태로 뜨고 나머지 기능에는 영향이 없다.
 */
@Slf4j
@Component
public class WebPushSender implements PushSender {

    // 알림을 하루 안에 못 받으면(기기가 꺼져 있는 등) 버린다. 오래된 채팅 알림은 의미가 없다
    private static final int TTL_SECONDS = 24 * 60 * 60;

    private final String publicKey;
    private final PushService pushService;

    public WebPushSender(@Value("${webpush.vapid.public-key:}") String publicKey,
                         @Value("${webpush.vapid.private-key:}") String privateKey,
                         @Value("${webpush.vapid.subject:mailto:admin@devchat.app}") String subject) {
        this.publicKey = publicKey;
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.warn("[웹푸시] VAPID 키가 없어 휴대폰 알림을 보내지 않습니다 (webpush.vapid.*)");
            this.pushService = null;
            return;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            this.pushService = new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            throw new IllegalStateException("웹푸시 VAPID 키가 올바르지 않습니다", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return pushService != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public int send(PushSubscription subscription, String jsonPayload) throws Exception {
        if (pushService == null) return 0;
        Notification notification = Notification.builder()
            .endpoint(subscription.getEndpoint())
            .userPublicKey(subscription.getP256dh())
            .userAuth(subscription.getAuth())
            .payload(jsonPayload.getBytes(StandardCharsets.UTF_8))
            .ttl(TTL_SECONDS)
            .urgency(Urgency.HIGH)
            .build();
        HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
        int status = response.getStatusLine().getStatusCode();
        if (status >= 400 && status != 404 && status != 410) {
            String body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
            log.warn("[웹푸시] 발송 실패 status={} body={}", status, body.length() > 200 ? body.substring(0, 200) : body);
        }
        return status;
    }
}
