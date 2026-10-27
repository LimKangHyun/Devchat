package project.api.domain.chat.chatmessage.event;

import java.util.List;

/**
 * 채팅 메시지에서 참여자를 @멘션했다. 메시지 커밋 뒤 멘션 알림을 만든다.
 *
 * @param mentionedMemberIds 클라이언트가 보낸 멘션 대상. 실제 참여자인지는 알림을 만들 때 다시 확인한다
 * @param preview            알림에 붙일 메시지 미리보기
 */
public record ChatMentionEvent(Long roomId, Long senderId, List<Long> mentionedMemberIds, String preview) {
}
