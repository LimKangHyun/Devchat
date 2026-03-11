package project.backend.domain.community.entity;

import jakarta.persistence.*;
import lombok.*;
import project.backend.domain.member.entity.Member;

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
    }

    public String getMemberNickname() {
        return member.getNickname();
    }

    public String getProfileImage() {
        return member.getProfileImage();
    }
}