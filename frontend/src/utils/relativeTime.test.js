import { relativeTime, parseServerTime, formatKstDate } from './relativeTime';

describe('서버 시각(오프셋 없는 KST)', () => {
  const now = new Date('2026-10-28T12:00:00+09:00');

  it('오프셋이 없으면 +09:00으로 읽는다', () => {
    expect(parseServerTime('2026-10-28T11:57:00').getTime()).toBe(new Date('2026-10-28T11:57:00+09:00').getTime());
    expect(parseServerTime('2026-10-28T11:57:00.123456').getTime()).toBe(new Date('2026-10-28T11:57:00.123+09:00').getTime());
  });

  it('오프셋이 있으면 그대로 읽는다', () => {
    expect(parseServerTime('2026-10-28T02:57:00Z').getTime()).toBe(new Date('2026-10-28T11:57:00+09:00').getTime());
  });

  it('relativeTime이 문자열을 KST로 계산한다', () => {
    expect(relativeTime('2026-10-28T11:57:00', now)).toBe('3분 전');
    expect(relativeTime('2026-10-28T11:56:59.123456', now)).toBe('3분 전');
    expect(relativeTime('2026-10-27T23:00:00', now)).toBe('어제');
  });

  it('formatKstDate는 한국 날짜를 만든다', () => {
    expect(formatKstDate('2026-10-02T00:30:00')).toBe('2026.10.2');
    expect(formatKstDate(new Date('2026-10-01T15:30:00Z'))).toBe('2026.10.2');
  });
});

describe('relativeTime', () => {
  const now = new Date('2026-10-28T12:00:00+09:00');
  const at = (iso) => new Date(iso);

  it('1분 안이면 방금 전', () => {
    expect(relativeTime(at('2026-10-28T11:59:30+09:00'), now)).toBe('방금 전');
  });

  it('분 단위', () => {
    expect(relativeTime(at('2026-10-28T11:57:00+09:00'), now)).toBe('3분 전');
  });

  it('시간 단위', () => {
    expect(relativeTime(at('2026-10-28T07:00:00+09:00'), now)).toBe('5시간 전');
  });

  it('어제', () => {
    expect(relativeTime(at('2026-10-27T23:00:00+09:00'), now)).toBe('어제');
  });

  it('올해 지난 날짜는 월 일', () => {
    expect(relativeTime(at('2026-10-02T09:00:00+09:00'), now)).toBe('10월 2일');
  });

  it('작년 이전은 연월일', () => {
    expect(relativeTime(at('2025-12-31T09:00:00+09:00'), now)).toBe('2025년 12월 31일');
  });
});
