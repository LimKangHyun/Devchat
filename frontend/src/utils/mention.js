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

/** 메시지 본문에 "@닉네임"으로 들어 있는 참여자 id. 닉네임 뒤는 끝·공백·문장부호여야 한다(밥/밥2 구분) */
export const findMentionedIds = (content, participants) => {
  if (!content || !participants?.length) return [];
  return participants
    .filter((p) => new RegExp(`(^|\\s)@${escapeRegExp(p.nickname)}(?=$|[\\s.,!?:;)\\]])`).test(content))
    .map((p) => p.memberId);
};

/** 렌더링용으로 "@단어"를 잘라낸다. [{text, mention}] */
export const splitMentions = (text) => {
  const parts = [];
  const re = /(^|\s)(@[^\s@]+)/g;
  let last = 0;
  let m;
  while ((m = re.exec(text)) !== null) {
    const at = m.index + m[1].length;
    if (at > last) parts.push({ text: text.slice(last, at), mention: false });
    parts.push({ text: m[2], mention: true });
    last = at + m[2].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), mention: false });
  return parts;
};
