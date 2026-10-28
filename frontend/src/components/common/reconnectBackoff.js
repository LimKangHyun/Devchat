// 웹소켓 재연결 대기 시간: 1초부터 두 배씩, 최대 30초.
// 서버 재시작·배포 때 모든 사용자가 같은 순간에 몰리지 않도록 절반~전체 사이에서 무작위로 고른다.
export const RECONNECT_BASE_MS = 1000
export const RECONNECT_MAX_MS = 30000

/** attempt: 연속으로 실패한 횟수(0부터). 연결에 성공하면 0으로 되돌린다 */
export function reconnectDelay(attempt, random = Math.random) {
  const ceiling = Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS * 2 ** Math.max(0, attempt))
  return Math.round(ceiling / 2 + random() * (ceiling / 2))
}
