package project.backend.domain.chat.codereview.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.chat.codereview.entity.CodeReview;

public interface CodeReviewRepository extends JpaRepository<CodeReview, Long> {

}
