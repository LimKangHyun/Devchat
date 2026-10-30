import React, { useState } from 'react';
import { X, Info, Github, Loader2 } from 'lucide-react';
import s from './Modals.module.css';
import useBackClose from '../../hooks/useBackClose';

// 레포 없이 만든 방에 GitHub 레포를 나중에 연결하는 모달 (방장 전용)
const ConnectRepoModal = ({ roomName, onClose, onSubmit }) => {
  useBackClose(true, onClose);
  const [repoUrl, setRepoUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const valid = /^https?:\/\/(www\.)?github\.com\/[^/\s]+\/[^/\s]+/.test(repoUrl.trim());

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!valid || loading) return;
    setLoading(true);
    setError('');
    try {
      await onSubmit(repoUrl.trim());
    } catch (err) {
      setError(err.response?.data?.message || '레포지토리 연결에 실패했어요. 잠시 후 다시 시도해 주세요.');
      setLoading(false);
    }
  };

  return (
    <div className="dc-modal-backdrop" onClick={loading ? undefined : onClose}>
      <form className="dc-modal" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <div className="dc-modal-header">
          <h2 className="dc-modal-title">레포지토리 연결</h2>
          <button type="button" className="dc-icon-btn" onClick={onClose} disabled={loading} aria-label="닫기">
            <X size={20} />
          </button>
        </div>

        <div className="dc-modal-body">
          <p style={{ margin: '0 0 16px', color: 'var(--text-2)', fontSize: 14, lineHeight: 1.6 }}>
            <b style={{ color: 'var(--text)' }}>{roomName}</b>에 GitHub 레포를 연결하면 PR 알림, AI 코드 리뷰,
            PR 요약이 이 방에 올라와요.
          </p>

          <div className={s.field}>
            <label htmlFor="connectRepoUrl" className="dc-label">GitHub 레포지토리 URL</label>
            <div className={s.inputGroup}>
              <span className={s.inputAddon}><Github size={16} /></span>
              <input
                id="connectRepoUrl"
                type="text"
                autoFocus
                value={repoUrl}
                onChange={(e) => { setRepoUrl(e.target.value); setError(''); }}
                placeholder="https://github.com/username/repo"
                disabled={loading}
              />
            </div>
            {error ? (
              <div className="dc-help" style={{ color: 'var(--danger)' }}>{error}</div>
            ) : (
              <div className={`dc-help ${s.helpRow}`}>
                <Info size={13} />
                <span>레포의 관리자 권한이 있어야 해요. 연결 후 코드 인덱싱이 자동으로 시작돼요.</span>
              </div>
            )}
          </div>
        </div>

        <div className="dc-modal-footer">
          <button type="button" className="dc-btn" onClick={onClose} disabled={loading}>취소</button>
          <button type="submit" className="dc-btn dc-btn-primary" disabled={!valid || loading}>
            {loading && <Loader2 size={14} style={{ animation: 'dc-spin 1s linear infinite' }} />}
            {loading ? '연결 중...' : '연결하기'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ConnectRepoModal;
