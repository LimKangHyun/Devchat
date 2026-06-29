package project.backend.domain.community.entity;

import jakarta.persistence.*;
import lombok.*;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.PostErrorCode;
import project.backend.global.exception.ex.PostException;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Applicant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    private ApplicantStatus status = ApplicantStatus.PENDING;

    private LocalDateTime appliedAt = LocalDateTime.now();

    private LocalDateTime rejectedAt;

    public static Applicant of(Post post, Member member) {
        Applicant applicant = new Applicant();
        applicant.post = post;
        applicant.member = member;
        return applicant;
    }

    public void approve() {
        this.status = ApplicantStatus.APPROVED;
    }

    public void reject() {
        this.status = ApplicantStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
    }

    public String getMemberNickname() {
        return member.getNickname();
    }

    public String getProfileImage() {
        return member.getProfileImage();
    }

    public void validateReapply() {
        if (this.status != ApplicantStatus.REJECTED) {
            throw new PostException(PostErrorCode.ALREADY_APPLIED);
        }
        if (this.rejectedAt.isAfter(LocalDateTime.now().minusHours(24))) {
            throw new PostException(PostErrorCode.APPLY_TOO_SOON);
        }
    }
}