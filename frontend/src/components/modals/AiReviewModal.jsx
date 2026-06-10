import React, { useState, useEffect } from 'react';
import { FaTimes, FaGithub, FaFile, FaChevronDown, FaChevronUp, FaExpand, FaCompress, FaCodeBranch } from 'react-icons/fa';
import ReactMarkdown from 'react-markdown';
import axiosInstance from '../api/axiosInstance';
import { UI_FONT, CODE_FONT, getFileHash } from './aiReviewUtils';
import DiffViewer from './DiffViewer';

const AiReviewModal = ({ message, roomId, onClose, repositoryUrl, currentUser }) => {
  const [reviewData, setReviewData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [publishing, setPublishing] = useState(false);
  const [published, setPublished] = useState(false);
  const [publishedBy, setPublishedBy] = useState(null);
  const [commentStates, setCommentStates] = useState({});
  const [showReasonPopup, setShowReasonPopup] = useState({});
  const [showReactivateConfirm, setShowReactivateConfirm] = useState({});
  const [pendingReason, setPendingReason] = useState({});
  const [prInfoExpanded, setPrInfoExpanded] = useState(false);
  const [prInfoHovered, setPrInfoHovered] = useState(false);
  const [prTitle, setPrTitle] = useState(null);
  const [prBody, setPrBody] = useState(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const isClosed = message.prStatus === 'CLOSED' || message.prStatus === 'MERGED';

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const res = await axiosInstance.get(`/ai-reviews/${message.aiReviewId}`);
        const parsed = JSON.parse(res.data.reviewJson);
        setReviewData(parsed);
        setPublished(res.data.githubPublished);
        setPublishedBy(res.data.publishedBy || null);
        setPrTitle(res.data.prTitle || null);
        setPrBody(res.data.prBody || null);

        const initialStates = {};
        parsed.files.forEach(file => {
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
        [commentId]: { active: false, reason, otherReason: reason === 'OTHER' ? otherReason : null, changedBy: currentUser?.nickname || 'me' },
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
      setPublishedBy(currentUser?.nickname || null);
    } catch (e) {
      console.error('GitHub 등록 실패:', e);
      alert('GitHub 등록에 실패했습니다.');
    } finally {
      setPublishing(false);
    }
  };

  const hasActiveReviews = reviewData?.files?.some(
    file => (file.reviews || []).some(r => isActive(r))
  ) ?? false;

  const currentFile = reviewData?.files?.[selectedFileIndex];

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
    published,
  };

  return (
    <div
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      style={{
        position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.6)', zIndex: 9999,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: isFullscreen ? '0' : '20px', fontFamily: UI_FONT,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          backgroundColor: '#fff',
          borderRadius: isFullscreen ? '0' : '10px',
          width: isFullscreen ? '100vw' : '100%',
          maxWidth: isFullscreen ? 'none' : '1400px',
          height: isFullscreen ? '100vh' : '90vh',
          display: 'flex', flexDirection: 'column',
          boxShadow: isFullscreen ? 'none' : '0 24px 64px rgba(0,0,0,0.25)',
          overflow: 'hidden', fontFamily: UI_FONT,
        }}
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
            {isClosed && (
              <span style={{
                backgroundColor: message.prStatus === 'MERGED' ? '#6f42c1' : '#e53e3e',
                color: 'white', padding: '2px 10px', borderRadius: '12px',
                fontSize: '12px', fontWeight: '500', fontFamily: UI_FONT,
              }}>
                {message.prStatus === 'MERGED' ? '🔀 병합됨' : '🚫 닫힘'}
              </span>
            )}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {published ? (
              <span style={{ fontSize: '13px', color: '#27ae60', fontWeight: '500', fontFamily: UI_FONT }}>
                ✅ GitHub에 등록됨{publishedBy ? ` — by ${publishedBy}` : ''}
              </span>
            ) : isClosed ? (
              <span style={{ fontSize: '13px', fontWeight: '500', fontFamily: UI_FONT, color: message.prStatus === 'MERGED' ? '#6f42c1' : '#e53e3e' }}>
                {message.prStatus === 'MERGED' ? '병합된 PR — GitHub 등록 불가' : '닫힌 PR — GitHub 등록 불가'}
              </span>
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
            <button
              onClick={() => setIsFullscreen(prev => !prev)}
              title={isFullscreen ? '축소' : '전체화면'}
              style={{ display: 'flex', alignItems: 'center', padding: '7px', backgroundColor: '#f7fafc', color: '#4a5568', border: '1px solid #e2e8f0', borderRadius: '6px', cursor: 'pointer' }}
            >
              {isFullscreen ? <FaCompress size={14} /> : <FaExpand size={14} />}
            </button>
            <button onClick={onClose} style={{ display: 'flex', alignItems: 'center', padding: '7px', backgroundColor: '#fed7d7', color: '#c53030', border: 'none', borderRadius: '6px', cursor: 'pointer' }}>
              <FaTimes size={14} />
            </button>
          </div>
        </div>

        {/* PR 제목/본문 — 헤더 바로 아래 */}
        {prTitle && (
          <div style={{ borderBottom: '1px solid #e2e8f0', flexShrink: 0 }}>
            <div
              onClick={() => setPrInfoExpanded(prev => !prev)}
              onMouseEnter={() => setPrInfoHovered(true)}
              onMouseLeave={() => setPrInfoHovered(false)}
              style={{
                display: 'flex', alignItems: 'center', gap: '10px',
                padding: '9px 20px', cursor: 'pointer', userSelect: 'none',
                backgroundColor: prInfoHovered ? '#edf2f7' : '#f8fafc',
                transition: 'background-color 0.15s',
              }}
            >
              <FaCodeBranch size={12} color="#718096" style={{ flexShrink: 0 }} />
              <span style={{
                flex: 1, fontSize: '13px', fontWeight: '500', color: '#2d3748',
                fontFamily: UI_FONT, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {prTitle}
              </span>
              <span style={{ fontSize: '11px', color: '#a0aec0', fontFamily: UI_FONT, flexShrink: 0, marginRight: '4px' }}>
                {prInfoExpanded ? 'PR 본문 닫기' : 'PR 본문 보기'}
              </span>
              {prInfoExpanded
                ? <FaChevronUp size={11} color="#a0aec0" />
                : <FaChevronDown size={11} color="#a0aec0" />
              }
            </div>
            {prInfoExpanded && (
              <div style={{
                padding: '12px 20px 16px', borderTop: '1px solid #e2e8f0',
                backgroundColor: '#fff',
                fontSize: '13px', color: '#4a5568', fontFamily: UI_FONT,
                lineHeight: '1.7', maxHeight: '200px', overflowY: 'auto',
              }}>
                {prBody
                  ? <ReactMarkdown>{prBody}</ReactMarkdown>
                  : <span style={{ color: '#a0aec0' }}>PR 본문 없음</span>
                }
              </div>
            )}
          </div>
        )}

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
                    onClick={() => setSelectedFileIndex(idx)}
                    title={file.filePath}
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