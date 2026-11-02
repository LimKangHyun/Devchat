// 채팅 @멘션 처리

// 커서 바로 앞이 "@검색어"인지 (앞은 줄 시작이나 공백이어야 이메일 등과 구분된다)
const QUERY_BEFORE_CARET = /(^|\s)@([^\s@]*)$/;

const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** 커서 위치의 멘션 검색 상태. 없으면 null */
export const getMentionQuery = (text, caret) => {
  const m = QUERY_BEFORE_CARET.exec(text.slice(0, caret));
  if (!m) return null;
  return { start: caret - m[2].length - 1, query: m[2] };
};

// "@닉네임" 규칙은 한 곳에만 둔다: 앞은 줄 시작·공백, 뒤는 끝·공백·문장부호(밥/밥2 구분).
// 알림 대상(findMentionedIds)과 파란 강조(splitMentions)가 같은 규칙을 쓴다
const MENTION_TAIL = '(?=$|[\\s.,!?:;)\\]])';

/** 메시지 본문에 "@닉네임"으로 들어 있는 참여자 id */
export const findMentionedIds = (content, participants) => {
  if (!content || !participants?.length) return [];
  return participants
    .filter((p) => p.nickname && new RegExp(`(^|\\s)@${escapeRegExp(p.nickname)}${MENTION_TAIL}`).test(content))
    .map((p) => p.memberId);
};

// 참여자 목록(배열 참조)별로 컴파일한 정규식을 캐시한다
const matcherCache = new WeakMap();
const getMatcher = (participants) => {
  if (matcherCache.has(participants)) return matcherCache.get(participants);
  const names = [...new Set(participants.map((p) => p?.nickname).filter(Boolean))]
    .sort((a, b) => b.length - a.length) // 긴 닉네임 먼저 (밥2가 밥보다 우선)
    .map(escapeRegExp);
  const re = names.length ? new RegExp(`(^|\\s)(@(?:${names.join('|')}))${MENTION_TAIL}`, 'g') : null;
  matcherCache.set(participants, re);
  return re;
};

/** 렌더링용으로 글을 나눈다. 참여자 닉네임과 맞는 "@닉네임"만 mention. [{text, mention}] */
export const splitMentions = (text, participants) => {
  const re = participants?.length ? getMatcher(participants) : null;
  if (!re) return text ? [{ text, mention: false }] : [];
  const parts = [];
  let last = 0;
  let m;
  re.lastIndex = 0;
  while ((m = re.exec(text)) !== null) {
    const at = m.index + m[1].length;
    if (at > last) parts.push({ text: text.slice(last, at), mention: false });
    parts.push({ text: m[2], mention: true });
    last = at + m[2].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), mention: false });
  return parts;
};
