import { relativeTime } from './relativeTime';

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
