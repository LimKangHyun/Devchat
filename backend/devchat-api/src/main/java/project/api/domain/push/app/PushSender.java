package project.api.domain.push.app;

import project.api.domain.push.entity.PushSubscription;

/** 브라우저 회사 푸시 서버로 암호화된 알림을 보낸다 */
public interface PushSender {

    /** 푸시 키가 설정돼 있어 보낼 수 있는지 */
    boolean isEnabled();

    /** 브라우저가 구독할 때 쓰는 공개키 (VAPID, base64url) */
    String publicKey();

    /** @return 푸시 서버의 HTTP 상태 코드 (404·410이면 구독이 만료된 것) */
    int send(PushSubscription subscription, String jsonPayload) throws Exception;
}
