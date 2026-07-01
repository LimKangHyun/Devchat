package project.api.domain.community.dto.event;

public record ApplyEvent(
    Long authorId,
    String authorUsername,
    Long applicantId,
    String applicantNickname,
    Long postId,
    String postTitle
) {

}