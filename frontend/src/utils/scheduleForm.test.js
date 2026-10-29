import { emptyForm, formFromSchedule, changeForm, validateForm, buildRequest } from './scheduleForm';
import { kstDate } from './calendarGrid';

describe('emptyForm', () => {
  it('prefill이 없으면 다음 정각부터 1시간, 알림 30분 전', () => {
    const f = emptyForm({}, kstDate(2026, 10, 29, 14, 20));
    expect(f).toMatchObject({ allDay: false, startDate: '2026-10-29', startTime: '15:00', endDate: '2026-10-29', endTime: '16:00', reminders: [30] });
  });

  it('하루 종일 prefill은 그날, 알림은 하루 전 9시', () => {
    const day = kstDate(2026, 11, 3);
    const f = emptyForm({ title: '워크숍', start: day, end: day, allDay: true });
    expect(f).toMatchObject({ title: '워크숍', allDay: true, startDate: '2026-11-03', endDate: '2026-11-03', reminders: [900] });
  });

  it('끝이 없으면 시간 일정은 1시간', () => {
    const f = emptyForm({ start: kstDate(2026, 11, 3, 15, 0) });
    expect(f).toMatchObject({ startTime: '15:00', endTime: '16:00', endDate: '2026-11-03' });
  });
});

describe('changeForm', () => {
  const base = emptyForm({ start: kstDate(2026, 10, 29, 10, 0) });

  it('하루 종일을 바꾸면 알림 기본값도 바뀐다', () => {
    expect(changeForm(base, 'allDay', true).reminders).toEqual([900]);
  });

  it('시작 날짜를 옮기면 끝 날짜도 같이 옮긴다', () => {
    const f = { ...base, endDate: '2026-10-30' };
    expect(changeForm(f, 'startDate', '2026-11-05').endDate).toBe('2026-11-06');
  });

  it('시작 시각이 끝 시각을 넘으면 끝을 한 시간 뒤로', () => {
    expect(changeForm(base, 'startTime', '12:30').endTime).toBe('13:30');
    expect(changeForm(base, 'startTime', '23:30').endTime).toBe('23:59');
  });

  it('매주를 고르면 시작 요일을 켠다', () => {
    expect(changeForm(base, 'repeatFrequency', 'WEEKLY').repeatDays).toEqual(['THURSDAY']);
  });

  it('매주 월·수를 고른 채 시작을 수요일로 옮겨도 월·수 그대로', () => {
    const f = { ...emptyForm({ start: kstDate(2026, 11, 2, 10, 0) }), repeatFrequency: 'WEEKLY', repeatDays: ['MONDAY', 'WEDNESDAY'] };
    expect(changeForm(f, 'startDate', '2026-11-04').repeatDays).toEqual(['MONDAY', 'WEDNESDAY']);
    // 새 시작 요일은 더한다
    expect(changeForm(f, 'startDate', '2026-11-06').repeatDays).toEqual(['MONDAY', 'WEDNESDAY', 'FRIDAY']);
  });
});

describe('validateForm / buildRequest', () => {
  it('제목이 없거나 끝이 앞서면 막는다', () => {
    const f = emptyForm({ start: kstDate(2026, 10, 29, 10, 0) });
    expect(validateForm(f)).toBe('제목을 적어 주세요.');
    expect(validateForm({ ...f, title: 'a', endTime: '09:00' })).toBe('끝나는 시각이 시작보다 늦어야 해요.');
    expect(validateForm({ ...f, title: 'a', repeatFrequency: 'DAILY', repeatEnd: 'count', repeatCount: 0 })).toMatch('반복 횟수');
    expect(validateForm({ ...f, title: 'a' })).toBeNull();
  });

  it('하루 종일은 0시로 보내고 반복 끝 조건은 하나만', () => {
    const f = {
      ...emptyForm({ start: kstDate(2026, 11, 3), allDay: true }),
      title: ' 워크숍 ', repeatFrequency: 'WEEKLY', repeatDays: ['TUESDAY'], repeatEnd: 'count', repeatCount: '5', repeatUntil: '2026-12-01',
      participants: [{ id: 7, nickname: '민수' }],
    };
    expect(buildRequest(f)).toEqual({
      title: '워크숍', location: null, memo: null,
      startAt: '2026-11-03T00:00:00', endAt: '2026-11-03T00:00:00', allDay: true,
      repeatFrequency: 'WEEKLY', repeatDays: ['TUESDAY'], repeatUntil: null, repeatCount: 5,
      reminderMinutes: [900], participantIds: [7],
    });
  });

  it('서버 일정으로 고치기 폼을 채운다', () => {
    const f = formFromSchedule({
      title: '회의', startAt: '2026-11-03T15:00:00', endAt: '2026-11-03T16:30:00', allDay: false,
      repeatFrequency: 'MONTHLY', repeatDays: [], repeatUntil: '2027-01-01', repeatCount: null,
      reminderMinutes: [10, 30], participants: [{ id: 2, nickname: '지수', profileImg: null }],
    });
    expect(f).toMatchObject({ startTime: '15:00', endTime: '16:30', repeatEnd: 'until', reminders: [10, 30], participants: [{ id: 2 }] });
  });
});
