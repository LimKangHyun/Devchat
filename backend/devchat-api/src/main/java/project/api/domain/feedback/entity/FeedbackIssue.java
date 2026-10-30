package project.api.domain.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.api.domain.member.entity.Member;

/** 피드백으로 만든 GitHub 이슈와 보낸 사람. 공개 이슈에는 보낸 사람을 적지 않아서 여기에만 둔다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackIssue {

    public static final int TITLE_MAX = 200;

    @Id
    private Integer issueNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public FeedbackIssue(int issueNumber, Member member, String title) {
        this.issueNumber = issueNumber;
        this.member = member;
        this.title = title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title;
        this.createdAt = LocalDateTime.now();
    }
}
