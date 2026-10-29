import { kstParts, kstDate } from './calendarGrid';

const WEEKDAY = { 일: 0, 월: 1, 화: 2, 수: 3, 목: 4, 금: 5, 토: 6 };
const DAY_MS = 24 * 60 * 60 * 1000;

const daysInMonth = (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate();
const isDigit = (c) => c !== undefined && c >= '0' && c <= '9';
const isHangul = (c) => c !== undefined && /[가-힣]/.test(c);

const validYmd = (y, m, d) => m >= 1 && m <= 12 && d >= 1 && d <= daysInMonth(y, m);

// 연도 없는 날짜: base보다 30일 넘게 과거면 다음 해
const resolveNoYear = (m, d, base) => {
  const b = kstParts(base);
  if (!validYmd(b.y, m, d)) {
    // 2/29처럼 올해 없는 날짜는 다음 해 시도
    return validYmd(b.y + 1, m, d) ? { y: b.y + 1, m, d } : null;
  }
  const cand = kstDate(b.y, m, d).getTime();
  const today = kstDate(b.y, b.m, b.d).getTime();
  const y = today - cand > 30 * DAY_MS ? b.y + 1 : b.y;
  return validYmd(y, m, d) ? { y, m, d } : null;
};

// 날짜 패턴. 위에 있을수록 우선
const DATE_RULES = [
  {
    re: /(\d{4})([-./])(\d{1,2})\2(\d{1,2})(?!\d)/g,
    guard: (text, m) => !isDigit(text[m.index - 1]),
    resolve: (m) => {
      const [y, mo, d] = [+m[1], +m[3], +m[4]];
      if (y < 1900 || y > 2100) return null;
      return validYmd(y, mo, d) ? { y, m: mo, d } : null;
    },
  },
  {
    re: /(\d{1,2})월\s?(\d{1,2})일/g,
    resolve: (m, base) => resolveNoYear(+m[1], +m[2], base),
  },
  {
    re: /(\d{1,2})\/(\d{1,2})(?![\d/])(?!\.\d)/g,
    // 앞이 숫자나 /, 또는 "숫자."이면 버전 번호 같은 것
    guard: (text, m) => {
      const prev = text[m.index - 1];
      if (isDigit(prev) || prev === '/') return false;
      return !(prev === '.' && isDigit(text[m.index - 2]));
    },
    resolve: (m, base) => resolveNoYear(+m[1], +m[2], base),
  },
  {
    re: /오늘|내일|모레|글피/g,
    resolve: (m, base) => {
      const add = { 오늘: 0, 내일: 1, 모레: 2, 글피: 3 }[m[0]];
      const b = kstParts(base);
      return kstParts(kstDate(b.y, b.m, b.d + add));
    },
  },
  {
    re: /(다다음|다음|이번)\s?주\s?([월화수목금토일])(?:요일|욜)/g,
    resolve: (m, base) => {
      const weeks = { 이번: 0, 다음: 1, 다다음: 2 }[m[1]];
      const b = kstParts(base);
      const mondayOffset = (b.dow + 6) % 7; // 월요일 시작
      const target = (WEEKDAY[m[2]] + 6) % 7;
      return kstParts(kstDate(b.y, b.m, b.d - mondayOffset + weeks * 7 + target));
    },
  },
  {
    re: /([월화수목금토일])(?:요일|욜)/g,
    // 한글 바로 뒤거나 "지난/저번 (주)" 뒤면 지나간 요일
    guard: (text, m) =>
      !isHangul(text[m.index - 1]) &&
      !/(지난|저번)\s?주?\s?$/.test(text.slice(Math.max(0, m.index - 8), m.index)),
    resolve: (m, base) => {
      const b = kstParts(base);
      const diff = (WEEKDAY[m[1]] - b.dow + 7) % 7;
      return kstParts(kstDate(b.y, b.m, b.d + diff));
    },
  },
];

// 날짜 바로 뒤(공백 0~1칸)의 시간
const TIME_RE = /\s?(?:(오전|오후)\s?)?(?:(\d{1,2}):(\d{2})(?!\d)|(\d{1,2})시(?!간)(?:\s?(반)(?![드대면응성납복값])|\s?(\d{1,2})분)?)/y;

const readTime = (text, from) => {
  TIME_RE.lastIndex = from;
  const t = TIME_RE.exec(text);
  if (!t) return null;
  const ampm = t[1];
  let h = t[2] !== undefined ? +t[2] : +t[4];
  let min = 0;
  if (t[3] !== undefined) min = +t[3];
  else if (t[5]) min = 30;
  else if (t[6] !== undefined) min = +t[6];
  if (min > 59) return null;
  if (ampm === '오전') {
    if (h < 1 || h > 12) return null;
    h %= 12;
  } else if (ampm === '오후') {
    if (h < 1 || h > 23) return null;
    if (h < 12) h += 12;
  } else {
    if (h > 23) return null;
    if (t[4] !== undefined && h >= 1 && h <= 6) h += 12; // 회의 관용 (H시만)
  }
  return { h, min, length: t[0].length };
};

export const findDates = (text, base) => {
  const found = [];
  DATE_RULES.forEach((rule, priority) => {
    rule.re.lastIndex = 0;
    let m;
    while ((m = rule.re.exec(text)) !== null) {
      if (rule.guard && !rule.guard(text, m)) continue;
      const ymd = rule.resolve(m, base);
      if (!ymd) continue;
      let length = m[0].length;
      const time = readTime(text, m.index + length);
      let start;
      let end;
      if (time) {
        length += time.length;
        start = kstDate(ymd.y, ymd.m, ymd.d, time.h, time.min);
        end = new Date(start.getTime() + 60 * 60 * 1000);
      } else {
        start = kstDate(ymd.y, ymd.m, ymd.d);
        end = new Date(start.getTime());
      }
      found.push({ index: m.index, length, start, end, allDay: !time, priority });
    }
  });

  // 겹치면 긴 것 하나만 (같으면 우선순위가 높은 규칙)
  found.sort((a, b) => b.length - a.length || a.priority - b.priority || a.index - b.index);
  const kept = [];
  found.forEach((c) => {
    const overlap = kept.some((k) => c.index < k.index + k.length && k.index < c.index + c.length);
    if (!overlap) kept.push(c);
  });
  return kept
    .sort((a, b) => a.index - b.index)
    .map(({ index, length, start, end, allDay }) => ({ index, length, start, end, allDay }));
};
