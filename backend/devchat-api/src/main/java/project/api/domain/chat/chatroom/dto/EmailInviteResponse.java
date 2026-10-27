package project.api.domain.chat.chatroom.dto;

/** @param requested 발송을 요청한 주소 수(중복 제거 후). 실제 발송은 비동기로 진행된다. */
public record EmailInviteResponse(int requested) {
}
