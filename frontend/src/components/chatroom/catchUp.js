// 다시 연결됐을 때 놓친 메시지를 채운다.
//  1) 화면의 마지막 메시지 id 이후를 100개씩, 더 없을 때까지 받는다 (최대 5번 = 500개)
//  2) 그보다 많이 놓쳤으면 다 받지 않고 최신 페이지로 새로 그린다 (complete: false)
//  3) 끊긴 사이 수정·삭제는 id가 새로 생기지 않으므로, 최신 페이지를 받아 이미 있는 메시지를 덮어쓴다
export const CATCH_UP_PAGE = 100
export const CATCH_UP_MAX_PAGES = 5

export const maxMessageId = (messages) =>
  messages.reduce((max, m) => (m.messageId != null && (max == null || m.messageId > max) ? m.messageId : max), null)

/** fetchAfter(after) → { messages, hasMore } (오래된 순서) */
export async function fetchMissedMessages(fetchAfter, afterId) {
  const collected = []
  let after = afterId
  for (let page = 0; page < CATCH_UP_MAX_PAGES; page++) {
    const { messages = [], hasMore } = await fetchAfter(after)
    collected.push(...messages)
    if (!hasMore || messages.length === 0) return { messages: collected, complete: true }
    after = messages[messages.length - 1].messageId
  }
  return { messages: collected, complete: false }
}

const withValidDate = (m) => ({
  ...m,
  createdAt: m.createdAt && !isNaN(new Date(m.createdAt).getTime()) ? m.createdAt : new Date().toISOString(),
})

/** 화면 목록에 놓친 메시지를 더하고, 최신 페이지로 이미 있는 메시지(수정·삭제)를 갱신한다 */
export function mergeMessages(prev, missed, latest) {
  const byId = new Map(prev.map((m) => [m.messageId, m]))
  missed.forEach((m) => byId.set(m.messageId, withValidDate(m)))
  latest.forEach((m) => byId.set(m.messageId, withValidDate(m)))
  return [...byId.values()].sort((a, b) => a.messageId - b.messageId)
}
