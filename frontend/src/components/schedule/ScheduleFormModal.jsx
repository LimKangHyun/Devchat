import React, { useState } from 'react';
import { X, AlertTriangle, Loader2, Bell } from 'lucide-react';
import { createSchedule, updateSchedule, errorMessage, notifySchedulesChanged } from '../../api/scheduleApi';
import {
  emptyForm, formFromSchedule, changeForm, validateForm, buildRequest,
  MAX_TITLE, MAX_LOCATION, MAX_MEMO, MAX_REMINDERS, MAX_REPEAT_COUNT,
} from '../../utils/scheduleForm';
import { REMINDER_CHOICES, reminderLabel } from '../../utils/reminderOptions';
import { DOW_LABELS, DOW_ENUM } from '../../utils/scheduleFormat';
import { kstParts, kstDate } from '../../utils/calendarGrid';
import ParticipantPicker from './ParticipantPicker';
import useEscapeKey from './useEscapeKey';
import ms from '../modals/Modals.module.css';
import s from './Schedule.module.css';

const REPEAT_OPTIONS = [
  ['NONE', '안 함'],
  ['DAILY', '매일'],
  ['WEEKLY', '매주'],
  ['MONTHLY', '매달'],
  ['YEARLY', '매년'],
];
const REPEAT_ENDS = [
  ['never', '계속'],
  ['until', '날짜까지'],
  ['count', 'N번'],
];
// 월요일부터 보여 준다
const WEEK_ORDER = [1, 2, 3, 4, 5, 6, 0];

const dowOfKey = (key) => {
  const [y, m, d] = key.split('-').map(Number);
  return DOW_ENUM[kstParts(kstDate(y, m, d)).dow];
};

