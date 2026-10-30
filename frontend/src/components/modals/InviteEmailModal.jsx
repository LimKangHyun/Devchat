import React, { useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { X, Mail, AlertTriangle, Loader2, Check } from 'lucide-react';
import s from './Modals.module.css';
import useBackClose from '../../hooks/useBackClose';

const MAX_EMAILS = 10;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// 입력값을 쉼표·공백·줄바꿈 기준으로 나눈다. 붙여넣기로 여러 주소를 한 번에 넣을 수 있다.
const splitEmails = (text) => text.split(/[\s,;]+/).map((e) => e.trim()).filter(Boolean);

const InviteEmailModal = ({ roomId, roomName, onClose }) => {
  useBackClose(true, onClose);
  const [emails, setEmails] = useState([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState(null);
  const [sending, setSending] = useState(false);
  const [sentCount, setSentCount] = useState(null);

  const addEmails = (text) => {
    const parts = splitEmails(text);
    if (parts.length === 0) return true;
    const invalid = parts.find((e) => !EMAIL_RE.test(e));
    if (invalid) {
      setError(`이메일 형식이 올바르지 않습니다: ${invalid}`);
      return false;
    }
    const next = [...new Set([...emails, ...parts.map((e) => e.toLowerCase())])];
    if (next.length > MAX_EMAILS) {
      setError(`한 번에 최대 ${MAX_EMAILS}명까지 초대할 수 있습니다.`);
      return false;
    }
    setEmails(next);
    setError(null);
    return true;
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ',' || e.key === ' ') {
      e.preventDefault();
      if (addEmails(draft)) setDraft('');
    } else if (e.key === 'Backspace' && !draft && emails.length) {
      setEmails(emails.slice(0, -1));
    }
  };

  const handlePaste = (e) => {
    const text = e.clipboardData.getData('text');
    if (/[\s,;]/.test(text)) {
      e.preventDefault();
      if (addEmails(draft + text)) setDraft('');
    }
  };

  const removeEmail = (target) => setEmails(emails.filter((e) => e !== target));

  const handleSubmit = async (e) => {
    e.preventDefault();
    // 입력 중인 주소도 함께 보낸다.
    let list = emails;
    if (draft.trim()) {
      const parts = splitEmails(draft);
      if (parts.some((p) => !EMAIL_RE.test(p))) {
        setError('이메일 형식을 확인해주세요.');
        return;
      }
      list = [...new Set([...emails, ...parts.map((p) => p.toLowerCase())])];
    }
    if (list.length === 0) {
      setError('초대할 이메일을 입력해주세요.');
      return;
    }
    if (list.length > MAX_EMAILS) {
      setError(`한 번에 최대 ${MAX_EMAILS}명까지 초대할 수 있습니다.`);
      return;
    }

    setSending(true);
    setError(null);
    try {
      const res = await axiosInstance.post(`/chat-rooms/${roomId}/invites/email`, { emails: list });
      setSentCount(res.data?.requested ?? list.length);
    } catch (err) {
      setError(err.response?.data?.message || '초대 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="dc-modal-backdrop" onClick={sending ? undefined : onClose}>
      <div className="dc-modal" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="dc-modal-header">
          <h3 className="dc-modal-title">이메일로 초대</h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} disabled={sending} aria-label="닫기">
            <X size={20} />
          </button>
        </div>

        {sentCount !== null ? (
          <>
            <div className="dc-modal-body">
              <p className={s.lead} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Check size={18} /> {sentCount}명에게 초대 메일을 보냈어요.
              </p>
              <p style={{ margin: 0, color: 'var(--text-3)', fontSize: 14 }}>
                메일의 "채팅방 입장하기" 버튼을 누르면 로그인 후 바로 <b>{roomName}</b>에 들어올 수 있어요.
                메일이 안 보이면 스팸함을 확인해 달라고 알려주세요.
              </p>
            </div>
            <div className="dc-modal-footer">
              <button type="button" className="dc-btn" onClick={() => { setSentCount(null); setEmails([]); }}>
                더 초대하기
              </button>
              <button type="button" className="dc-btn dc-btn-primary" onClick={onClose}>닫기</button>
            </div>
          </>
        ) : (
          <form onSubmit={handleSubmit} className={s.form}>
            <div className="dc-modal-body">
              <p className={s.lead}><b>{roomName}</b> 입장 링크를 이메일로 보내요.</p>
              <div className={s.field}>
                <label htmlFor="inviteEmail" className="dc-label">
                  받는 사람 <span className={s.required}>*</span>
                </label>
                <div className={`${s.chipInput} ${error ? s.inputGroupError : ''}`}>
                  <Mail size={16} className={s.chipInputIcon} />
                  {emails.map((email) => (
                    <span key={email} className={s.chip}>
                      {email}
                      <button type="button" onClick={() => removeEmail(email)} aria-label={`${email} 빼기`}>
                        <X size={12} />
                      </button>
                    </span>
                  ))}
                  <input
                    id="inviteEmail"
                    type="email"
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    onKeyDown={handleKeyDown}
                    onPaste={handlePaste}
                    onBlur={() => { if (draft.trim() && addEmails(draft)) setDraft(''); }}
                    placeholder={emails.length ? '' : 'friend@example.com'}
                    autoFocus
                  />
                </div>
                {error ? (
                  <div className={s.error}>
                    <AlertTriangle size={13} />
                    <span>{error}</span>
                  </div>
                ) : (
                  <div style={{ marginTop: 6, fontSize: 13, color: 'var(--text-3)' }}>
                    Enter나 쉼표로 여러 명을 넣을 수 있어요. (최대 {MAX_EMAILS}명)
                  </div>
                )}
              </div>
            </div>
            <div className="dc-modal-footer">
              <button type="button" className="dc-btn" onClick={onClose} disabled={sending}>취소</button>
              <button type="submit" className="dc-btn dc-btn-primary" disabled={sending}>
                {sending ? (<><Loader2 size={15} className={s.spin} />보내는 중...</>) : '초대 메일 보내기'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default InviteEmailModal;
