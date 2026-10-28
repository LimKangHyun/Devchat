package project.api.domain.chat.chatroom.event;

/** 회원이 방을 만들었다 (그 회원의 다른 창·기기에 방 목록을 갱신하라고 알리기 위함) */
public record RoomCreatedByMemberEvent(Long roomId, Long memberId) {
}
