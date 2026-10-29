import React, { useEffect, useState } from 'react';
import { X, Loader2, Clock, Repeat, MapPin, AlignLeft, Bell, User, Users, AlertTriangle } from 'lucide-react';
import {
  fetchSchedule, deleteSchedule, excludeOccurrence, leaveSchedule, errorMessage, notifySchedulesChanged,
} from '../../api/scheduleApi';
import { rangeLabel, repeatLabel, dateLabel, parseDayKey } from '../../utils/scheduleFormat';
import { reminderLabel } from '../../utils/reminderOptions';
import Avatar from './Avatar';
import ms from '../modals/Modals.module.css';
import s from './Schedule.module.css';

// 일정 자세히. occurrence: 달력에서 누른 회차 { date: 'YYYY-MM-DD', start, end } (없을 수 있다)
const ScheduleDetailModal = ({ scheduleId, occurrence, onClose, onEdit, onDone }) => {
  const occurrenceDate = occurrence?.date || null;
  const [schedule, setSchedule] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [step, setStep] = useState('view'); // view | delete | leave
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    fetchSchedule(scheduleId)
      .then((data) => { if (alive) setSchedule(data); })
      .catch((err) => {
        if (!alive) return;
        setLoadError(err?.response?.status === 404
          ? '일정을 찾을 수 없어요. 지워졌거나 참여자에서 빠졌을 수 있어요.'
          : errorMessage(err, '일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'));
      });
    return () => { alive = false; };
  }, [scheduleId]);

  const recurring = schedule && schedule.repeatFrequency && schedule.repeatFrequency !== 'NONE';

  const run = async (action, message) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      notifySchedulesChanged();
      onDone?.(message);
    } catch (err) {
      setError(errorMessage(err, '처리하지 못했어요. 잠시 후 다시 시도해 주세요.'));
      setBusy(false);
    }
  };

  const removeAll = () => run(() => deleteSchedule(scheduleId), '일정을 지웠어요');
  const removeOnce = () => run(() => excludeOccurrence(scheduleId, occurrenceDate), '이번 일정만 뺐어요');
  const leave = () => run(() => leaveSchedule(scheduleId), '일정에서 나갔어요');

  const back = () => { setStep('view'); setError(null); };

  const renderConfirm = () => {
    if (step === 'delete') {
      return (
        <div className={s.confirm}>
          {recurring ? (
            <>
              <p>반복 일정이에요. 어떻게 지울까요?</p>
              <div className={s.confirmChoices}>
                {occurrenceDate && (
                  <button type="button" className="dc-btn" onClick={removeOnce} disabled={busy}>
                    이번만 빼기 <span className={s.muted}>({dateLabel(parseDayKey(occurrenceDate))})</span>
                  </button>
                )}
                <button type="button" className="dc-btn dc-btn-danger" onClick={removeAll} disabled={busy}>전체 지우기</button>
              </div>
              {!occurrenceDate && <p className="dc-help">이번만 빼려면 달력에서 그 날의 일정을 눌러 주세요.</p>}
            </>
          ) : (
            <>
              <p>이 일정을 지울까요? 참여자에게도 취소 알림이 가요.</p>
              <div className={s.confirmChoices}>
                <button type="button" className="dc-btn dc-btn-danger" onClick={removeAll} disabled={busy}>지우기</button>
              </div>
            </>
          )}
        </div>
      );
    }
    return (
      <div className={s.confirm}>
        <p>이 일정에서 나갈까요? 내 달력에서 사라지고 알림도 오지 않아요.</p>
        <div className={s.confirmChoices}>
          <button type="button" className="dc-btn dc-btn-danger" onClick={leave} disabled={busy}>나가기</button>
        </div>
      </div>
    );
  };

  const renderBody = () => {
    if (loadError) return <p className={s.loadError} role="alert">{loadError}</p>;
    if (!schedule) return <div className={s.loading}><Loader2 size={20} className={ms.spin} /></div>;
    const repeatText = repeatLabel(schedule);
    return (
      <>
        <dl className={s.detailList}>
          <div className={s.detailRow}>
            <dt><Clock size={16} aria-label="시간" /></dt>
            <dd>{rangeLabel({ start: occurrence?.start || schedule.startAt, end: occurrence?.end || schedule.endAt, allDay: schedule.allDay })}</dd>
          </div>
          {repeatText && (
            <div className={s.detailRow}>
              <dt><Repeat size={16} aria-label="반복" /></dt>
              <dd>{repeatText}</dd>
            </div>
          )}
          {schedule.location && (
            <div className={s.detailRow}>
              <dt><MapPin size={16} aria-label="장소" /></dt>
              <dd>{schedule.location}</dd>
            </div>
          )}
          {schedule.memo && (
            <div className={s.detailRow}>
              <dt><AlignLeft size={16} aria-label="메모" /></dt>
              <dd className={s.memo}>{schedule.memo}</dd>
            </div>
          )}
          <div className={s.detailRow}>
            <dt><Bell size={16} aria-label="알림" /></dt>
            <dd>
              {(schedule.reminderMinutes || []).length === 0
                ? <span className={s.muted}>알림 없음</span>
                : schedule.reminderMinutes.map((m) => reminderLabel(m, schedule.allDay)).join(', ')}
            </dd>
          </div>
          <div className={s.detailRow}>
            <dt><User size={16} aria-label="만든 사람" /></dt>
            <dd className={s.personLine}>
              <Avatar name={schedule.owner?.nickname} img={schedule.owner?.profileImg} size={22} />
              <span>{schedule.owner?.nickname}</span>
              <span className={s.muted}>만든 사람</span>
            </dd>
          </div>
          {(schedule.participants || []).length > 0 && (
            <div className={s.detailRow}>
              <dt><Users size={16} aria-label="참여자" /></dt>
              <dd>
                <ul className={s.avatarList}>
                  {schedule.participants.map((p) => (
                    <li key={p.id} title={p.nickname}>
                      <Avatar name={p.nickname} img={p.profileImg} size={22} />
                      <span>{p.nickname}</span>
                    </li>
                  ))}
                </ul>
              </dd>
            </div>
          )}
        </dl>
        {step !== 'view' && renderConfirm()}
      </>
    );
  };

  const renderFooter = () => {
    if (!schedule) return null;
    if (step !== 'view') {
      return <button type="button" className="dc-btn" onClick={back} disabled={busy}>취소</button>;
    }
    if (schedule.mine) {
      return (
        <>
          <button type="button" className="dc-btn" onClick={() => setStep('delete')}>지우기</button>
          <button type="button" className="dc-btn dc-btn-primary" onClick={() => onEdit?.(schedule)}>고치기</button>
        </>
      );
    }
    return <button type="button" className="dc-btn" onClick={() => setStep('leave')}>이 일정에서 나가기</button>;
  };

  return (
    <div className="dc-modal-backdrop" onClick={busy ? undefined : onClose}>
      <div className="dc-modal" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-labelledby="schedule-detail-title">
        <div className="dc-modal-header">
          <h3 className="dc-modal-title" id="schedule-detail-title">{schedule?.title || '일정'}</h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기" disabled={busy}>
            <X size={20} />
          </button>
        </div>
        <div className="dc-modal-body">
          {renderBody()}
          {error && (
            <div className={ms.error} role="alert">
              <AlertTriangle size={13} />
              <span>{error}</span>
            </div>
          )}
        </div>
        {schedule && <div className="dc-modal-footer">{renderFooter()}</div>}
      </div>
    </div>
  );
};

export default ScheduleDetailModal;
