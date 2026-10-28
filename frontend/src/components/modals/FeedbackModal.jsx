import React, { useState } from 'react';
import { X, AlertTriangle, Loader2, CheckCircle2, ExternalLink, Globe } from 'lucide-react';
import axiosInstance from '../api/axiosInstance';
import s from './Modals.module.css';
import { FEEDBACK_TYPES, MAX_TITLE, MAX_CONTENT, validateFeedback } from '../../utils/feedbackForm';

// 피드백을 DevChat GitHub 레포의 이슈로 남긴다
const FeedbackModal = ({ onClose }) => {
  const [type, setType] = useState('BUG');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [created, setCreated] = useState(null); // { issueNumber, issueUrl }

  const submit = async (e) => {
    e.preventDefault();
    const problem = validateFeedback({ title, content });
    if (problem) { setError(problem); return; }
    setSending(true);
    setError(null);
    try {
      const res = await axiosInstance.post('/feedback', {
        type,
        title: title.trim(),
        content: content.trim(),
        pagePath: window.location.pathname,
        userAgent: navigator.userAgent,
      });
      setCreated(res.data);
    } catch (err) {
      setError(err.response?.data?.message || '피드백을 보내지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="dc-modal-backdrop" onClick={sending ? undefined : onClose}>
      <div className="dc-modal" style={{ maxWidth: 520 }} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-labelledby="feedback-title">
        <div className="dc-modal-header">
          <h3 className="dc-modal-title" id="feedback-title">피드백 보내기</h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기" disabled={sending}>
            <X size={20} />
          </button>
        </div>

        {created ? (
          <>
            <div className="dc-modal-body">
              <div className={s.feedbackDone}>
                <CheckCircle2 size={36} />
                <p>보내 주셔서 고마워요!</p>
                <span>GitHub 이슈 #{created.issueNumber}로 등록됐어요.</span>
              </div>
            </div>
            <div className="dc-modal-footer">
              {created.issueUrl && (
                <a className="dc-btn" style={{ textDecoration: 'none' }} href={created.issueUrl} target="_blank" rel="noopener noreferrer">
                  <ExternalLink size={15} />
                  이슈 보기
                </a>
              )}
              <button type="button" className="dc-btn dc-btn-primary" onClick={onClose}>닫기</button>
            </div>
          </>
        ) : (
          <form onSubmit={submit} className={s.form}>
            <div className="dc-modal-body">
              <div className={s.field}>
                <span className="dc-label" id="feedback-type-label">종류</span>
                <div className={s.feedbackTypes} role="radiogroup" aria-labelledby="feedback-type-label">
                  {FEEDBACK_TYPES.map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      role="radio"
                      aria-checked={type === value}
                      className={`${s.feedbackType} ${type === value ? s.feedbackTypeActive : ''}`}
                      onClick={() => setType(value)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className={s.field}>
                <label htmlFor="feedback-title-input" className="dc-label">
                  제목 <span className={s.required}>*</span>
                </label>
                <input
                  id="feedback-title-input"
                  className="dc-input"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={MAX_TITLE}
                  autoComplete="off"
                  placeholder={type === 'BUG' ? '예) 휴대폰에서 사진 전송이 안 돼요' : type === 'IDEA' ? '예) 메시지 고정 기능이 있으면 좋겠어요' : '한 줄로 적어 주세요'}
                />
              </div>

              <div className={s.field}>
                <label htmlFor="feedback-content" className="dc-label">
                  내용 <span className={s.required}>*</span>
                </label>
                <textarea
                  id="feedback-content"
                  className={`dc-input ${s.feedbackTextarea}`}
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  maxLength={MAX_CONTENT}
                  rows={6}
                  autoComplete="off"
                  placeholder={type === 'BUG' ? '어떤 화면에서 무엇을 했더니 어떻게 됐는지 적어 주세요.' : '자유롭게 적어 주세요.'}
                />
                <div className={s.feedbackCount}>{content.length.toLocaleString()} / {MAX_CONTENT.toLocaleString()}</div>
              </div>

              <p className={`dc-help ${s.helpRow}`}>
                <Globe size={14} />
                <span>공개 GitHub 이슈로 올라가 누구나 볼 수 있어요. 개인정보·비밀번호는 적지 마세요. 사용 중인 화면 주소와 브라우저 정보가 함께 담겨요.</span>
              </p>

              {error && (
                <div className={s.error} role="alert">
                  <AlertTriangle size={13} />
                  <span>{error}</span>
                </div>
              )}
            </div>

            <div className="dc-modal-footer">
              <button type="button" className="dc-btn" onClick={onClose} disabled={sending}>취소</button>
              <button type="submit" className="dc-btn dc-btn-primary" disabled={sending}>
                {sending ? (<><Loader2 size={15} className={s.spin} />보내는 중...</>) : '보내기'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default FeedbackModal;
