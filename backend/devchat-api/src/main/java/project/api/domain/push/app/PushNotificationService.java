package project.api.domain.push.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
            json = objectMapper.writeValueAsString(message);
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
