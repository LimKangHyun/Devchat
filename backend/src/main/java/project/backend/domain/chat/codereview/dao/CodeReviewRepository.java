package project.backend.domain.chat.codereview.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.chat.codereview.entity.CodeReview;

public interface CodeReviewRepository extends JpaRepository<CodeReview, Long> {

	List<CodeReview> findByMessageIdOrderByLineNumberAscCreatedAtAsc(Long messageId);
}
