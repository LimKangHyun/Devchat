// 알림 선택지. value는 minutesBefore (시간 일정: 시작 기준, 하루 종일: 그날 0시 기준)
export const REMINDER_CHOICES = {
  timed: [
    { value: 0, label: '시작할 때' },
    { value: 10, label: '10분 전' },
    { value: 30, label: '30분 전' },
    { value: 60, label: '1시간 전' },
    { value: 120, label: '2시간 전' },
    { value: 1440, label: '하루 전' },
    { value: 10080, label: '일주일 전' },
  ],
  allDay: [
    { value: -540, label: '당일 오전 9시' },
    { value: 900, label: '하루 전 오전 9시' },
    { value: 2340, label: '이틀 전 오전 9시' },
    { value: 9540, label: '일주일 전 오전 9시' },
  ],
};

export const reminderLabel = (minutes, allDay) => {
  const list = allDay ? REMINDER_CHOICES.allDay : REMINDER_CHOICES.timed;
  const found = list.find((c) => c.value === minutes);
  return found ? found.label : `${minutes}분 전`;
};
