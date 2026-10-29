import React, { useEffect, useRef, useState } from 'react';
import { X, Loader2, CalendarPlus, Copy, RefreshCw, Check, AlertTriangle } from 'lucide-react';
import { fetchFeedUrl, regenerateFeedUrl, errorMessage } from '../../api/scheduleApi';
import ms from '../modals/Modals.module.css';
import s from './Schedule.module.css';

// 애플 캘린더 구독 주소를 보여 주고 복사·새로 만들기를 한다
const CalendarConnectModal = ({ onClose }) => {
  const [urls, setUrls] = useState(null); // { httpsUrl, webcalUrl }
  const [error, setError] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const copiedTimer = useRef(null);

  useEffect(() => {
    let alive = true;
    fetchFeedUrl()
      .then((data) => { if (alive) setUrls(data); })
      .catch((err) => { if (alive) setError(errorMessage(err, '주소를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')); });
    return () => { alive = false; clearTimeout(copiedTimer.current); };
  }, []);

  const copy = async () => {
    if (!urls) return;
    try {
      await navigator.clipboard.writeText(urls.httpsUrl);
      setCopied(true);
      clearTimeout(copiedTimer.current);
      copiedTimer.current = setTimeout(() => setCopied(false), 2000);
    } catch (e) {
      setError('복사하지 못했어요. 주소를 길게 눌러 직접 복사해 주세요.');
    }
  };

  const regenerate = async () => {
    setBusy(true);
    setError(null);
    try {
      setUrls(await regenerateFeedUrl());
      setConfirm(false);
    } catch (err) {
      setError(errorMessage(err, '주소를 새로 만들지 못했어요. 잠시 후 다시 시도해 주세요.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="dc-modal-backdrop" onClick={busy ? undefined : onClose}>
      <div className="dc-modal" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-labelledby="calendar-connect-title">
        <div className="dc-modal-header">
          <h3 className="dc-modal-title" id="calendar-connect-title">애플 캘린더에 연결</h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기" disabled={busy}>
            <X size={20} />
          </button>
        </div>

        <div className="dc-modal-body">
          <p className={ms.lead}>애플 캘린더에 'DevChat' 달력이 생겨요. 바뀐 내용은 몇 분에서 한 시간 안에 반영돼요. 알림은 DevChat이 보내요.</p>

          {!urls && !error && <div className={s.loading}><Loader2 size={20} className={ms.spin} /></div>}

          {urls && (
            <>
              <a className={`dc-btn dc-btn-primary ${s.wideBtn}`} href={urls.webcalUrl}>
                <CalendarPlus size={16} />
                애플 캘린더에 추가
              </a>
              <div className={s.feedUrl}>
                <input className="dc-input" value={urls.httpsUrl} readOnly onFocus={(e) => e.target.select()} aria-label="구독 주소" />
                <button type="button" className="dc-btn" onClick={copy}>
                  {copied ? <Check size={15} /> : <Copy size={15} />}
                  {copied ? '복사했어요' : '주소 복사'}
                </button>
              </div>
              <p className="dc-help">이 주소를 아는 사람은 내 일정을 볼 수 있어요. 다른 사람에게 알려 주지 마세요.</p>
            </>
          )}

          {confirm && (
            <div className={s.confirm}>
              <p>주소를 새로 만들까요? 예전 주소로 구독한 달력은 더 이상 바뀐 내용을 받지 못해요.</p>
              <div className={s.confirmChoices}>
                <button type="button" className="dc-btn" onClick={() => setConfirm(false)} disabled={busy}>취소</button>
                <button type="button" className="dc-btn dc-btn-danger" onClick={regenerate} disabled={busy}>
                  {busy ? <Loader2 size={15} className={ms.spin} /> : '새로 만들기'}
                </button>
              </div>
            </div>
          )}

          {error && (
            <div className={ms.error} role="alert">
              <AlertTriangle size={13} />
              <span>{error}</span>
            </div>
          )}
        </div>

        {urls && !confirm && (
          <div className="dc-modal-footer">
            <button type="button" className="dc-btn" onClick={() => setConfirm(true)}>
              <RefreshCw size={15} />
              주소 새로 만들기
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default CalendarConnectModal;
