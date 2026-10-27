package project.common.message.index;

/**
 * @param cancelled 사용자가 취소했거나 채팅방이 삭제되어 멈춘 경우. 이때 저장한 청크는 모두 지워진 상태다.
 *                  이 필드가 없던 이전 메시지는 false로 읽힌다.
 */
public record RepoIndexResultMessage(Long roomId, boolean success, String errorMessage, boolean cancelled) {}
