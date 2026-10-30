import React, { useState, useEffect } from 'react';
import { FaTimes, FaCopy, FaExpand, FaCompress, FaPlus, FaCheck, FaTrash } from 'react-icons/fa';
import axiosInstance from '../api/axiosInstance';
import Highlight from 'react-highlight';
import useBackClose from '../../hooks/useBackClose';
import './ReviewModals.css';

const CodeReviewModal = ({ message, onClose }) => {
  useBackClose(true, onClose);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);

  const [reviews, setReviews] = useState({});
  const [activeReviewLine, setActiveReviewLine] = useState(null);
  const [reviewText, setReviewText] = useState('');
  const [hoveredLine, setHoveredLine] = useState(null);
  const [loading, setLoading] = useState(false);
  const [editingReviewId, setEditingReviewId] = useState(null);
  const [editReviewText, setEditReviewText] = useState('');
  const [currentUser, setCurrentUser] = useState(null);
  const [currentFileContent, setCurrentFileContent] = useState('');

  const isAiReview = message.type === 'AI_REVIEW';

  // .hljs 스타일은 ReviewModals.css 의 .rv-code-text .hljs 에서 지정
  const HighlightedCode = ({ content, language }) => (
    <Highlight className={language}>{content}</Highlight>
  );

  const fetchCurrentUser = async () => {
    try {
      const response = await axiosInstance.get('/user/details');
      setCurrentUser(response.data.id);
    } catch (error) {
      console.error('사용자 정보 조회 실패:', error);
      setCurrentUser(null);
    }
  };

  const canEdit = (review) => {
    return currentUser && review.authorId === currentUser;
  };

  const codeReviewAPI = {
    create: async (reviewData) => {
      try {
        const response = await axiosInstance.post('/code-reviews', reviewData);
        return response.data;
      } catch (error) {
        const status = error.response?.status;
        const msg = error.response?.data?.message || '리뷰 생성 실패';
        const customError = new Error(msg);
        customError.status = status;
        throw customError;
      }
    },
    getByMessageId: async (messageId) => {
      try {
        const response = await axiosInstance.get(`/code-reviews/${messageId}`);
        return response.data.reviews || response.data;
      } catch (error) {
        if (error.response?.status === 404) return [];
        const status = error.response?.status;
        const msg = error.response?.data?.message || '리뷰 조회 실패';
        const customError = new Error(msg);
        customError.status = status;
        throw customError;
      }
    },
    delete: async (reviewId) => {
      try {
        await axiosInstance.delete(`/code-reviews/${reviewId}`);
      } catch (error) {
        const status = error.response?.status;
        const msg = error.response?.data?.message || '리뷰 삭제 실패';
        const customError = new Error(msg);
        customError.status = status;
        throw customError;
      }
    },
    update: async (reviewId, content) => {
      try {
        const response = await axiosInstance.put(`/code-reviews/${reviewId}`, { content });
        return response.data;
      } catch (error) {
        const status = error.response?.status;
        const msg = error.response?.data?.message || '리뷰 수정 실패';
        const customError = new Error(msg);
        customError.status = status;
        throw customError;
      }
    }
  };

  useEffect(() => {
    fetchCurrentUser();
  }, []);

  useEffect(() => {
    const loadReviews = async () => {
      try {
        setLoading(true);

        if (isAiReview) {
          const response = await axiosInstance.get(`/ai-reviews/${message.aiReviewId}`);
          const { files, fileContents } = response.data;

          // 첫 번째 파일 선택
          const firstFilePath = Object.keys(files)[0];
          const firstFileComments = files[firstFilePath] || [];

          // fileContent 설정
          setCurrentFileContent(fileContents?.[firstFilePath] || '');

          const reviewsByLine = {};
          firstFileComments
              .filter(item => item.active)
              .forEach(item => {
                  const lineNumber = item.lineNumber;
                  if (!reviewsByLine[lineNumber]) reviewsByLine[lineNumber] = [];
                  reviewsByLine[lineNumber].push({
                      id: `ai-${item.commentId}`,
                      content: item.comment,
                      author: 'AI 리뷰봇',
                      authorId: null,
                      timestamp: message.createdAt
                  });
              });
          setReviews(reviewsByLine);
          return;
      }

        // 일반 CODE 타입이면 기존대로 API 호출
        if (!message.messageId) return;
        const reviews = await codeReviewAPI.getByMessageId(message.messageId);
        const reviewsByLine = {};
        if (reviews && Array.isArray(reviews)) {
          reviews.forEach(review => {
            const lineNumber = review.lineNumber;
            if (!reviewsByLine[lineNumber]) reviewsByLine[lineNumber] = [];
            reviewsByLine[lineNumber].push({
              id: review.reviewId,
              content: review.content,
              author: review.authorName,
              authorId: review.authorId,
              timestamp: review.createAt
            });
          });
        }
        setReviews(reviewsByLine);

      } catch (error) {
        if (error.status === 404) { setReviews({}); return; }
        let errorMessage = '기존 댓글을 불러오는데 실패했습니다.';
        if (error.status === 401) errorMessage = '로그인이 필요합니다.';
        else if (error.status === 403) errorMessage = '댓글 조회 권한이 없습니다.';
        alert(errorMessage);
      } finally {
        setLoading(false);
      }
    };

    loadReviews();
  }, [message.messageId]);

  const codeLines = (isAiReview ? currentFileContent : message.content)?.split('\n') ?? [];

  const handleCopyCode = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch (err) {
      console.error('복사 실패:', err);
    }
  };

  useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape') {
        if (editingReviewId) handleCancelEdit();
        else if (activeReviewLine) handleCancelReview();
        else onClose();
      }
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [onClose, activeReviewLine, editingReviewId]);

  const handleBackgroundClick = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  const handleAddReview = (lineNumber) => {
    setActiveReviewLine(lineNumber);
    setReviewText('');
  };

  const handleSaveReview = async () => {
    if (!reviewText.trim()) return;
    try {
      setLoading(true);
      const reviewData = { messageId: message.messageId, lineNumber: activeReviewLine, content: reviewText.trim() };
      const createdReview = await codeReviewAPI.create(reviewData);
      const newReview = {
        id: createdReview.reviewId,
        content: createdReview.content,
        author: createdReview.authorName,
        authorId: createdReview.authorId,
        timestamp: createdReview.createAt
      };
      setReviews(prev => ({ ...prev, [activeReviewLine]: [...(prev[activeReviewLine] || []), newReview] }));
      setActiveReviewLine(null);
      setReviewText('');
    } catch (error) {
      console.error('댓글 저장 실패:', error);
      alert(error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleCancelReview = () => { setActiveReviewLine(null); setReviewText(''); };

  const handleDeleteReview = async (lineNumber, reviewId) => {
    try {
      await codeReviewAPI.delete(reviewId);
      setReviews(prev => ({ ...prev, [lineNumber]: prev[lineNumber].filter(r => r.id !== reviewId) }));
    } catch (error) {
      console.error('댓글 삭제 실패:', error);
      alert(error.message || '댓글 삭제에 실패했습니다.');
    }
  };

  const handleEditReview = (review) => { setEditingReviewId(review.id); setEditReviewText(review.content); };

  const handleSaveEdit = async (lineNumber, reviewId) => {
    if (!editReviewText.trim()) return;
    try {
      setLoading(true);
      const updatedReview = await codeReviewAPI.update(reviewId, editReviewText.trim());
      setReviews(prev => ({
        ...prev,
        [lineNumber]: prev[lineNumber].map(r =>
          r.id === reviewId ? { ...r, content: updatedReview.content || editReviewText.trim() } : r
        )
      }));
      setEditingReviewId(null);
      setEditReviewText('');
    } catch (error) {
      console.error('댓글 수정 실패:', error);
      alert(error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEdit = () => { setEditingReviewId(null); setEditReviewText(''); };

  return (
    <div
      className={`rv-backdrop${isFullscreen ? ' is-full' : ''}`}
      onClick={handleBackgroundClick}
    >
      <div
        className={`rv-modal${isFullscreen ? ' is-full' : ''}`}
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="rv-header">
          <div className="rv-title-wrap">
            <h3 className="rv-title">{isAiReview ? 'AI 코드 리뷰' : '코드 리뷰'}</h3>
            <span className="rv-tag">
              {isAiReview ? `PR #${message.prNumber}` : (message.language || 'java')}
            </span>
          </div>

          <div className="rv-actions">
            <button
              type="button"
              onClick={handleCopyCode}
              className={`dc-btn rv-btn-sm${copySuccess ? ' is-done' : ''}`}
            >
              {copySuccess ? <FaCheck size={11} /> : <FaCopy size={11} />}
              {copySuccess ? '복사됨' : '복사'}
            </button>
            <button
              type="button"
              onClick={() => setIsFullscreen(!isFullscreen)}
              className="dc-icon-btn rv-hide-mobile"
              title={isFullscreen ? '축소' : '전체화면'}
              aria-label={isFullscreen ? '축소' : '전체화면'}
            >
              {isFullscreen ? <FaCompress size={14} /> : <FaExpand size={14} />}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="dc-icon-btn"
              title="닫기"
              aria-label="닫기"
            >
              <FaTimes size={16} />
            </button>
          </div>
        </div>

        {/* 메시지 정보 */}
        <div className="rv-meta">
          {isAiReview ? (
            <span className="rv-meta-path">{message.filePath}</span>
          ) : (
            <>
              <strong>{message.senderName}</strong>
              <span className="rv-meta-dot">·</span>
              <span>{new Date(message.createdAt).toLocaleString('ko-KR')}</span>
            </>
          )}
        </div>

        {/* 코드 영역 */}
        <div className="rv-code-scroll dc-scroll">
          <div className="rv-code">
            {codeLines.map((line, index) => {
              const lineNumber = index + 1;
              const hasReviews = reviews[lineNumber] && reviews[lineNumber].length > 0;

              return (
                <div key={lineNumber}>
                  <div
                    className={`rv-line${hoveredLine === lineNumber ? ' is-hovered' : ''}${hasReviews ? ' has-reviews' : ''}`}
                    onMouseEnter={() => setHoveredLine(lineNumber)}
                    onMouseLeave={() => setHoveredLine(null)}
                  >
                    <div className="rv-ln">{lineNumber}</div>

                    <div className="rv-add-slot">
                      {(hoveredLine === lineNumber || hasReviews) && (
                        <button
                          type="button"
                          onClick={() => handleAddReview(lineNumber)}
                          className={`rv-add-btn${hasReviews ? ' has-reviews' : ''}`}
                          title="이 줄에 댓글 달기"
                          aria-label={`${lineNumber}번 줄에 댓글 달기`}
                        >
                          <FaPlus size={8} />
                        </button>
                      )}
                    </div>

                    <div className="rv-code-text">
                      <HighlightedCode content={line} language={message.language || 'java'} />
                    </div>
                  </div>

                  {/* 댓글 입력창 */}
                  {activeReviewLine === lineNumber && (
                    <div className="rv-composer rv-indent">
                      <textarea
                        className="rv-textarea"
                        value={reviewText}
                        onChange={(e) => setReviewText(e.target.value)}
                        placeholder="리뷰를 남겨주세요..."
                        autoFocus
                      />
                      <div className="rv-composer-bar">
                        <button type="button" onClick={handleCancelReview} className="dc-btn dc-btn-ghost rv-btn-sm">
                          취소
                        </button>
                        <button
                          type="button"
                          onClick={handleSaveReview}
                          disabled={!reviewText.trim()}
                          className="dc-btn dc-btn-primary rv-btn-sm"
                        >
                          <FaCheck size={11} />
                          리뷰 저장
                        </button>
                      </div>
                    </div>
                  )}

                  {/* 댓글들 (스레드) */}
                  {hasReviews && (
                    <div className="rv-thread rv-indent">
                      {reviews[lineNumber].map((review) => {
                        const isBot = review.authorId === null;
                        const editable = canEdit(review) && editingReviewId !== review.id;
                        return (
                          <div
                            key={review.id}
                            className={`rv-msg${editable ? ' is-editable' : ''}`}
                            title={editable ? '클릭해서 수정' : undefined}
                            onClick={() => {
                              if (canEdit(review) && editingReviewId !== review.id) handleEditReview(review);
                            }}
                          >
                            <div className={`rv-avatar${isBot ? ' is-bot' : ''}`} aria-hidden="true">
                              {isBot ? 'AI' : String(review.author || '?').charAt(0)}
                            </div>
                            <div className="rv-msg-body">
                              <div className="rv-msg-head">
                                <span className="rv-msg-name">{review.author}</span>
                                <span className="rv-msg-time">
                                  {new Date(review.timestamp).toLocaleString('ko-KR')}
                                </span>
                                {canEdit(review) && (
                                  <span className="rv-msg-tools">
                                    <button
                                      type="button"
                                      onClick={(e) => { e.stopPropagation(); handleDeleteReview(lineNumber, review.id); }}
                                      className="rv-msg-del"
                                      title="삭제"
                                      aria-label="댓글 삭제"
                                    >
                                      <FaTrash size={11} />
                                    </button>
                                  </span>
                                )}
                              </div>

                              {editingReviewId === review.id ? (
                                <div className="rv-composer is-inline">
                                  <textarea
                                    className="rv-textarea"
                                    value={editReviewText}
                                    onChange={(e) => setEditReviewText(e.target.value)}
                                    autoFocus
                                  />
                                  <div className="rv-composer-bar">
                                    <button type="button" onClick={handleCancelEdit} className="dc-btn dc-btn-ghost rv-btn-sm">
                                      취소
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => handleSaveEdit(lineNumber, review.id)}
                                      disabled={!editReviewText.trim() || loading}
                                      className="dc-btn dc-btn-primary rv-btn-sm"
                                    >
                                      <FaCheck size={11} />
                                      저장
                                    </button>
                                  </div>
                                </div>
                              ) : (
                                <div className="rv-msg-text">{review.content}</div>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* 푸터 */}
        <div className="rv-footer">
          <div>
            {isAiReview
              ? 'AI가 생성한 인라인 리뷰입니다. 추가 댓글을 달 수 있어요.'
              : '라인 번호 옆 + 버튼을 눌러 줄마다 댓글을 달 수 있어요.'}
          </div>
          <div className="rv-footer-count">
            총 {Object.values(reviews).flat().length}개의 댓글
          </div>
        </div>
      </div>
    </div>
  );
};

export default CodeReviewModal;