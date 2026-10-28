const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

// 서버가 주는 시각은 오프셋 없는 KST. 오프셋이 없으면 +09:00으로 읽는다
export const parseServerTime = (value) => {
  if (value instanceof Date) return value;
  const text = String(value);
  return new Date(/(Z|[+-]\d{2}:?\d{2})$/.test(text) ? text : `${text}+09:00`);
};

// 날짜를 한국 시간 기준으로 나눈다
const kst = (date) => {
  const d = new Date(date.getTime() + KST_OFFSET_MS);
  return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), day: Math.floor(d.getTime() / DAY_MS) };
};

// 방금 전 / N분 전 / N시간 전 / 어제 / M월 D일 / YYYY년 M월 D일
export const relativeTime = (date, now = new Date()) => {
  const target = parseServerTime(date);
  const diff = now.getTime() - target.getTime();
  if (diff < 60 * 1000) return '방금 전';
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)}분 전`;

  const t = kst(target);
  const n = kst(now);
  if (t.day === n.day) return `${Math.floor(diff / 3600000)}시간 전`;
  if (t.day === n.day - 1) return '어제';
  if (t.y === n.y) return `${t.m}월 ${t.d}일`;
  return `${t.y}년 ${t.m}월 ${t.d}일`;
};
