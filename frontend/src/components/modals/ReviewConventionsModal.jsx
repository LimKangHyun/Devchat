import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';
import { X, AlertTriangle, Loader2, Check, Lock } from 'lucide-react';
import s from './Modals.module.css';
import useBackClose from '../../hooks/useBackClose';

const MAX_LENGTH = 2000;

const PLACEHOLDER = `예)
- DTO는 class 대신 record로 작성한다.
- Controller에서 Repository를 직접 호출하지 않는다.
- API 경로는 kebab-case를 사용한다.`;

/**
 * 채팅방(연결된 저장소)의 팀 리뷰 규칙. AI 리뷰가 이 규칙을 우선해서 리뷰한다.
 * 방장만 수정할 수 있고, 다른 참여자는 읽기만 한다.
 */
const ReviewConventionsModal = ({ roomId, isOwner, onClose }) => {
  useBackClose(true, onClose);
  const [conventions, setConventions] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let cancelled = false;
    axiosInstance.get(`/chat-rooms/${roomId}/review-conventions`)
      .then((res) => { if (!cancelled) setConventions(res.data?.conventions ?? ''); })
      .catch((err) => { if (!cancelled) setError(err.response?.data?.message || '팀 리뷰 규칙을 불러오지 못했습니다.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [roomId]);

  const tooLong = conventions.length > MAX_LENGTH;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (tooLong) return;
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const res = await axiosInstance.put(`/chat-rooms/${roomId}/review-conventions`, { conventions });
      setConventions(res.data?.conventions ?? '');
      setSaved(true);
    } catch (err) {
      setError(err.response?.data?.message || '팀 리뷰 규칙을 저장하지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="dc-modal-backdrop" onClick={saving ? undefined : onClose}>
      <div className="dc-modal" style={{ maxWidth: 560 }} onClick={(e) => e.stopPropagation()}>
        <div className="dc-modal-header">
          <h3 className="dc-modal-title">팀 리뷰 규칙</h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} disabled={saving} aria-label="닫기">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className={s.form}>
          <div className="dc-modal-body">
            <p className={s.lead}>
              AI 리뷰가 이 규칙을 기본 기준보다 우선해서 리뷰해요. 규칙을 어긴 코드에는 <b>[팀 규칙]</b>으로 시작하는 코멘트가 달려요.
            </p>

            {loading ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-2)' }}>
                <Loader2 size={16} style={{ animation: 'dc-spin 1s linear infinite' }} /> 불러오는 중...
              </div>
            ) : (
              <div className={s.field}>
                <label htmlFor="reviewConventions" className="dc-label">규칙</label>
                <textarea
                  id="reviewConventions"
                  className="dc-input"
                  value={conventions}
                  onChange={(e) => { setConventions(e.target.value); setSaved(false); }}
                  placeholder={isOwner ? PLACEHOLDER : '아직 정해진 팀 리뷰 규칙이 없어요.'}
                  readOnly={!isOwner}
                  rows={10}
                  style={{ height: 'auto', minHeight: 200, padding: '10px 12px', lineHeight: 1.6, resize: 'vertical', fontFamily: 'inherit' }}
                />
                <div className="dc-help" style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                  {isOwner ? (
                    <span>한 줄에 규칙 하나씩, 구체적으로 적을수록 정확하게 리뷰해요.</span>
                  ) : (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <Lock size={12} /> 방장만 수정할 수 있어요.
                    </span>
                  )}
                  <span style={{ color: tooLong ? 'var(--danger)' : undefined, whiteSpace: 'nowrap' }}>
                    {conventions.length} / {MAX_LENGTH}
                  </span>
                </div>
                {error && (
                  <div className={s.error}>
                    <AlertTriangle size={13} />
                    <span>{error}</span>
                  </div>
                )}
                {saved && !error && (
                  <div className="dc-help" style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text)' }}>
                    <Check size={14} /> 저장했어요. 다음 AI 리뷰부터 적용돼요.
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="dc-modal-footer">
            <button type="button" className="dc-btn" onClick={onClose} disabled={saving}>닫기</button>
            {isOwner && (
              <button type="submit" className="dc-btn dc-btn-primary" disabled={loading || saving || tooLong}>
                {saving ? '저장 중...' : '저장'}
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
};

export default ReviewConventionsModal;
