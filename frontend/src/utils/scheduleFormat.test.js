import {
  repeatLabel, rangeLabel, timeLabel, dateLabel, toLocalIso, occurrencesByDay, groupUpcoming,
} from './scheduleFormat';
import { kstDate } from './calendarGrid';

describe('repeatLabel', () => {
  it('반복 없음은 빈 문자열', () => {
    expect(repeatLabel({ repeatFrequency: 'NONE' })).toBe('');
    expect(repeatLabel({})).toBe('');
  });

  it('매주는 월요일부터 요일을 잇는다', () => {
    expect(repeatLabel({ repeatFrequency: 'WEEKLY', repeatDays: ['WEDNESDAY', 'MONDAY'] })).toBe('매주 월·수');
    expect(repeatLabel({ repeatFrequency: 'WEEKLY', repeatDays: ['SUNDAY', 'SATURDAY'] })).toBe('매주 토·일');
  });

  it('매일, 매달, 매년은 시작일을 쓴다', () => {
    const startAt = '2026-01-31T10:00:00';
    expect(repeatLabel({ repeatFrequency: 'DAILY', startAt })).toBe('매일');
    expect(repeatLabel({ repeatFrequency: 'MONTHLY', startAt })).toBe('매달 31일');
    expect(repeatLabel({ repeatFrequency: 'YEARLY', startAt })).toBe('매년 1월 31일');
  });

  it('끝 조건을 붙인다', () => {
    const startAt = '2026-10-29T10:00:00';
    expect(repeatLabel({ repeatFrequency: 'DAILY', repeatUntil: '2026-11-30', startAt })).toBe('매일 · 11월 30일까지');
    expect(repeatLabel({ repeatFrequency: 'DAILY', repeatUntil: '2027-01-05', startAt })).toBe('매일 · 2027년 1월 5일까지');
    expect(repeatLabel({ repeatFrequency: 'MONTHLY', repeatCount: 10, startAt })).toBe('매달 29일 · 10번');
  });
});

describe('시간 문구', () => {
  it('오전/오후 h:mm', () => {
    expect(timeLabel('2026-10-29T00:05:00')).toBe('오전 12:05');
    expect(timeLabel('2026-10-29T12:00:00')).toBe('오후 12:00');
    expect(timeLabel('2026-10-29T15:30:00')).toBe('오후 3:30');
  });

  it('날짜와 요일', () => {
    expect(dateLabel('2026-10-29T00:00:00')).toBe('10월 29일 (목)');
  });

  it('범위', () => {
    expect(rangeLabel({ start: '2026-10-29T00:00:00', end: '2026-10-29T00:00:00', allDay: true })).toBe('10월 29일 (목) 하루 종일');
    expect(rangeLabel({ start: '2026-10-29T00:00:00', end: '2026-10-31T00:00:00', allDay: true })).toBe('10월 29일 (목) – 10월 31일 (토)');
    expect(rangeLabel({ start: '2026-10-29T15:00:00', end: '2026-10-29T16:00:00', allDay: false })).toBe('10월 29일 (목) 오후 3:00 – 오후 4:00');
    expect(rangeLabel({ start: '2026-10-29T23:00:00', end: '2026-10-30T01:00:00', allDay: false }))
      .toBe('10월 29일 (목) 오후 11:00 – 10월 30일 (금) 오전 1:00');
  });

  it('Date를 오프셋 없는 KST 문자열로', () => {
    expect(toLocalIso(kstDate(2026, 10, 29, 15, 7))).toBe('2026-10-29T15:07:00');
    expect(toLocalIso(kstDate(2026, 11, 1))).toBe('2026-11-01T00:00:00');
  });
});

describe('occurrencesByDay', () => {
  it('여러 날 일정은 모든 날에, 0시에 끝나는 시간 일정은 다음 날에 넣지 않는다', () => {
    const map = occurrencesByDay([
      { scheduleId: 1, title: '여행', start: '2026-10-29T00:00:00', end: '2026-10-31T00:00:00', allDay: true },
      { scheduleId: 2, title: '야근', start: '2026-10-29T22:00:00', end: '2026-10-30T00:00:00', allDay: false },
    ]);
    expect(map.get('2026-10-29').map((o) => o.scheduleId)).toEqual([1, 2]);
    expect(map.get('2026-10-30').map((o) => o.scheduleId)).toEqual([1]);
    expect(map.get('2026-10-31').map((o) => o.scheduleId)).toEqual([1]);
    expect(map.has('2026-11-01')).toBe(false);
  });

  it('같은 날은 하루 종일 먼저, 그다음 시작 순', () => {
    const map = occurrencesByDay([
      { scheduleId: 3, title: 'b', start: '2026-10-29T15:00:00', end: '2026-10-29T16:00:00', allDay: false },
      { scheduleId: 4, title: 'a', start: '2026-10-29T09:00:00', end: '2026-10-29T10:00:00', allDay: false },
      { scheduleId: 5, title: 'c', start: '2026-10-29T00:00:00', end: '2026-10-29T00:00:00', allDay: true },
    ]);
    expect(map.get('2026-10-29').map((o) => o.scheduleId)).toEqual([5, 4, 3]);
  });
});

describe('groupUpcoming', () => {
  it('오늘·내일·이후로 묶고 빈 묶음은 뺀다', () => {
    const now = kstDate(2026, 10, 29, 10, 0);
    const groups = groupUpcoming([
      { scheduleId: 1, start: '2026-10-29T15:00:00' },
      { scheduleId: 2, start: '2026-10-31T09:00:00' },
      { scheduleId: 3, start: '2026-11-03T09:00:00' },
    ], now);
    expect(groups.map((g) => g.label)).toEqual(['오늘', '이후']);
    expect(groups[1].items.map((o) => o.scheduleId)).toEqual([2, 3]);
  });

  it('내일은 KST 기준', () => {
    // UTC로는 아직 29일이지만 KST로는 30일 오전 1시
    const now = new Date('2026-10-29T16:00:00Z');
    const groups = groupUpcoming([{ scheduleId: 1, start: '2026-10-31T09:00:00' }], now);
    expect(groups[0].label).toBe('내일');
  });
});
