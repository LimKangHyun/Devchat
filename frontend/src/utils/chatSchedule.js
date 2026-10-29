import { kstParts, kstDate } from './calendarGrid';
import { findDates } from './dateDetect';
import { splitMentions } from './mention';
import { parseServerTime } from './relativeTime';

const DAY_MS = 24 * 60 * 60 * 1000;
// split에 캡처 그룹을 쓰면 홀수 번째 조각이 URL이다
const URL_SPLIT = /(https?:\/\/[^\s]+)/;

// 메시지 내용을 한 줄로 줄여 앞 50자
export const chatScheduleTitle = (content) =>
  String(content || '').replace(/\s+/g, ' ').trim().slice(0, 50);

// 찾은 날짜(found)나 내일 하루 종일(KST)로 일정 만들기 값을 만든다
export const chatSchedulePrefill = (content, found, now = new Date()) => {
  const title = chatScheduleTitle(content);
  if (found) return { title, start: found.start, end: found.end, allDay: found.allDay };
  const t = kstParts(new Date(now.getTime() + DAY_MS));
  const day = kstDate(t.y, t.m, t.d);
  return { title, start: day, end: day, allDay: true };
};

// 메시지를 URL·멘션·글 조각으로 나눈다. [{ type: 'url' | 'mention' | 'text', text }]
export const messageSegments = (content) => {
  const out = [];
  String(content || '').split(URL_SPLIT).forEach((part, i) => {
    if (i % 2 === 1) {
      out.push({ type: 'url', text: part });
      return;
    }
    splitMentions(part).forEach((p) => out.push({ type: p.mention ? 'mention' : 'text', text: p.text }));
  });
  return out.filter((seg) => seg.text);
};

// URL·멘션이 아닌 글에서 처음 나오는 날짜 (날짜 칩과 같은 기준). 없으면 undefined
export const firstDateInMessage = (content, createdAt) => {
  const base = createdAt ? parseServerTime(createdAt) : new Date();
  for (const seg of messageSegments(content)) {
    if (seg.type !== 'text') continue;
    const [found] = findDates(seg.text, base);
    if (found) return found;
  }
  return undefined;
};
