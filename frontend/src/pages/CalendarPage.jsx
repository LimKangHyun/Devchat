import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Plus, CalendarPlus, Repeat, Loader2 } from 'lucide-react';
import { fetchOccurrences } from '../api/scheduleApi';
import { monthGrid, weekDays, dayKey, kstParts, kstDate } from '../utils/calendarGrid';
import {
  DOW_LABELS, toLocalIso, occurrencesByDay, occurrenceRef, shortTime, timeLabel, parseDayKey, dateLabel,
} from '../utils/scheduleFormat';
import { useScheduleModal } from '../components/schedule/ScheduleModalContext';
import CalendarConnectModal from '../components/schedule/CalendarConnectModal';
import useIsMobile from '../hooks/useIsMobile';
import styles from './CalendarPage.module.css';

const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_PER_CELL = 3;

const startOfDay = (date) => {
  const t = kstParts(date);
  return kstDate(t.y, t.m, t.d);
};

const CalendarPage = () => {
  const isMobile = useIsMobile();
  const { openCreate, openDetail } = useScheduleModal();
  const [searchParams, setSearchParams] = useSearchParams();

  const [view, setView] = useState('month'); // month | week
  const [cursor, setCursor] = useState(() => startOfDay(new Date()));
  const [selected, setSelected] = useState(() => dayKey(new Date()));
  const [occurrences, setOccurrences] = useState([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [showConnect, setShowConnect] = useState(false);

  const mode = isMobile ? 'month' : view;
  const { y, m } = kstParts(cursor);

  const grid = useMemo(() => monthGrid(y, m), [y, m]);
  const week = useMemo(() => weekDays(cursor), [cursor]);
  const [from, to] = mode === 'month'
    ? [grid[0][0], new Date(grid[5][6].getTime() + DAY_MS)]
    : [week[0], new Date(week[6].getTime() + DAY_MS)];
  const fromIso = toLocalIso(from);
  const toIso = toLocalIso(to);

  // 마지막 요청만 반영한다 (달을 빨리 넘기면 앞 요청이 늦게 올 수 있다)
  const requestSeq = useRef(0);
  const load = useCallback(async () => {
    const seq = ++requestSeq.current;
    setLoading(true);
    try {
      const data = await fetchOccurrences(fromIso, toIso);
      if (seq !== requestSeq.current) return;
      setOccurrences(data);
      setFailed(false);
    } catch (e) {
      if (seq === requestSeq.current) setFailed(true);
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  }, [fromIso, toIso]);

  useEffect(() => {
    load();
    // 화면을 떠나면 늦게 온 응답은 버린다
    return () => { requestSeq.current += 1; };
  }, [load]);
  useEffect(() => {
    window.addEventListener('schedules:changed', load);
    return () => window.removeEventListener('schedules:changed', load);
  }, [load]);

  // 알림·푸시에서 /calendar?schedule={id} 로 들어오면 자세히 창을 연다
  const scheduleParam = searchParams.get('schedule');
  useEffect(() => {
    if (!scheduleParam) return;
    const id = Number(scheduleParam);
    if (Number.isFinite(id) && id > 0) openDetail(id);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('schedule');
      return next;
    }, { replace: true });
  }, [scheduleParam, openDetail, setSearchParams]);

  const fromTime = from.getTime();
  const toTime = to.getTime();
  const byDay = useMemo(
    () => occurrencesByDay(occurrences, { from: new Date(fromTime), to: new Date(toTime) }),
    [occurrences, fromTime, toTime],
  );
  const todayKey = dayKey(new Date());

  const move = (step) => {
    if (mode === 'month') {
      const next = kstDate(y, m + step, 1);
      setCursor(next);
      if (isMobile) setSelected(dayKey(next));
    } else {
      setCursor(new Date(cursor.getTime() + step * 7 * DAY_MS));
    }
  };
  const goToday = () => {
    setCursor(startOfDay(new Date()));
    setSelected(dayKey(new Date()));
  };

  const createOn = (day) => openCreate({ start: day, end: day, allDay: true });
  // role=button 칸: Enter와 Space로 연다
  const onCellKey = (e, day) => {
    if (e.target !== e.currentTarget) return;
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      createOn(day);
    }
  };
  const showDetail = (e, o) => {
    e.stopPropagation();
    openDetail(o.scheduleId, occurrenceRef(o));
  };

  const renderChip = (o, day) => {
    // 여러 날 일정의 다음 날 칸에는 시각을 빼고 보여 준다
    const continued = occurrenceRef(o).date !== dayKey(day);
    return (
      <button
        type="button"
        key={`${o.scheduleId}-${o.start}`}
        className={`${styles.chip} ${o.allDay ? styles.chipAllDay : ''} ${o.mine ? '' : styles.chipShared}`}
        onClick={(e) => showDetail(e, o)}
        title={o.title}
      >
        {!o.allDay && !continued && <span className={styles.chipTime}>{timeLabel(o.start)}</span>}
        <span className={styles.chipTitle}>{o.title}</span>
        {o.recurring && <Repeat size={10} className={styles.chipIcon} aria-label="반복" />}
      </button>
    );
  };

  const renderMonth = () => (
    <div className={styles.month}>
      <div className={styles.dowRow}>
        {DOW_LABELS.map((d, i) => (
          <div key={d} className={`${styles.dow} ${i === 0 ? styles.sun : ''} ${i === 6 ? styles.sat : ''}`}>{d}</div>
        ))}
      </div>
      <div className={styles.monthGrid}>
        {grid.flat().map((day, i) => {
          const key = dayKey(day);
          const t = kstParts(day);
          const list = byDay.get(key) || [];
          const extra = list.length - MAX_PER_CELL;
          return (
            <div
              key={key}
              role="button"
              tabIndex={0}
              className={`${styles.cell} ${t.m !== m ? styles.cellOut : ''} ${key === todayKey ? styles.cellToday : ''}`}
              onClick={() => createOn(day)}
              onKeyDown={(e) => onCellKey(e, day)}
              aria-label={`${dateLabel(day)} 일정 만들기`}
            >
              <span className={`${styles.dayNum} ${i % 7 === 0 ? styles.sun : ''} ${i % 7 === 6 ? styles.sat : ''}`}>{t.d}</span>
              <div className={styles.chips}>
                {list.slice(0, MAX_PER_CELL).map((o) => renderChip(o, day))}
                {extra > 0 && (
                  <button
                    type="button"
                    className={styles.more}
                    onClick={(e) => { e.stopPropagation(); setCursor(day); setView('week'); }}
                  >
                    +{extra}
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  const renderWeek = () => (
    <div className={styles.week}>
      {week.map((day) => {
        const key = dayKey(day);
        const t = kstParts(day);
        const list = byDay.get(key) || [];
        const allDay = list.filter((o) => o.allDay);
        const timed = list.filter((o) => !o.allDay);
        return (
          <div
            key={key}
            role="button"
            tabIndex={0}
            className={`${styles.weekCol} ${key === todayKey ? styles.weekColToday : ''}`}
            onClick={() => createOn(day)}
            onKeyDown={(e) => onCellKey(e, day)}
            aria-label={`${dateLabel(day)} 일정 만들기`}
          >
            <div className={styles.weekHead}>
              <span className={`${t.dow === 0 ? styles.sun : ''} ${t.dow === 6 ? styles.sat : ''}`}>{DOW_LABELS[t.dow]}</span>
              <strong>{t.d}</strong>
            </div>
            <div className={styles.weekAllDay}>
              {allDay.map((o) => renderChip(o, day))}
            </div>
            <div className={styles.weekTimed}>
              {timed.map((o) => renderChip(o, day))}
            </div>
          </div>
        );
      })}
    </div>
  );

  const renderMobile = () => {
    const list = byDay.get(selected) || [];
    const selectedDay = parseDayKey(selected);
    return (
      <>
        <div className={styles.miniMonth}>
          <div className={styles.dowRow}>
            {DOW_LABELS.map((d, i) => (
              <div key={d} className={`${styles.dow} ${i === 0 ? styles.sun : ''} ${i === 6 ? styles.sat : ''}`}>{d}</div>
            ))}
          </div>
          <div className={styles.miniGrid}>
            {grid.flat().map((day, i) => {
              const key = dayKey(day);
              const t = kstParts(day);
              const count = (byDay.get(key) || []).length;
              return (
                <button
                  type="button"
                  key={key}
                  aria-pressed={key === selected}
                  className={`${styles.miniCell} ${t.m !== m ? styles.cellOut : ''} ${key === todayKey ? styles.miniToday : ''} ${key === selected ? styles.miniSelected : ''}`}
                  onClick={() => setSelected(key)}
                  aria-label={`${dateLabel(day)}${count ? `, 일정 ${count}개` : ''}`}
                >
                  <span className={`${i % 7 === 0 ? styles.sun : ''} ${i % 7 === 6 ? styles.sat : ''}`}>{t.d}</span>
                  <span className={styles.dots}>
                    {Array.from({ length: Math.min(count, 3) }, (_, k) => <i key={k} />)}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        <section className={styles.dayList}>
          <div className={styles.dayListHead}>
            <h3>{dateLabel(selectedDay)}</h3>
            <button type="button" className="dc-btn" onClick={() => createOn(selectedDay)}>
              <Plus size={15} />
              일정
            </button>
          </div>
          {list.length === 0 ? (
            <p className={styles.dayEmpty}>일정이 없어요.</p>
          ) : (
            <ul>
              {list.map((o) => (
                <li key={`${o.scheduleId}-${o.start}`}>
                  <button type="button" className={styles.dayItem} onClick={(e) => showDetail(e, o)}>
                    <span className={`${styles.dayItemBar} ${o.mine ? '' : styles.chipShared}`} />
                    <span className={styles.dayItemTime}>{shortTime(o)}</span>
                    <span className={styles.dayItemTitle}>{o.title}</span>
                    {o.recurring && <Repeat size={12} className={styles.chipIcon} aria-label="반복" />}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </>
    );
  };

  return (
    <div className={styles.page}>
      <div className={styles.toolbar}>
        <div className={styles.nav}>
          <button type="button" className="dc-icon-btn" onClick={() => move(-1)} aria-label={mode === 'month' ? '이전 달' : '이전 주'}>
            <ChevronLeft size={18} />
          </button>
          <button type="button" className="dc-btn" onClick={goToday}>오늘</button>
          <button type="button" className="dc-icon-btn" onClick={() => move(1)} aria-label={mode === 'month' ? '다음 달' : '다음 주'}>
            <ChevronRight size={18} />
          </button>
        </div>
        <h2 className={styles.title}>{`${y}년 ${m}월`}</h2>
        {loading && <Loader2 size={16} className={styles.spin} aria-label="불러오는 중" />}
        <div className={styles.toolbarRight}>
          {isMobile ? (
            <>
              <button type="button" className="dc-icon-btn" onClick={() => setShowConnect(true)} aria-label="애플 캘린더에 연결">
                <CalendarPlus size={18} />
              </button>
              <button type="button" className="dc-icon-btn" onClick={() => openCreate()} aria-label="새 일정">
                <Plus size={18} />
              </button>
            </>
          ) : (
            <div className={styles.segment} role="radiogroup" aria-label="보기">
              {[['month', '월'], ['week', '주']].map(([v, label]) => (
                <button
                  key={v}
                  type="button"
                  role="radio"
                  aria-checked={view === v}
                  className={`${styles.segmentItem} ${view === v ? styles.segmentItemOn : ''}`}
                  onClick={() => setView(v)}
                >
                  {label}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {failed && <p className={styles.error} role="alert">일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>}

      {isMobile ? renderMobile() : mode === 'month' ? renderMonth() : renderWeek()}

      {showConnect && createPortal(<CalendarConnectModal onClose={() => setShowConnect(false)} />, document.body)}
    </div>
  );
};

export default CalendarPage;
