package project.backend.domain.github.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.github.dao.AiCommentModRepository;
import project.backend.domain.github.dao.AiReviewCommentRepository;
import project.backend.domain.github.entity.AiCommentMod;
import project.backend.domain.github.entity.AiReview;
import project.backend.domain.github.entity.AiReviewComment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiReviewCommentSaveService {

    private final AiReviewCommentRepository aiReviewCommentRepository;
    private final AiCommentModRepository aiCommentModRepository;

    @Transactional
    public void saveComments(AiReview aiReview, List<Map<String, Object>> fileResults) {
        aiReviewCommentRepository.deleteByAiReview_Id(aiReview.getId());
        for (Map<String, Object> file : fileResults) {
            String filePath = (String) file.get("filePath");
            List<Map<String, Object>> reviews = (List<Map<String, Object>>) file.get("reviews");
            if (reviews == null || reviews.isEmpty()) continue;

            for (Map<String, Object> review : reviews) {
                int lineNumber = ((Number) review.get("lineNumber")).intValue();
                String comment = (String) review.get("comment");

                AiReviewComment aiReviewComment = AiReviewComment.builder()
                        .aiReview(aiReview)
                        .filePath(filePath)
                        .lineNumber(lineNumber)
                        .comment(comment)
                        .createdAt(LocalDateTime.now())
                        .build();
                aiReviewCommentRepository.save(aiReviewComment);

                AiCommentMod initialStatus = AiCommentMod.builder()
                        .comment(aiReviewComment)
                        .active(true)
                        .reason(null)
                        .otherReason(null)
                        .changedBy("SYSTEM")
                        .createdAt(LocalDateTime.now())
                        .build();
                aiCommentModRepository.save(initialStatus);
            }
        }
    }
}