package project.api.domain.push.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.api.domain.member.entity.Member;

/** 기기·브라우저 하나의 웹 푸시 구독 */
@Entity
@Table(name = "push_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 브라우저 회사 푸시 서버 주소 (Apple·Google·Mozilla) */
    @Column(nullable = false, length = 1000)
    private String endpoint;

    /** endpoint가 길어 유니크 인덱스를 해시에 건다 */
    @Column(name = "endpoint_hash", nullable = false, length = 64, unique = true)
    private String endpointHash;

    /** 브라우저 공개키·인증 비밀. 알림 내용을 이 기기만 풀 수 있게 암호화하는 데 쓴다 */
    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PushSubscription(Member member, String endpoint, String p256dh, String auth) {
        this.member = member;
        this.endpoint = endpoint;
        this.endpointHash = hash(endpoint);
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = LocalDateTime.now();
    }

    /** 같은 기기로 다른 계정이 로그인하면 구독 주인과 키를 바꾼다 */
    public void reassign(Member member, String p256dh, String auth) {
        this.member = member;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static String hash(String endpoint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
