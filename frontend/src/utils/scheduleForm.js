import { kstParts, kstDate, dayKey } from './calendarGrid';
import { toDate, toTimeInput, DOW_ENUM } from './scheduleFormat';

export const MAX_TITLE = 100;
export const MAX_LOCATION = 200;
export const MAX_MEMO = 5000;
export const MAX_REMINDERS = 5;
export const MAX_REPEAT_COUNT = 365;

export const defaultReminders = (allDay) => (allDay ? [900] : [30]);

const HOUR_MS = 60 * 60 * 1000;

// 'HH:mm' 에 분을 더한다 (23:59를 넘지 않는다)
const addMinutes = (time, minutes) => {
  const [h, m] = time.split(':').map(Number);
  const total = Math.min(h * 60 + m + minutes, 23 * 60 + 59);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
};

const dowOf = (dateKey) => {
  const [y, m, d] = dateKey.split('-').map(Number);
  return DOW_ENUM[kstParts(kstDate(y, m, d)).dow];
};

// 새 일정 폼. prefill: { title?, start?, end?, allDay? } (start/end는 Date 또는 서버 시각 문자열)
export const emptyForm = (prefill = {}, now = new Date()) => {
  const allDay = !!prefill.allDay;
  let start;
  if (prefill.start) start = toDate(prefill.start);
  else {
    // 다음 정각
    const t = kstParts(now);
    const hour = new Date(now.getTime() + 9 * HOUR_MS).getUTCHours();
    start = kstDate(t.y, t.m, t.d, hour + 1);
  }
  let end = prefill.end ? toDate(prefill.end) : null;
  if (!end || end < start) end = allDay ? start : new Date(start.getTime() + HOUR_MS);

  const startTime = allDay ? '09:00' : toTimeInput(start);
  let endTime = allDay ? '10:00' : toTimeInput(end);
  if (!allDay && dayKey(start) === dayKey(end) && endTime <= startTime) endTime = addMinutes(startTime, 60);

  return {
    title: prefill.title ? String(prefill.title).slice(0, MAX_TITLE) : '',
    allDay,
    startDate: dayKey(start),
    startTime,
    endDate: dayKey(end),
    endTime,
    location: '',
    memo: '',
    reminders: defaultReminders(allDay),
    repeatFrequency: 'NONE',
    repeatDays: [],
    repeatEnd: 'never',
    repeatUntil: '',
    repeatCount: 10,
    participants: [],
  };
};

// 서버 일정(ScheduleResponse)으로 고치기 폼을 채운다
export const formFromSchedule = (s) => {
  const start = toDate(s.startAt);
  const end = toDate(s.endAt);
  return {
    title: s.title || '',
    allDay: !!s.allDay,
    startDate: dayKey(start),
    startTime: s.allDay ? '09:00' : toTimeInput(start),
    endDate: dayKey(end),
    endTime: s.allDay ? '10:00' : toTimeInput(end),
    location: s.location || '',
    memo: s.memo || '',
    reminders: [...(s.reminderMinutes || [])],
    repeatFrequency: s.repeatFrequency || 'NONE',
    repeatDays: [...(s.repeatDays || [])],
    repeatEnd: s.repeatUntil ? 'until' : s.repeatCount ? 'count' : 'never',
    repeatUntil: s.repeatUntil || '',
    repeatCount: s.repeatCount || 10,
    participants: (s.participants || []).map((p) => ({ id: p.id, nickname: p.nickname, profileImg: p.profileImg })),
  };
};

// 한 칸을 바꾸고, 끝이 시작보다 앞서지 않게 맞춘다
export const changeForm = (form, field, value) => {
  const next = { ...form, [field]: value };
  if (field === 'allDay') {
    // 알림 기준이 달라지므로 기본값으로 돌린다
    next.reminders = defaultReminders(value);
  }
  if (field === 'startDate' && value) {
    if (form.startDate && form.endDate) {
      // 기간을 그대로 두고 함께 옮긴다
      const diff = Date.parse(form.endDate) - Date.parse(form.startDate);
      if (diff >= 0) next.endDate = new Date(Date.parse(value) + diff).toISOString().slice(0, 10);
    }
    if (!next.endDate || next.endDate < value) next.endDate = value;
    // 매주 반복이면 시작 요일을 켜 둔다 (서버도 넣는다)
    if (next.repeatFrequency === 'WEEKLY') {
      const old = form.startDate ? dowOf(form.startDate) : null;
      const days = next.repeatDays.filter((d) => d !== old);
      next.repeatDays = days.includes(dowOf(value)) ? days : [...days, dowOf(value)];
    }
  }
  if (field === 'startTime' && value && !next.allDay && next.startDate === next.endDate && next.endTime <= value) {
    next.endTime = addMinutes(value, 60);
  }
  if (field === 'repeatFrequency' && value === 'WEEKLY' && next.repeatDays.length === 0 && next.startDate) {
    next.repeatDays = [dowOf(next.startDate)];
  }
  return next;
};

// 문제가 있으면 안내 문구, 없으면 null
export const validateForm = (f) => {
  if (!f.title.trim()) return '제목을 적어 주세요.';
  if (!f.startDate || !f.endDate) return '날짜를 골라 주세요.';
  if (f.allDay) {
    if (f.endDate < f.startDate) return '끝나는 날이 시작하는 날보다 빨라요.';
  } else {
    if (!f.startTime || !f.endTime) return '시각을 골라 주세요.';
    if (`${f.endDate}T${f.endTime}` <= `${f.startDate}T${f.startTime}`) return '끝나는 시각이 시작보다 늦어야 해요.';
  }
  if (f.repeatFrequency !== 'NONE') {
    if (f.repeatEnd === 'until' && (!f.repeatUntil || f.repeatUntil < f.startDate)) return '반복이 끝나는 날을 확인해 주세요.';
    if (f.repeatEnd === 'count') {
      const n = Number(f.repeatCount);
      if (!Number.isInteger(n) || n < 1 || n > MAX_REPEAT_COUNT) return `반복 횟수는 1~${MAX_REPEAT_COUNT}번이에요.`;
    }
  }
  if (f.reminders.length > MAX_REMINDERS) return `알림은 ${MAX_REMINDERS}개까지예요.`;
  return null;
};

// 폼 → ScheduleRequest
export const buildRequest = (f) => {
  const repeating = f.repeatFrequency !== 'NONE';
  return {
    title: f.title.trim(),
    location: f.location.trim() || null,
    memo: f.memo.trim() || null,
    startAt: `${f.startDate}T${f.allDay ? '00:00' : f.startTime}:00`,
    endAt: `${f.endDate}T${f.allDay ? '00:00' : f.endTime}:00`,
    allDay: f.allDay,
    repeatFrequency: f.repeatFrequency,
    repeatDays: repeating && f.repeatFrequency === 'WEEKLY' ? f.repeatDays : [],
    repeatUntil: repeating && f.repeatEnd === 'until' ? f.repeatUntil : null,
    repeatCount: repeating && f.repeatEnd === 'count' ? Number(f.repeatCount) : null,
    reminderMinutes: [...f.reminders],
    participantIds: f.participants.map((p) => p.id),
  };
};
