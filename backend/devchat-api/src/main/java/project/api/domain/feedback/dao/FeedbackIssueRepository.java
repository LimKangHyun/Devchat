package project.api.domain.feedback.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import project.api.domain.feedback.entity.FeedbackIssue;

public interface FeedbackIssueRepository extends JpaRepository<FeedbackIssue, Integer> {

    @EntityGraph(attributePaths = "member")
    Optional<FeedbackIssue> findWithMemberByIssueNumber(Integer issueNumber);
}
