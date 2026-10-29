import { monthGrid, weekDays, dayKey } from './calendarGrid';

describe('calendarGrid', () => {
  it('monthGrid는 6주 x 7일이고 일요일에 시작한다', () => {
    const grid = monthGrid(2026, 11);
    expect(grid).toHaveLength(6);
    grid.forEach((w) => expect(w).toHaveLength(7));
    expect(dayKey(grid[0][0])).toBe('2026-11-01');
    expect(dayKey(grid[5][6])).toBe('2026-12-12');
  });

  it('달이 일요일이 아닌 날 시작하면 앞달 날짜가 채워진다', () => {
    expect(dayKey(monthGrid(2026, 10)[0][0])).toBe('2026-09-27');
  });

  it('weekDays는 그 주 일~토', () => {
    const days = weekDays(new Date('2026-10-28T12:00:00+09:00'));
    expect(days.map(dayKey)).toEqual([
      '2026-10-25', '2026-10-26', '2026-10-27', '2026-10-28',
      '2026-10-29', '2026-10-30', '2026-10-31',
    ]);
  });

  it('dayKey는 KST 기준', () => {
    expect(dayKey(new Date('2026-10-28T23:30:00+09:00'))).toBe('2026-10-28');
    expect(dayKey(new Date('2026-10-28T00:10:00+09:00'))).toBe('2026-10-28');
  });
});
