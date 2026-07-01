package project.api.domain.community.dto.event;

public record ApplicantResultEvent(
    Long applicantMemberId,
    String applicantUsername,
    Long authorId,
    Long postId,
    String postTitle,
    boolean approved
) {

}