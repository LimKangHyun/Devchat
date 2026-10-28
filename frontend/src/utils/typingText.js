// "○○님이 입력 중…" 문구. 입력하는 사람이 없으면 빈 문자열
export function typingText(names) {
  const list = (names || []).filter(Boolean);
  if (list.length === 0) return '';
  if (list.length === 1) return `${list[0]}님이 입력 중…`;
  if (list.length === 2) return `${list[0]}님, ${list[1]}님이 입력 중…`;
  return `${list[0]}님 외 ${list.length - 1}명이 입력 중…`;
}

// 받은 신호를 반영한 새 목록 { [memberId]: { nickname, until } }. 내 신호와 만료된 항목은 뺀다
export const TYPING_SHOW_MS = 6000;

export function applyTypingEvent(state, event, myId, now = Date.now()) {
  const next = {};
  Object.entries(state || {}).forEach(([id, v]) => {
    if (v.until > now) next[id] = v;
  });
  if (!event || event.memberId == null || String(event.memberId) === String(myId)) return next;
  if (event.typing) next[event.memberId] = { nickname: event.nickname, until: now + TYPING_SHOW_MS };
  else delete next[event.memberId];
  return next;
}

export function activeTypers(state, now = Date.now()) {
  return Object.values(state || {}).filter((v) => v.until > now).map((v) => v.nickname);
}