// 일정 만들기 / 고치기. schedule이 있으면 고치기
const ScheduleFormModal = ({ schedule, prefill, onClose, onSaved }) => {
  const editing = !!schedule;
  const [form, setForm] = useState(() => (editing ? formFromSchedule(schedule) : emptyForm(prefill)));
  const [saving, setSaving] = useState(false);
  useEscapeKey(onClose, saving);
  const [error, setError] = useState(null);

  const set = (field) => (e) => {
    const v = e && e.target ? (e.target.type === 'checkbox' ? e.target.checked : e.target.value) : e;
    setForm((f) => changeForm(f, field, v));
  };

  const choices = form.allDay ? REMINDER_CHOICES.allDay : REMINDER_CHOICES.timed;
  const remaining = choices.filter((c) => !form.reminders.includes(c.value));
  const addReminder = (e) => {
    const v = Number(e.target.value);
    if (e.target.value === '' || form.reminders.length >= MAX_REMINDERS) return;
    setForm((f) => ({ ...f, reminders: [...f.reminders, v].sort((a, b) => a - b) }));
  };
  const removeReminder = (v) => setForm((f) => ({ ...f, reminders: f.reminders.filter((x) => x !== v) }));

  const startDow = form.startDate ? dowOfKey(form.startDate) : null;
  const toggleDay = (day) => setForm((f) => ({
    ...f,
    repeatDays: f.repeatDays.includes(day) ? f.repeatDays.filter((d) => d !== day) : [...f.repeatDays, day],
  }));

  const submit = async (e) => {
    e.preventDefault();
    const problem = validateForm(form);
    if (problem) { setError(problem); return; }
    setSaving(true);
    setError(null);
    try {
      const body = buildRequest(form);
      const saved = editing ? await updateSchedule(schedule.id, body) : await createSchedule(body);
      notifySchedulesChanged();
      onSaved?.(saved);
    } catch (err) {
      setError(errorMessage(err, '일정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.'));
      setSaving(false);
    }
  };

  return (
    <div className="dc-modal-backdrop" onClick={saving ? undefined : onClose}>
      <div className="dc-modal" style={{ maxWidth: 560 }} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-labelledby="schedule-form-title">
        <div className="dc-modal-header">
          <h3 className="dc-modal-title" id="schedule-form-title">{editing ? '일정 고치기' : '새 일정'}</h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기" disabled={saving}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={submit} className={ms.form}>
          <div className="dc-modal-body">
            <div className={s.field}>
              <label htmlFor="schedule-title" className="dc-label">제목 <span className={ms.required}>*</span></label>
              <input
                id="schedule-title"
                className="dc-input"
                value={form.title}
                onChange={set('title')}
                maxLength={MAX_TITLE}
                autoComplete="off"
                autoFocus={!editing}
                placeholder="예) 스프린트 회의"
              />
            </div>

            <div className={s.field}>
              <label className={s.checkRow}>
                <input type="checkbox" checked={form.allDay} onChange={set('allDay')} />
                <span>하루 종일</span>
              </label>
              <div className={s.timeGrid}>
                <span className={s.timeLabel}>시작</span>
                <div className={s.timeInputs}>
                  <input type="date" className="dc-input" value={form.startDate} onChange={set('startDate')} aria-label="시작 날짜" required />
                  {!form.allDay && (
                    <input type="time" className="dc-input" value={form.startTime} onChange={set('startTime')} aria-label="시작 시각" required />
                  )}
                </div>
                <span className={s.timeLabel}>끝</span>
                <div className={s.timeInputs}>
                  <input type="date" className="dc-input" value={form.endDate} min={form.startDate} onChange={set('endDate')} aria-label="끝 날짜" required />
                  {!form.allDay && (
                    <input type="time" className="dc-input" value={form.endTime} onChange={set('endTime')} aria-label="끝 시각" required />
                  )}
                </div>
              </div>
            </div>

            <div className={s.field}>
              <label htmlFor="schedule-location" className="dc-label">장소 <span className={ms.optional}>(선택)</span></label>
              <input id="schedule-location" className="dc-input" value={form.location} onChange={set('location')} maxLength={MAX_LOCATION} autoComplete="off" />
            </div>

            <div className={s.field}>
              <label htmlFor="schedule-memo" className="dc-label">메모 <span className={ms.optional}>(선택)</span></label>
              <textarea id="schedule-memo" className={`dc-input ${s.textarea}`} value={form.memo} onChange={set('memo')} maxLength={MAX_MEMO} rows={3} />
            </div>

            <div className={s.field}>
              <span className="dc-label">알림</span>
              <div className={s.chipRow}>
                {form.reminders.map((v) => (
                  <span key={v} className={s.reminderChip}>
                    <Bell size={12} />
                    <span>{reminderLabel(v, form.allDay)}</span>
                    <button type="button" onClick={() => removeReminder(v)} aria-label={`${reminderLabel(v, form.allDay)} 알림 빼기`}>
                      <X size={12} />
                    </button>
                  </span>
                ))}
                {form.reminders.length === 0 && <span className={s.muted}>알림 없음</span>}
                {form.reminders.length < MAX_REMINDERS && remaining.length > 0 && (
                  <select className={s.addSelect} value="" onChange={addReminder} aria-label="알림 추가">
                    <option value="">+ 추가</option>
                    {remaining.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
                  </select>
                )}
              </div>
            </div>

            <div className={s.field}>
              <label htmlFor="schedule-repeat" className="dc-label">반복</label>
              <select id="schedule-repeat" className="dc-input" value={form.repeatFrequency} onChange={set('repeatFrequency')}>
                {REPEAT_OPTIONS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
              </select>
              {editing && schedule.repeatFrequency && schedule.repeatFrequency !== 'NONE' && (
                <p className="dc-help">반복 일정은 고치면 모든 회차가 함께 바뀌어요.</p>
              )}

              {form.repeatFrequency === 'WEEKLY' && (
                <div className={s.dayToggles} role="group" aria-label="반복 요일">
                  {WEEK_ORDER.map((i) => {
                    const day = DOW_ENUM[i];
                    const on = form.repeatDays.includes(day) || day === startDow;
                    const locked = day === startDow;
                    return (
                      <button
                        key={day}
                        type="button"
                        aria-pressed={on}
                        className={`${s.dayToggle} ${on ? s.dayToggleOn : ''}`}
                        onClick={() => !locked && toggleDay(day)}
                        title={locked ? '시작하는 날의 요일은 늘 들어가요' : undefined}
                      >
                        {DOW_LABELS[i]}
                      </button>
                    );
                  })}
                </div>
              )}

              {form.repeatFrequency !== 'NONE' && (
                <div className={s.repeatEnd}>
                  <span className={s.timeLabel}>끝</span>
                  <div className={s.segment} role="radiogroup" aria-label="반복 끝">
                    {REPEAT_ENDS.map(([v, label]) => (
                      <button
                        key={v}
                        type="button"
                        role="radio"
                        aria-checked={form.repeatEnd === v}
                        className={`${s.segmentItem} ${form.repeatEnd === v ? s.segmentItemOn : ''}`}
                        onClick={() => setForm((f) => changeForm(f, 'repeatEnd', v))}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                  {form.repeatEnd === 'until' && (
                    <input type="date" className={`dc-input ${s.repeatEndInput}`} value={form.repeatUntil} min={form.startDate} onChange={set('repeatUntil')} aria-label="반복 끝 날짜" />
                  )}
                  {form.repeatEnd === 'count' && (
                    <label className={s.countInput}>
                      <input type="number" className="dc-input" min={1} max={MAX_REPEAT_COUNT} value={form.repeatCount} onChange={set('repeatCount')} aria-label="반복 횟수" />
                      <span>번</span>
                    </label>
                  )}
                </div>
              )}
            </div>

            <div className={s.field}>
              <span className="dc-label">참여자 <span className={ms.optional}>(선택)</span></span>
              <ParticipantPicker
                value={form.participants}
                onChange={(list) => setForm((f) => ({ ...f, participants: list }))}
                disabled={saving}
              />
              <p className="dc-help">넣은 사람에게 알림이 가고, 그 사람 달력에도 보여요.</p>
            </div>

            {error && (
              <div className={ms.error} role="alert">
                <AlertTriangle size={13} />
                <span>{error}</span>
              </div>
            )}
          </div>

          <div className="dc-modal-footer">
            <button type="button" className="dc-btn" onClick={onClose} disabled={saving}>취소</button>
            <button type="submit" className="dc-btn dc-btn-primary" disabled={saving}>
              {saving ? (<><Loader2 size={15} className={ms.spin} />저장하는 중...</>) : '저장'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ScheduleFormModal;
