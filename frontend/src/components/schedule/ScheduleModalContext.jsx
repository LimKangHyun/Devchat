import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import ScheduleFormModal from './ScheduleFormModal';
import ScheduleDetailModal from './ScheduleDetailModal';
import Toast from '../common/Toast';

const ScheduleModalContext = createContext(null);

const noop = () => {};
const FALLBACK = { openCreate: noop, openDetail: noop };

// 어디서든 일정 만들기·자세히 창을 연다
export const useScheduleModal = () => useContext(ScheduleModalContext) || FALLBACK;

export const ScheduleModalProvider = ({ children }) => {
  // { kind: 'create', prefill } | { kind: 'edit', schedule } | { kind: 'detail', id, occurrence }
  const [modal, setModal] = useState(null);
  const [toast, setToast] = useState(null);
  const toastTimer = useRef(null);

  useEffect(() => () => clearTimeout(toastTimer.current), []);

  const showToast = useCallback((message) => {
    setToast(message);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 2500);
  }, []);

  // prefill: { title?, start?, end?, allDay? }
  const openCreate = useCallback((prefill) => setModal({ kind: 'create', prefill: prefill || {} }), []);
  // occurrence: { date: 'YYYY-MM-DD', start, end } — 누른 회차. 반복 일정의 "이번만 빼기"에 쓴다
  const openDetail = useCallback((scheduleId, occurrence) => {
    if (!scheduleId) return;
    setModal({ kind: 'detail', id: scheduleId, occurrence: occurrence?.date ? occurrence : null });
  }, []);

  const value = useMemo(() => ({ openCreate, openDetail }), [openCreate, openDetail]);
  const close = () => setModal(null);

  let content = null;
  if (modal?.kind === 'create') {
    content = (
      <ScheduleFormModal
        prefill={modal.prefill}
        onClose={close}
        onSaved={() => { showToast('일정을 만들었어요'); close(); }}
      />
    );
  } else if (modal?.kind === 'edit') {
    content = (
      <ScheduleFormModal
        schedule={modal.schedule}
        onClose={() => setModal({ kind: 'detail', id: modal.schedule.id, occurrence: modal.occurrence })}
        onSaved={() => { showToast('일정을 고쳤어요'); close(); }}
      />
    );
  } else if (modal?.kind === 'detail') {
    content = (
      <ScheduleDetailModal
        key={modal.id}
        scheduleId={modal.id}
        occurrence={modal.occurrence}
        onClose={close}
        onEdit={(schedule) => setModal({ kind: 'edit', schedule, occurrence: modal.occurrence })}
        onDone={(message) => { close(); if (message) showToast(message); }}
      />
    );
  }

  return (
    <ScheduleModalContext.Provider value={value}>
      {children}
      {content && createPortal(content, document.body)}
      {toast && createPortal(<Toast open message={toast} />, document.body)}
    </ScheduleModalContext.Provider>
  );
};

export default ScheduleModalContext;
