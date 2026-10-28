// 채팅 메시지 최대 길이. 서버(ChatMessageService.MAX_CONTENT_LENGTH)와 같은 값이어야 한다.
// 이보다 긴 메시지는 DB 컬럼과 웹소켓 메시지 한계를 넘어 저장되지 않거나 연결이 끊긴다.
export const MAX_MESSAGE_LENGTH = 10000

// 이 길이부터 입력창에 글자 수를 보여준다
export const SHOW_COUNT_FROM = 9000

export const TOO_LONG_MESSAGE = `메시지는 ${MAX_MESSAGE_LENGTH.toLocaleString()}자까지 보낼 수 있어요.`

export const isTooLong = (text) => (text?.length ?? 0) > MAX_MESSAGE_LENGTH
