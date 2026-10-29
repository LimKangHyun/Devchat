import { chatScheduleTitle, chatSchedulePrefill, firstDateInMessage, messageSegments } from './chatSchedule';
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

describe('messageSegments / firstDateInMessage', () => {
  const at = (s) => new Date(`${s}+09:00`).getTime();
  // 서버 시각 (KST, 오프셋 없음)
  const createdAt = '2026-10-28T12:00:00';

  it('URL·멘션·글로 나눈다', () => {
    expect(messageSegments('@지수 https://a.b/c 내일 봐')).toEqual([
      { type: 'mention', text: '@지수' },
      { type: 'text', text: ' ' },
      { type: 'url', text: 'https://a.b/c' },
      { type: 'text', text: ' 내일 봐' },
    ]);
  });

  it('URL 안의 날짜는 무시하고 글에서 찾는다', () => {
    const f = firstDateInMessage('https://blog.x/2026/10/30/post 내일', createdAt);
    expect(f.start.getTime()).toBe(at('2026-10-29T00:00:00'));
    expect(f.allDay).toBe(true);
  });

  it('멘션 안의 날짜도 무시하고, 없으면 undefined', () => {
    expect(firstDateInMessage('@내일 안녕', createdAt)).toBeUndefined();
    expect(firstDateInMessage('', createdAt)).toBeUndefined();
  });
});
