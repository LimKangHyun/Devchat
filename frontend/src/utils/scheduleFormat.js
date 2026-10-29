import { kstParts, kstDate, dayKey } from './calendarGrid';
import { parseServerTime } from './relativeTime';

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
// 범위가 없을 때 한 일정이 차지할 수 있는 최대 날 수
const MAX_WALK_DAYS = 400;
const pad = (n) => String(n).padStart(2, '0');

export const DOW_LABELS = ['일', '월', '화', '수', '목', '금', '토'];
// 서버 DayOfWeek 이름. 인덱스는 0=일
export const DOW_ENUM = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

export const toDate = (value) => (value instanceof Date ? value : parseServerTime(value));

// 한국 시간 시·분
const kstClock = (date) => {
  const d = new Date(date.getTime() + KST_OFFSET_MS);
  return { h: d.getUTCHours(), mi: d.getUTCMinutes() };
};

// 'HH:mm' (KST)
export const toTimeInput = (date) => {
  const t = kstClock(date);
  return `${pad(t.h)}:${pad(t.mi)}`;
};

// 서버로 보낼 오프셋 없는 KST 'YYYY-MM-DDTHH:mm:00'
export const toLocalIso = (date) => `${dayKey(date)}T${toTimeInput(date)}:00`;

// 'YYYY-MM-DD' 를 KST 0시 Date로
export const parseDayKey = (key) => {
  const [y, m, d] = key.split('-').map(Number);
  return kstDate(y, m, d);
};

// 오전 9:00 / 오후 3:30
export const timeLabel = (value) => {
  const { h, mi } = kstClock(toDate(value));
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h < 12 ? '오전' : '오후'} ${h12}:${pad(mi)}`;
};

// 10월 29일 (목)
export const dateLabel = (value) => {
  const t = kstParts(toDate(value));
  return `${t.m}월 ${t.d}일 (${DOW_LABELS[t.dow]})`;
};

// 일정 시간 문구. 하루 종일이면 end는 마지막 날 0시
export const rangeLabel = ({ start, end, allDay }) => {
  const s = toDate(start);
  const e = toDate(end);
  const sameDay = dayKey(s) === dayKey(e);
  if (allDay) return sameDay ? `${dateLabel(s)} 하루 종일` : `${dateLabel(s)} – ${dateLabel(e)}`;
  if (sameDay) return `${dateLabel(s)} ${timeLabel(s)} – ${timeLabel(e)}`;
  return `${dateLabel(s)} ${timeLabel(s)} – ${dateLabel(e)} ${timeLabel(e)}`;
};

// 반복 문구. 예) "매주 월·수", "매달 31일 · 10번"
export const repeatLabel = ({ repeatFrequency, repeatDays, repeatUntil, repeatCount, startAt }) => {
  if (!repeatFrequency || repeatFrequency === 'NONE') return '';
  const start = startAt ? kstParts(toDate(startAt)) : null;
  let text;
  switch (repeatFrequency) {
    case 'DAILY': text = '매일'; break;
    case 'WEEKLY': {
      // 월요일부터 읽는다
      const order = [1, 2, 3, 4, 5, 6, 0];
      const picked = order.filter((i) => (repeatDays || []).includes(DOW_ENUM[i]));
      text = picked.length ? `매주 ${picked.map((i) => DOW_LABELS[i]).join('·')}` : '매주';
      break;
    }
    case 'MONTHLY': text = start ? `매달 ${start.d}일` : '매달'; break;
    case 'YEARLY': text = start ? `매년 ${start.m}월 ${start.d}일` : '매년'; break;
    default: return '';
  }
  if (repeatUntil) {
    const [y, m, d] = String(repeatUntil).split('-').map(Number);
    const sameYear = start && start.y === y;
    text += sameYear ? ` · ${m}월 ${d}일까지` : ` · ${y}년 ${m}월 ${d}일까지`;
  } else if (repeatCount) {
    text += ` · ${repeatCount}번`;
  }
  return text;
};

// 시작 시각 순, 같은 날이면 하루 종일 먼저
export const compareOccurrence = (a, b) => {
  if (a.allDay !== b.allDay && dayKey(toDate(a.start)) === dayKey(toDate(b.start))) return a.allDay ? -1 : 1;
  return toDate(a.start) - toDate(b.start) || String(a.title).localeCompare(String(b.title));
};

// 회차를 날짜 칸('YYYY-MM-DD')별로 나눈다. 여러 날에 걸치면 모든 날에 넣는다
export const occurrencesByDay = (list, range) => {
  const map = new Map();
  [...(list || [])].sort(compareOccurrence).forEach((o) => {
    // range: { from, to } (to는 그날을 포함하지 않는다). 없으면 일정 전체 기간
    const s = toDate(o.start);
    let e = toDate(o.end);
    // 시간 일정이 0시에 끝나면 그 날은 넣지 않는다
    if (!o.allDay && e > s && dayKey(e) !== dayKey(s) && toTimeInput(e) === '00:00') {
      e = new Date(e.getTime() - 1);
    }
    // 보이는 범위 안의 날만 돈다 (범위보다 훨씬 전에 시작한 긴 일정도 빠지지 않게)
    let first = dayKey(s);
    let last = dayKey(e);
    if (range?.from && dayKey(range.from) > first) first = dayKey(range.from);
    if (range?.to) {
      const end = dayKey(new Date(range.to.getTime() - 1));
      if (end < last) last = end;
    }
    let cur = parseDayKey(first);
    for (let i = 0; i < MAX_WALK_DAYS && dayKey(cur) <= last; i++) {
      const key = dayKey(cur);
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(o);
      cur = new Date(cur.getTime() + DAY_MS);
    }
  });
  return map;
};

// 다가오는 일정을 오늘 / 내일 / 이후 로 묶는다 (빈 묶음은 뺀다)
export const groupUpcoming = (list, now = new Date()) => {
  const today = dayKey(now);
  const t = kstParts(now);
  const tomorrow = dayKey(kstDate(t.y, t.m, t.d + 1));
  const groups = [
    { label: '오늘', items: [] },
    { label: '내일', items: [] },
    { label: '이후', items: [] },
  ];
  (list || []).forEach((o) => {
    const key = dayKey(toDate(o.start));
    if (key <= today) groups[0].items.push(o);
    else if (key === tomorrow) groups[1].items.push(o);
    else groups[2].items.push(o);
  });
  return groups.filter((g) => g.items.length > 0);
};

// openDetail에 넘길 회차 정보
export const occurrenceRef = (o) => ({ date: dayKey(toDate(o.start)), start: o.start, end: o.end });

// 목록 한 줄의 시간 문구
export const shortTime = (o) => (o.allDay ? '하루 종일' : timeLabel(o.start));
