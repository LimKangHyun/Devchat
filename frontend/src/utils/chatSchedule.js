import { kstParts, kstDate } from './calendarGrid';

const DAY_MS = 24 * 60 * 60 * 1000;

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
