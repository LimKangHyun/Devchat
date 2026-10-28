package project.api.domain.push.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import project.api.domain.notification.app.SeenRoomTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.push.dao.PushSubscriptionRepository;
import project.api.domain.push.dto.PushSubscribeRequest;
import project.api.domain.push.entity.PushSubscription;

/** 기기별 푸시 구독을 관리하고, 한 회원의 모든 기기로 알림을 보낸다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSender pushSender;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final SeenRoomTracker seenRoomTracker;

    private static final int MAX_SEEN_IN_PAYLOAD = 30;

    @Transactional
    public void subscribe(Long memberId, PushSubscribeRequest request) {
        Member member = memberRepository.getReferenceById(memberId);
        String hash = PushSubscription.hash(request.endpoint());
        subscriptionRepository.findByEndpointHash(hash).ifPresentOrElse(
            existing -> existing.reassign(member, request.keys().p256dh(), request.keys().auth()),
            () -> subscriptionRepository.save(new PushSubscription(
                member, request.endpoint(), request.keys().p256dh(), request.keys().auth()))
        );
    }

    /** 이 기기의 구독을 지운다 (로그아웃·알림 끄기). 남의 구독은 지우지 않는다 */
    @Transactional
    public void unsubscribe(Long memberId, String endpoint) {
        subscriptionRepository.deleteByEndpointHashAndMember_Id(PushSubscription.hash(endpoint), memberId);
    }

    /** 회원의 모든 기기로 보낸다. 브라우저가 구독을 해지한 기기(404·410)는 지운다 */
    public void sendToMember(Long memberId, PushMessage message) {
        if (!pushSender.isEnabled()) return;
        List<PushSubscription> subscriptions = subscriptionRepository.findByMember_Id(memberId);
        if (subscriptions.isEmpty()) return;

        String json;
        try {
            // 다른 기기에서 이미 본 방의 알림을 이 기기 알림 센터에서 지우도록 함께 보낸다
            // (웹 푸시 내용은 약 4KB까지라 최근 것만)
            List<SeenRoomTracker.SeenRoom> seen = seenRoomTracker.recentlySeen(memberId).stream()
                .sorted(java.util.Comparator.comparingLong(SeenRoomTracker.SeenRoom::at).reversed())
                .limit(MAX_SEEN_IN_PAYLOAD)
                .toList();
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("title", message.title());
            payload.put("body", message.body());
            payload.put("url", message.url());
            payload.put("tag", message.tag());
            payload.put("seen", seen);
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[웹푸시] 알림 내용 변환 실패: {}", e.getMessage());
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            try {
                int status = pushSender.send(subscription, json);
                if (status == 404 || status == 410) {
                    subscriptionRepository.delete(subscription);
                }
            } catch (Exception e) {
                log.warn("[웹푸시] 발송 중 오류 member={}: {}", memberId, e.getMessage());
            }
        }
    }
}
