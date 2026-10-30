package project.api.domain.notification.entity;

public enum NotificationType {
    FRIEND_REQUESTED,
    FRIEND_ACCEPTED,
    FRIEND_REJECTED,
    WE_ARE_FRIEND_NOW,
    NEW_DM,
    CODE_REVIEW,
    STUDY_APPLY,
    STUDY_APPROVED,
    STUDY_REJECTED,
    MENTION,
    SCHEDULE_ADDED,
    SCHEDULE_CHANGED,
    SCHEDULE_CANCELED,
    SCHEDULE_REMINDER,
    // 보낸 피드백(GitHub 이슈)에 관리자가 댓글을 달았다 / 이슈가 닫혔다. referenceId는 이슈 번호
    FEEDBACK_REPLIED,
    FEEDBACK_RESOLVED
}
