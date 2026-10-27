import React, { useState, useEffect } from 'react';
import { FaTimes, FaGithub, FaFile, FaChevronDown, FaChevronUp, FaExpand, FaCompress, FaCodeBranch, FaCheck, FaBan } from 'react-icons/fa';
import ReactMarkdown from 'react-markdown';
import axiosInstance from '../api/axiosInstance';
import { getFileHash } from './aiReviewUtils';
import DiffViewer from './DiffViewer';
import './ReviewModals.css';

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
        const data = res.data;

        // files 객체를 DiffViewer가 쓸 수 있는 배열로 변환
        const filesArray = Object.keys(data.fileContents || {}).map(filePath => ({
          filePath,
          fileContent: data.fileContents[filePath] || '',
          beforeContent: (data.beforeFileContents || {})[filePath] || '',
          reviews: (data.files || {})[filePath] || [],
          skipped: false,
        }));

        const initialStates = {};
        filesArray.forEach(file => {
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
        setReviewData({ ...data, files: filesArray });
        setPublished(data.githubPublished);
        setPublishedBy(data.publishedBy || null);
        setPrTitle(data.prTitle || null);
        setPrBody(data.prBody || null);
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
      className={`rv-backdrop${isFullscreen ? ' is-full' : ''}`}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={`rv-modal is-wide${isFullscreen ? ' is-full' : ''}`}
        role="dialog"
        aria-modal="true"
      >
        {/* 헤더 */}
        <div className="rv-header">
          <div className="rv-title-wrap">
            <h3 className="rv-title">AI 코드 리뷰</h3>
            <span className="rv-tag">PR #{message.prNumber}</span>
            {isClosed && (
              <span className={`rv-tag ${message.prStatus === 'MERGED' ? 'is-merged' : 'is-closed'}`}>
                {message.prStatus === 'MERGED' ? <FaCodeBranch size={10} /> : <FaBan size={10} />}
                {message.prStatus === 'MERGED' ? '병합됨' : '닫힘'}
              </span>
            )}
          </div>
          <div className="rv-actions">
            {published ? (
              <span className="rv-status is-ok">
                <FaCheck size={11} />
                GitHub에 등록됨{publishedBy ? ` · ${publishedBy}` : ''}
              </span>
            ) : isClosed ? (
              <span className={`rv-status ${message.prStatus === 'MERGED' ? 'is-merged' : 'is-closed'}`}>
                {message.prStatus === 'MERGED' ? '병합된 PR — GitHub 등록 불가' : '닫힌 PR — GitHub 등록 불가'}
              </span>
            ) : (
              <button
                type="button"
                onClick={handlePublish}
                disabled={publishing || !hasActiveReviews}
                title={!hasActiveReviews ? '활성화된 리뷰가 없습니다' : ''}
                className="dc-btn dc-btn-primary rv-btn-sm"
              >
                <FaGithub size={14} />
                {publishing ? '등록 중...' : 'GitHub에 등록'}
              </button>
            )}
            <button
              type="button"
              onClick={() => setIsFullscreen(prev => !prev)}
              title={isFullscreen ? '축소' : '전체화면'}
              aria-label={isFullscreen ? '축소' : '전체화면'}
              className="dc-icon-btn rv-hide-mobile"
            >
              {isFullscreen ? <FaCompress size={14} /> : <FaExpand size={14} />}
            </button>
            <button type="button" onClick={onClose} title="닫기" aria-label="닫기" className="dc-icon-btn">
              <FaTimes size={16} />
            </button>
          </div>
        </div>

        {/* PR 제목/본문 */}
        {prTitle && (
          <div className="rv-pr">
            <div
              onClick={() => setPrInfoExpanded(prev => !prev)}
              onMouseEnter={() => setPrInfoHovered(true)}
              onMouseLeave={() => setPrInfoHovered(false)}
              className={`rv-pr-toggle${prInfoHovered ? ' is-hovered' : ''}`}
            >
              <FaCodeBranch size={12} style={{ flexShrink: 0 }} />
              <span className="rv-pr-title">{prTitle}</span>
              <span className="rv-pr-hint">
                {prInfoExpanded ? 'PR 본문 닫기' : 'PR 본문 보기'}
                {prInfoExpanded ? <FaChevronUp size={10} /> : <FaChevronDown size={10} />}
              </span>
            </div>
            {prInfoExpanded && (
              <div className="rv-pr-body dc-scroll">
                {prBody
                  ? <ReactMarkdown>{prBody}</ReactMarkdown>
                  : <span className="rv-pr-empty">PR 본문 없음</span>
                }
              </div>
            )}
          </div>
        )}

        {/* 바디 */}
        {loading ? (
          <div className="rv-state">
            <div className="rv-spinner" />
            리뷰 불러오는 중...
          </div>
        ) : !reviewData ? (
          <div className="rv-state is-error">
            리뷰 데이터를 불러올 수 없습니다.
          </div>
        ) : (
          <div className="rv-split">
            {/* 파일 사이드바 */}
            <div className="rv-files dc-scroll">
              <div className="rv-files-label">
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
                    className={`rv-file${isSelected ? ' is-selected' : ''}`}
                  >
                    <FaFile size={11} style={{ flexShrink: 0 }} />
                    <div className="rv-file-info">
                      <div className="rv-file-name">{file.filePath.split('/').pop()}</div>
                      <div className="rv-file-path">{file.filePath}</div>
                    </div>
                    {activeCount > 0 && (
                      <span className="rv-count">{activeCount}</span>
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