import { chatScheduleTitle, chatSchedulePrefill } from './chatSchedule';
import { kstDate } from './calendarGrid';

describe('chatSchedule', () => {
  it('제목은 한 줄로 줄여 50자까지', () => {
    expect(chatScheduleTitle('가나\n다   라')).toBe('가나 다 라');
    expect(chatScheduleTitle('a'.repeat(80))).toHaveLength(50);
    expect(chatScheduleTitle(null)).toBe('');
  });

  it('찾은 날짜가 있으면 그대로 쓴다', () => {
    const found = { start: kstDate(2026, 11, 3, 15), end: kstDate(2026, 11, 3, 16), allDay: false };
    expect(chatSchedulePrefill('회의', found)).toEqual({ title: '회의', ...found });
  });

  it('없으면 내일 하루 종일 (KST)', () => {
    // 2026-10-29 23:30 KST
    const now = kstDate(2026, 10, 29, 23, 30);
    const p = chatSchedulePrefill('hi', null, now);
    expect(p.allDay).toBe(true);
    expect(p.start.getTime()).toBe(kstDate(2026, 10, 30).getTime());
  });
});
