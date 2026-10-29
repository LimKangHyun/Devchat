import React, { useCallback, useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { CalendarPlus, Plus, Repeat } from 'lucide-react';
import { fetchUpcoming } from '../../api/scheduleApi';
import { groupUpcoming, occurrenceRef, shortTime, dateLabel } from '../../utils/scheduleFormat';
import { useScheduleModal } from './ScheduleModalContext';
import CalendarConnectModal from './CalendarConnectModal';
import sb from '../Sidebar.module.css';
import s from './Schedule.module.css';

// 왼쪽 "일정" 탭: 연결·새 일정 버튼과 다가오는 일정
const ScheduleSidebar = () => {
  const { openCreate, openDetail } = useScheduleModal();
  const [items, setItems] = useState(null);
  const [failed, setFailed] = useState(false);
  const [showConnect, setShowConnect] = useState(false);

  const load = useCallback(async () => {
    try {
      setItems(await fetchUpcoming(20));
      setFailed(false);
    } catch (e) {
      setFailed(true);
    }
  }, []);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    window.addEventListener('schedules:changed', load);
    return () => window.removeEventListener('schedules:changed', load);
  }, [load]);

  const groups = groupUpcoming(items || []);

  return (
    <>
      <div className={s.sideActions}>
        <button type="button" className={`dc-btn dc-btn-primary ${s.sideBtn}`} onClick={() => openCreate()}>
          <Plus size={15} />
          새 일정
        </button>
        <button type="button" className={`dc-btn ${s.sideBtn}`} onClick={() => setShowConnect(true)}>
          <CalendarPlus size={15} />
          애플 캘린더에 연결
        </button>
      </div>

      <div className={`${sb.roomsContainer} dc-scroll-dark`}>
        {failed && !items ? (
          <div className={sb.emptyState}>일정을 불러오지 못했어요.</div>
        ) : !items ? (
          <div className={sb.loadingState}>일정 불러오는 중...</div>
        ) : groups.length === 0 ? (
          <div className={sb.emptyState}>다가오는 일정이 없어요.</div>
        ) : groups.map((g) => (
          <section key={g.label} className={s.sideGroup}>
            <h4 className={s.sideGroupTitle}>{g.label}</h4>
            {g.items.map((o) => (
              <button
                type="button"
                key={`${o.scheduleId}-${o.start}`}
                className={s.sideItem}
                onClick={() => openDetail(o.scheduleId, occurrenceRef(o))}
              >
                <span className={s.sideItemTime}>
                  {g.label === '이후' && <span>{dateLabel(o.start)}</span>}
                  <span>{shortTime(o)}</span>
                </span>
                <span className={s.sideItemTitle}>
                  {o.title}
                  {o.recurring && <Repeat size={11} className={s.sideItemIcon} aria-label="반복" />}
                </span>
              </button>
            ))}
          </section>
        ))}
      </div>

      {showConnect && createPortal(<CalendarConnectModal onClose={() => setShowConnect(false)} />, document.body)}
    </>
  );
};

export default ScheduleSidebar;
