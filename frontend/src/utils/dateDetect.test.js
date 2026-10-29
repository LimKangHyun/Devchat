import { findDates } from './dateDetect';

const base = new Date('2026-10-28T12:00:00+09:00'); // 수요일
const at = (s) => new Date(`${s}+09:00`).getTime();

describe('findDates', () => {
  it('M월 D일은 하루 종일', () => {
    const [r] = findDates('10월 30일', base);
    expect(r.start.getTime()).toBe(at('2026-10-30T00:00:00'));
    expect(r.end.getTime()).toBe(r.start.getTime());
    expect(r.allDay).toBe(true);
  });

  it('다음 주 화요일 오후 3시', () => {
    const res = findDates('다음 주 화요일 오후 3시에 회의', base);
    expect(res).toHaveLength(1);
    expect(res[0].index).toBe(0);
    expect(res[0].length).toBe('다음 주 화요일 오후 3시'.length);
    expect(res[0].start.getTime()).toBe(at('2026-11-03T15:00:00'));
    expect(res[0].end.getTime()).toBe(at('2026-11-03T16:00:00'));
    expect(res[0].allDay).toBe(false);
  });

  it('금요일 3시 반', () => {
    const [r] = findDates('금요일 3시 반', base);
    expect(r.start.getTime()).toBe(at('2026-10-30T15:30:00'));
  });

  it('오늘이 그 요일이면 오늘', () => {
    const [r] = findDates('수요일', base);
    expect(r.start.getTime()).toBe(at('2026-10-28T00:00:00'));
  });

  it('지난 요일은 다음 주로', () => {
    const [r] = findDates('월욜', base);
    expect(r.start.getTime()).toBe(at('2026-11-02T00:00:00'));
  });

  it('이번 주 월요일은 지난 월요일 (주는 월요일 시작)', () => {
    const [r] = findDates('이번 주 월요일', base);
    expect(r.start.getTime()).toBe(at('2026-10-26T00:00:00'));
    const [s] = findDates('이번주 일요일', base);
    expect(s.start.getTime()).toBe(at('2026-11-01T00:00:00'));
  });

  it('다다음 주', () => {
    const [r] = findDates('다다음 주 목요일', base);
    expect(r.start.getTime()).toBe(at('2026-11-12T00:00:00'));
  });

  it('내일 15:30', () => {
    const [r] = findDates('내일 15:30', base);
    expect(r.start.getTime()).toBe(at('2026-10-29T15:30:00'));
  });

  it('오전/오후와 12시', () => {
    expect(findDates('내일 오전 12시', base)[0].start.getTime()).toBe(at('2026-10-29T00:00:00'));
    expect(findDates('내일 오후 12시', base)[0].start.getTime()).toBe(at('2026-10-29T12:00:00'));
    expect(findDates('내일 10시', base)[0].start.getTime()).toBe(at('2026-10-29T10:00:00'));
    expect(findDates('내일 2시 15분', base)[0].start.getTime()).toBe(at('2026-10-29T14:15:00'));
  });

  it('M/D', () => {
    const [r] = findDates('11/2', base);
    expect(r.start.getTime()).toBe(at('2026-11-02T00:00:00'));
  });

  it('30일 넘게 과거면 다음 해', () => {
    const [r] = findDates('1월 3일', base);
    expect(r.start.getTime()).toBe(at('2027-01-03T00:00:00'));
    const [p] = findDates('10월 1일', base); // 27일 전이라 올해
    expect(p.start.getTime()).toBe(at('2026-10-01T00:00:00'));
  });

  it('YYYY-MM-DD, YYYY.MM.DD, YYYY/M/D', () => {
    expect(findDates('2026-11-02', base)[0].start.getTime()).toBe(at('2026-11-02T00:00:00'));
    expect(findDates('2026.11.02', base)[0].start.getTime()).toBe(at('2026-11-02T00:00:00'));
    const r = findDates('2026/11/2', base);
    expect(r).toHaveLength(1);
    expect(r[0].start.getTime()).toBe(at('2026-11-02T00:00:00'));
  });

  it('버전 번호 같은 건 잡지 않는다', () => {
    expect(findDates('버전 1.2/3', base)).toEqual([]);
    expect(findDates('13/40', base)).toEqual([]);
    expect(findDates('1/2/3', base)).toEqual([]);
  });

  it('날짜 없는 시간은 잡지 않는다', () => {
    expect(findDates('3시에 보자', base)).toEqual([]);
  });

  it('여러 개', () => {
    const res = findDates('2026-11-02 그리고 모레', base);
    expect(res).toHaveLength(2);
    expect(res[0].index).toBe(0);
    expect(res[1].start.getTime()).toBe(at('2026-10-30T00:00:00'));
  });

  it('시간(duration)이나 반드시는 시각이 아니다', () => {
    const res = findDates('내일 3시간', base);
    expect(res).toHaveLength(1);
    expect(res[0].allDay).toBe(true);
    expect(res[0].length).toBe(2);
    const r = findDates('내일 3시 반드시 와', base);
    expect(r[0].start.getTime()).toBe(at('2026-10-29T15:00:00'));
    expect(r[0].length).toBe('내일 3시'.length);
    expect(findDates('내일 3시 반', base)[0].start.getTime()).toBe(at('2026-10-29T15:30:00'));
  });

  it('1~6시 오후 규칙은 H시에만, HH:MM은 24시간', () => {
    expect(findDates('내일 03:00', base)[0].start.getTime()).toBe(at('2026-10-29T03:00:00'));
    expect(findDates('내일 1:30', base)[0].start.getTime()).toBe(at('2026-10-29T01:30:00'));
    expect(findDates('내일 3시', base)[0].start.getTime()).toBe(at('2026-10-29T15:00:00'));
  });

  it('12:345는 시각이 아니다', () => {
    const [r] = findDates('내일 12:345', base);
    expect(r.allDay).toBe(true);
  });

  it('지난 요일은 잡지 않는다', () => {
    expect(findDates('지난 월요일', base)).toEqual([]);
    expect(findDates('지난주 월요일', base)).toEqual([]);
    expect(findDates('저번 주 월요일', base)).toEqual([]);
    expect(findDates('저번주 월요일', base)).toEqual([]);
  });

  it('연도는 1900~2100만', () => {
    expect(findDates('1234-5-6', base)).toEqual([]);
    expect(findDates('12026-11-02', base)).toEqual([]);
  });

  it('없는 날짜는 잡지 않는다', () => {
    expect(findDates('2/30', base)).toEqual([]);
    expect(findDates('2월 30일', base)).toEqual([]);
    expect(findDates('2/29', base)).toEqual([]); // 2026년은 평년
  });

  it('겹치면 긴 것 하나만', () => {
    const res = findDates('5/6월 7일', base);
    expect(res).toHaveLength(1);
    expect(res[0].index).toBe(2);
    expect(res[0].start.getTime()).toBe(at('2027-06-07T00:00:00'));
  });

  it('정규식 lookbehind를 쓰지 않는다 (구형 Safari)', () => {
    const fs = require('fs');
    const path = require('path');
    const src = fs.readFileSync(path.join(__dirname, 'dateDetect.js'), 'utf8');
    expect(src).not.toContain('(?<');
  });

  it('base는 KST 날짜로 본다', () => {
    // UTC로는 10/27이지만 KST로는 10/28 00:30
    const early = new Date('2026-10-28T00:30:00+09:00');
    expect(findDates('오늘', early)[0].start.getTime()).toBe(at('2026-10-28T00:00:00'));
  });
});
