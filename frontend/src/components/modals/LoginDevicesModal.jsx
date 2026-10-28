import React, { useState, useEffect, useCallback, useRef } from 'react';
import { X, MonitorSmartphone, Loader2 } from 'lucide-react';
import axiosInstance from '../api/axiosInstance';
import Toast from '../common/Toast';
import s from './Modals.module.css';
import { relativeTime, parseServerTime } from '../../utils/relativeTime';

const formatDate = (value) => {
  const d = parseServerTime(value);
  // 한국 시간 기준 날짜
  const k = new Date(d.getTime() + 9 * 60 * 60 * 1000);
  return `${k.getUTCFullYear()}.${k.getUTCMonth() + 1}.${k.getUTCDate()}`;
};

// 로그인된 기기 목록을 보고, 다른 기기를 로그아웃한다
const LoginDevicesModal = ({ onClose }) => {
  const [sessions, setSessions] = useState(null);
  const [error, setError] = useState(null);
  const [confirm, setConfirm] = useState(null); // { id, name } 또는 { all: true }
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState(false);
  const toastTimer = useRef(null);

  const load = useCallback(async () => {
    try {
      const res = await axiosInstance.get('/sessions');
      setSessions(res.data);
      setError(null);
    } catch (err) {
      setError('기기 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
  }, []);

  useEffect(() => { load(); }, [load]);
  useEffect(() => () => clearTimeout(toastTimer.current), []);

  const run = async () => {
    if (!confirm) return;
    setBusy(true);
    try {
      if (confirm.all) await axiosInstance.post('/sessions/logout-others');
      else await axiosInstance.delete(`/sessions/${confirm.id}`);
      setConfirm(null);
      await load();
      setToast(true);
      clearTimeout(toastTimer.current);
      toastTimer.current = setTimeout(() => setToast(false), 2500);
    } catch (err) {
      setConfirm(null);
      setError('로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요.');
      load();
    } finally {
      setBusy(false);
    }
  };

  const hasOthers = !!sessions && sessions.some((x) => !x.current);

  return (
    <div className="dc-modal-backdrop" onClick={busy ? undefined : onClose}>
      <div className="dc-modal" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-labelledby="devices-title">
        <div className="dc-modal-header">
          <h3 className="dc-modal-title" id="devices-title">로그인 기기</h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기" disabled={busy}>
            <X size={20} />
          </button>
        </div>

        <div className="dc-modal-body">
          {error && <p className={s.deviceError} role="alert">{error}</p>}
          {!sessions && !error && (
            <div className={s.deviceLoading}><Loader2 size={20} className={s.deviceSpin} /></div>
          )}

          {confirm ? (
            <div className={s.deviceConfirm}>
              <p>{confirm.all ? '다른 기기에서 모두 로그아웃할까요?' : `${confirm.name}에서 로그아웃할까요?`}</p>
              <div className={s.deviceConfirmActions}>
                <button type="button" className="dc-btn" onClick={() => setConfirm(null)} disabled={busy}>취소</button>
                <button type="button" className="dc-btn dc-btn-primary" onClick={run} disabled={busy}>
                  {busy ? <Loader2 size={15} className={s.deviceSpin} /> : '확인'}
                </button>
              </div>
            </div>
          ) : (
            sessions && (
              <ul className={s.deviceList}>
                {sessions.map((x) => (
                  <li key={x.id} className={s.deviceRow}>
                    <MonitorSmartphone size={20} className={s.deviceIcon} />
                    <div className={s.deviceInfo}>
                      <div className={s.deviceName}>
                        <span>{x.deviceName}</span>
                        {x.current && <span className={s.deviceBadge}>이 기기</span>}
                      </div>
                      <div className={s.deviceMeta}>마지막 사용 · {relativeTime(x.lastUsedAt)}</div>
                      <div className={s.deviceMeta}>처음 로그인 · {formatDate(x.createdAt)}</div>
                    </div>
                    {!x.current && (
                      <button type="button" className="dc-btn" onClick={() => setConfirm({ id: x.id, name: x.deviceName })}>
                        로그아웃
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )
          )}
        </div>

        {!confirm && hasOthers && (
          <div className="dc-modal-footer">
            <button type="button" className="dc-btn" onClick={() => setConfirm({ all: true })}>
              다른 기기 모두 로그아웃
            </button>
          </div>
        )}
      </div>
      <Toast open={toast} message="로그아웃했어요" />
    </div>
  );
};

export default LoginDevicesModal;
