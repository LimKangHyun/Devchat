import React, { useState, useEffect } from 'react';
import { FaTimes, FaGithub, FaFile } from 'react-icons/fa';
import axiosInstance from '../api/axiosInstance';
import { UI_FONT, CODE_FONT, getFileHash } from './aiReviewUtils';
import DiffViewer from './DiffViewer';

const AiReviewModal = ({ message, roomId, onClose, repositoryUrl }) => {
  const [reviewData, setReviewData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [publishing, setPublishing] = useState(false);
  const [published, setPublished] = useState(false);
  const [commentStates, setCommentStates] = useState({});
  const [showReasonPopup, setShowReasonPopup] = useState({});
  const [showReactivateConfirm, setShowReactivateConfirm] = useState({});
  const [pendingReason, setPendingReason] = useState({});

  // ── 데이터 로드 ─────────────────────────────────────────────────────────────
  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const res = await axiosInstance.get(`/ai-reviews/${message.aiReviewId}`);
        const parsed = JSON.parse(res.data.reviewJson);
        setReviewData(parsed);
        setPublished(res.data.githubPublished);

        const initialStates = {};
        parsed.files.forEach(file => {
          // 파일 경로 해시 미리 계산 (클릭 시 딜레이 제거)
          getFileHash(file.filePath);
          (file.reviews || []).forEach(review => {
            if (review.commentId != null) {
              initialStates[review.commentId] = {
                active: review.active !== false,
                reason: review.reason || null,
                otherReason: review.otherReason || null,
                changedBy: review.changedBy || null,
              };
            }
          });
        });
        setCommentStates(initialStates);
      } catch (e) {
        console.error('AI 리뷰 로딩 실패:', e);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [message.aiReviewId]);

  useEffect(() => {
    const handleEsc = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [onClose]);

  // ── 코멘트 토글 핸들러 ────────────────────────────────────────────────────
  const handleDeactivateClick = (commentId) => {
    setPendingReason(prev => ({ ...prev, [commentId]: { reason: '', otherReason: '' } }));
    setShowReasonPopup(prev => ({ ...prev, [commentId]: true }));
  };

  const handleReasonCancel = (commentId) => {
    setShowReasonPopup(prev => ({ ...prev, [commentId]: false }));
    setPendingReason(prev => { const n = { ...prev }; delete n[commentId]; return n; });
  };

  const handleDeactivateConfirm = async (commentId, aiReviewId) => {
    const { reason, otherReason } = pendingReason[commentId] || {};
    if (!reason) { alert('사유를 선택해주세요.'); return; }
    if (reason === 'OTHER' && !otherReason?.trim()) { alert('기타 사유를 입력해주세요.'); return; }
    try {
      await axiosInstance.patch(`/ai-reviews/${aiReviewId}/reviews/${commentId}/toggle`, {
        reason, otherReason: reason === 'OTHER' ? otherReason : null,
      });
      setCommentStates(prev => ({
        ...prev,
        [commentId]: { active: false, reason, otherReason: reason === 'OTHER' ? otherReason : null, changedBy: 'me' },
      }));
      setShowReasonPopup(prev => ({ ...prev, [commentId]: false }));
      setPendingReason(prev => { const n = { ...prev }; delete n[commentId]; return n; });
    } catch (e) {
      console.error('비활성화 실패:', e);
      alert('비활성화에 실패했습니다.');
    }
  };

  const handleReactivateClick = (commentId) =>
    setShowReactivateConfirm(prev => ({ ...prev, [commentId]: true }));

  const handleReactivateConfirm = async (commentId, aiReviewId) => {
    try {
      await axiosInstance.patch(`/ai-reviews/${aiReviewId}/reviews/${commentId}/toggle`, { reason: null, otherReason: null });
      setCommentStates(prev => ({
        ...prev,
        [commentId]: { active: true, reason: null, otherReason: null, changedBy: null },
      }));
    } catch (e) {
      console.error('재활성화 실패:', e);
      alert('재활성화에 실패했습니다.');
    } finally {
      setShowReactivateConfirm(prev => ({ ...prev, [commentId]: false }));
    }
  };

  const handleReactivateCancel = (commentId) =>
    setShowReactivateConfirm(prev => ({ ...prev, [commentId]: false }));

  const isActive = (review) => {
    if (review.commentId == null) return true;
    const state = commentStates[review.commentId];
    return state == null ? review.active !== false : state.active;
  };

  const handlePublish = async () => {
    try {
      setPublishing(true);
      await axiosInstance.post(`/github/${roomId}/ai-review/${message.aiReviewId}/publish`);
      setPublished(true);
    } catch (e) {
      console.error('GitHub 등록 실패:', e);
      alert('GitHub 등록에 실패했습니다.');
    } finally {
      setPublishing(false);
    }
  };

  const handleFileClick = async (file, idx) => {
    setSelectedFileIndex(idx);
    if (repositoryUrl && message.prNumber) {
      const { getGitHubFileUrl } = await import('./aiReviewUtils');
      const url = await getGitHubFileUrl(repositoryUrl, message.prNumber, file.filePath);
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  };

  const hasActiveReviews = reviewData?.files?.some(
    file => (file.reviews || []).some(r => isActive(r))
  ) ?? false;

  const currentFile = reviewData?.files?.[selectedFileIndex];

  // ── 공통 props (DiffViewer에 전달) ──────────────────────────────────────────
  const diffViewerProps = {
    aiReviewId: message.aiReviewId,
    isActive, commentStates,
    showReasonPopup, showReactivateConfirm, pendingReason,
    onDeactivateClick: handleDeactivateClick,
    onReasonCancel: handleReasonCancel,
    onDeactivateConfirm: handleDeactivateConfirm,
    onReactivateClick: handleReactivateClick,
    onReactivateConfirm: handleReactivateConfirm,
    onReactivateCancel: handleReactivateCancel,
    onPendingReasonChange: (commentId, field, value) =>
      setPendingReason(prev => ({ ...prev, [commentId]: { ...prev[commentId], [field]: value } })),
    repositoryUrl,
    prNumber: message.prNumber,
  };

  return (
    <div
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.6)', zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px', fontFamily: UI_FONT }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{ backgroundColor: '#fff', borderRadius: '10px', width: '100%', maxWidth: '1400px', height: '90vh', display: 'flex', flexDirection: 'column', boxShadow: '0 24px 64px rgba(0,0,0,0.25)', overflow: 'hidden', fontFamily: UI_FONT }}
      >
        {/* 헤더 */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 20px', borderBottom: '1px solid #e2e8f0', backgroundColor: '#f8fafc', flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '15px', fontWeight: '600', color: '#1a202c', fontFamily: UI_FONT, letterSpacing: '-0.01em' }}>
              🤖 AI Code Review
            </span>
            <span style={{ backgroundColor: '#4299e1', color: 'white', padding: '2px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: '500', fontFamily: UI_FONT }}>
              PR #{message.prNumber}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {published ? (
              <span style={{ fontSize: '13px', color: '#27ae60', fontWeight: '500', fontFamily: UI_FONT }}>✅ GitHub에 등록됨</span>
            ) : (
              <button
                onClick={handlePublish}
                disabled={publishing || !hasActiveReviews}
                title={!hasActiveReviews ? '활성화된 리뷰가 없습니다' : ''}
                style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '7px 14px', backgroundColor: publishing || !hasActiveReviews ? '#a0aec0' : '#24292e', color: 'white', border: 'none', borderRadius: '6px', fontSize: '13px', fontWeight: '500', fontFamily: UI_FONT, cursor: publishing || !hasActiveReviews ? 'not-allowed' : 'pointer', letterSpacing: '-0.01em' }}
              >
                <FaGithub size={14} />
                {publishing ? '등록 중...' : 'GitHub에 등록'}
              </button>
            )}
            <button onClick={onClose} style={{ display: 'flex', alignItems: 'center', padding: '7px', backgroundColor: '#fed7d7', color: '#c53030', border: 'none', borderRadius: '6px', cursor: 'pointer' }}>
              <FaTimes size={14} />
            </button>
          </div>
        </div>

        {/* 바디 */}
        {loading ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ textAlign: 'center', color: '#a0aec0', fontFamily: UI_FONT }}>
              <div style={{ width: '28px', height: '28px', border: '2.5px solid #e8e8e8', borderTop: '2.5px solid #4299e1', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 10px' }} />
              <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
              리뷰 불러오는 중...
            </div>
          </div>
        ) : !reviewData ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#e53e3e', fontFamily: UI_FONT }}>
            리뷰 데이터를 불러올 수 없습니다.
          </div>
        ) : (
          <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

            {/* 파일 사이드바 */}
            <div style={{ width: '240px', flexShrink: 0, borderRight: '1px solid #e2e8f0', backgroundColor: '#f8fafc', overflowY: 'auto', padding: '8px 0', fontFamily: UI_FONT }}>
              <div style={{ padding: '8px 14px 4px', fontSize: '11px', color: '#a0aec0', fontWeight: '600', textTransform: 'uppercase', letterSpacing: '0.6px', fontFamily: UI_FONT }}>
                변경된 파일 ({reviewData.files.length})
              </div>
              {reviewData.files.map((file, idx) => {
                const activeCount = (file.reviews || []).filter(r => isActive(r)).length;
                const isSelected = selectedFileIndex === idx;
                return (
                  <div
                    key={idx}
                    onClick={() => handleFileClick(file, idx)}
                    title={repositoryUrl ? `GitHub에서 열기: ${file.filePath}` : file.filePath}
                    style={{ padding: '8px 14px', cursor: 'pointer', backgroundColor: isSelected ? '#ebf4ff' : 'transparent', borderLeft: isSelected ? '3px solid #4299e1' : '3px solid transparent', display: 'flex', alignItems: 'center', gap: '8px' }}
                  >
                    <FaFile size={11} color={isSelected ? '#4299e1' : '#a0aec0'} style={{ flexShrink: 0 }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '12px', fontWeight: isSelected ? '600' : '400', color: isSelected ? '#2b6cb0' : '#4a5568', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontFamily: CODE_FONT, letterSpacing: '-0.02em' }}>
                        {file.filePath.split('/').pop()}
                      </div>
                      <div style={{ fontSize: '10px', color: '#a0aec0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontFamily: CODE_FONT, letterSpacing: '-0.02em' }}>
                        {file.filePath}
                      </div>
                    </div>
                    {activeCount > 0 && (
                      <span style={{ backgroundColor: '#4299e1', color: 'white', borderRadius: '10px', padding: '1px 6px', fontSize: '10px', fontWeight: '600', fontFamily: UI_FONT, flexShrink: 0 }}>
                        {activeCount}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>

            {/* diff 뷰어 */}
            {currentFile && <DiffViewer file={currentFile} {...diffViewerProps} />}
          </div>
        )}
      </div>
    </div>
  );
};

export default AiReviewModal;