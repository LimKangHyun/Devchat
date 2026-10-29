import { REMINDER_CHOICES, reminderLabel } from './reminderOptions';

describe('reminderOptions', () => {
  it('표에 있는 값은 라벨을 돌려준다', () => {
    expect(reminderLabel(900, true)).toBe('하루 전 오전 9시');
    expect(reminderLabel(-540, true)).toBe('당일 오전 9시');
    expect(reminderLabel(0, false)).toBe('시작할 때');
    expect(reminderLabel(1440, false)).toBe('하루 전');
  });

  it('표에 없는 값은 N분 전', () => {
    expect(reminderLabel(45, false)).toBe('45분 전');
    expect(reminderLabel(900, false)).toBe('900분 전');
  });

  it('선택지 개수', () => {
    expect(REMINDER_CHOICES.timed).toHaveLength(7);
    expect(REMINDER_CHOICES.allDay).toHaveLength(4);
  });
});
