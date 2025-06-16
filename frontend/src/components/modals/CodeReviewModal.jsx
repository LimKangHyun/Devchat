import React, { useState, useRef, useEffect } from 'react';
import { FaTimes, FaCopy, FaExpand, FaCompress, FaPlus, FaCheck, FaTrash } from 'react-icons/fa';
import axiosInstance from '../api/axiosInstance';
import Highlight from 'react-highlight';

const CodeReviewModal = ({ message, onClose }) => {
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);

  // 댓글 관련 상태
  const [comments, setComments] = useState({}); // { lineNumber: [comments] }
  const [activeCommentLine, setActiveCommentLine] = useState(null);
  const [commentText, setCommentText] = useState('');
  const [hoveredLine, setHoveredLine] = useState(null);
  const [loading, setLoading] = useState(false); // 이것도 추가
  const [editingCommentId, setEditingCommentId] = useState(null);
  const [editCommentText, setEditCommentText] = useState('');
  const [currentUser, setCurrentUser] = useState(null);

  const HighlightedCode = ({ content, language }) => {
    return (
      <>
        <Highlight className={language}>{content}</Highlight>
        <style>{`
        .hljs {
          margin: 0 !important;
          padding: 0 !important;
          background: transparent !important;
          line-height: 22px !important;
          font-size: 16px !important;
          font-family: inherit !important;
          display: inline !important;
        }
      `}</style>
      </>
    );
  };

  // 현재 사용자 정보 가져오기
  const fetchCurrentUser = async () => {
    try {
      const response = await axiosInstance.get('/user/details');
      setCurrentUser(response.data.id); // username 대신 id 사용
      console.log('현재 사용자 ID:', response.data.id);
    } catch (error) {
      console.error('사용자 정보 조회 실패:', error);
      setCurrentUser(null);
    }
  };

  // 수정 가능 여부 체크 함수
  const canEdit = (comment) => {
    console.log('현재 사용자 ID:', currentUser);
    console.log('댓글 작성자 ID:', comment.authorId);
    console.log('수정 가능 여부:', currentUser && comment.authorId === currentUser);
    return currentUser && comment.authorId === currentUser;
  };

  // 현재 사용자 정보 로드
  useEffect(() => {
    fetchCurrentUser();
  }, []);

  useEffect(() => {
    const loadExistingReviews = async () => {
      try {
        setLoading(true);

        if (!message.messageId) {
          return;
        }
        const reviews = await codeReviewAPI.getByMessageId(message.messageId);

        // 서버에서 받은 리뷰 데이터를 라인별로 정리
        const reviewsByLine = {};

        if (reviews && Array.isArray(reviews)) {
          reviews.forEach(review => {
            const lineNumber = review.lineNumber;

            if (!reviewsByLine[lineNumber]) {
              reviewsByLine[lineNumber] = [];
            }

            reviewsByLine[lineNumber].push({
              id: review.reviewId,
              content: review.content,
              author: review.authorName,
              authorId: review.authorId, // 작성자 ID 추가
              timestamp: review.createAt
            });
          });
        }

        setComments(reviewsByLine);

      } catch (error) {
        if (error.status === 404) {
          setComments({});
          return;
        }

        let errorMessage = '기존 댓글을 불러오는데 실패했습니다.';
        if (error.status === 401) {
          errorMessage = '로그인이 필요합니다.';
        } else if (error.status === 403) {
          errorMessage = '댓글 조회 권한이 없습니다.';
        }

        alert(errorMessage);

      } finally {
        setLoading(false);
      }
    };

    loadExistingReviews();
  }, [message.messageId]);


  const codeReviewAPI = {
    // 리뷰 생성
    create: async (reviewData) => {
      try {
        const response = await axiosInstance.post('/code-reviews', reviewData);
        return response.data;
      } catch (error) {
        // 상태코드와 메시지를 함께 전달
        const status = error.response?.status;
        const message = error.response?.data?.message || '리뷰 생성 실패';
        const customError = new Error(message);
        customError.status = status;
        throw customError;
      }
    },

    // 메시지별 리뷰 조회
    getByMessageId: async (messageId) => {
      try {
        const response = await axiosInstance.get(`/code-reviews/${messageId}`);
        return response.data.reviews || response.data;
      } catch (error) {
        if (error.response?.status === 404) {
          return [];
        }
        
        const status = error.response?.status;
        const message = error.response?.data?.message || '리뷰 조회 실패';
        const customError = new Error(message);
        customError.status = status;
        throw customError;
      }
    },

    // 리뷰 삭제
    delete: async (reviewId) => {
      try {
        await axiosInstance.delete(`/code-reviews/${reviewId}`);
      } catch (error) {
        const status = error.response?.status;
        const message = error.response?.data?.message || '리뷰 삭제 실패';
        const customError = new Error(message);
        customError.status = status;
        throw customError;
      }
    },

    // 리뷰 수정
    update: async (reviewId, content) => {
      try {
        const response = await axiosInstance.put(`/code-reviews/${reviewId}`, {
          content: content
        });
        return response.data;
      } catch (error) {
        const status = error.response?.status;
        const message = error.response?.data?.message || '리뷰 수정 실패';
        const customError = new Error(message);
        customError.status = status;
        throw customError;
      }
    }
  };


  // 코드를 라인별로 분리
  const codeLines = message.content.split('\n');

  // 코드 복사 기능
  const handleCopyCode = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch (err) {
      console.error('복사 실패:', err);
    }
  };

  // ESC 키로 모달 닫기
  useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape') {
        if (editingCommentId) {
          handleCancelEdit();
        } else if (activeCommentLine) {
          handleCancelComment();
        } else {
          onClose();
        }
      }
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [onClose, activeCommentLine, editingCommentId]);

  // 배경 클릭으로 모달 닫기
  const handleBackgroundClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // 댓글 추가
  const handleAddComment = (lineNumber) => {
    setActiveCommentLine(lineNumber);
    setCommentText('');
  };

  // 댓글 저장
  const handleSaveComment = async () => {
    if (!commentText.trim()) return;

    try {
      setLoading(true);

      const reviewData = {
        messageId: message.messageId,
        lineNumber: activeCommentLine,
        content: commentText.trim()
      };

      const createdReview = await codeReviewAPI.create(reviewData);

      const newComment = {
        id: createdReview.reviewId,
        content: createdReview.content,
        author: createdReview.authorName,
        authorId: createdReview.authorId, // 작성자 ID 추가
        timestamp: createdReview.createAt
      };

      setComments(prev => ({
        ...prev,
        [activeCommentLine]: [...(prev[activeCommentLine] || []), newComment]
      }));

      setActiveCommentLine(null);
      setCommentText('');

    } catch (error) {
      console.error('댓글 저장 실패:', error);
      alert(error.message);
    } finally {
      setLoading(false);
    }
  };

  // 댓글 취소(취소버튼 누르는 것)
  const handleCancelComment = () => {
    setActiveCommentLine(null);
    setCommentText('');
  };

  // 댓글 삭제
  const handleDeleteComment = async (lineNumber, commentId) => {
    try {
      // 실제 API 호출
      await codeReviewAPI.delete(commentId);

      // 성공 시 UI에서 제거
      setComments(prev => ({
        ...prev,
        [lineNumber]: prev[lineNumber].filter(comment => comment.id !== commentId)
      }));

    } catch (error) {
      console.error('댓글 삭제 실패:', error);
      alert(error.message || '댓글 삭제에 실패했습니다.');
    }
  };

  // 댓글 수정 시작
  const handleEditComment = (comment) => {
    setEditingCommentId(comment.id);
    setEditCommentText(comment.content);
  };

  // 댓글 수정 저장
  const handleSaveEdit = async (lineNumber, commentId) => {
    if (!editCommentText.trim()) return;

    try {
      setLoading(true);

      const updatedReview = await codeReviewAPI.update(commentId, editCommentText.trim());

      // UI 업데이트
      setComments(prev => ({
        ...prev,
        [lineNumber]: prev[lineNumber].map(comment =>
          comment.id === commentId
            ? { ...comment, content: updatedReview.content || editCommentText.trim() }
            : comment
        )
      }));

      setEditingCommentId(null);
      setEditCommentText('');

    } catch (error) {
      console.error('댓글 수정 실패:', error);
      alert(error.message);
    } finally {
      setLoading(false);
    }
  };

  // 댓글 수정 취소
  const handleCancelEdit = () => {
    setEditingCommentId(null);
    setEditCommentText('');
  };

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.7)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: isFullscreen ? '0' : '20px'
      }}
      onClick={handleBackgroundClick}
    >
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: isFullscreen ? '0' : '12px',
          width: isFullscreen ? '100vw' : '90%',
          height: isFullscreen ? '100vh' : '85vh',
          maxWidth: isFullscreen ? 'none' : '1200px',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: isFullscreen ? 'none' : '0 20px 60px rgba(0, 0, 0, 0.3)',
          overflow: 'hidden'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '16px 20px',
          borderBottom: '1px solid #e2e8f0',
          backgroundColor: '#f8fafc'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <h3 style={{
              margin: 0,
              fontSize: '18px',
              fontWeight: '600',
              color: '#2d3748'
            }}>
              Code Review
            </h3>
            <span style={{
              backgroundColor: '#4299e1',
              color: 'white',
              padding: '2px 8px',
              borderRadius: '12px',
              fontSize: '12px',
              fontWeight: '500'
            }}>
              {message.language || 'java'}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {/* 복사 버튼 */}
            <button
              onClick={handleCopyCode}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                padding: '8px 12px',
                backgroundColor: copySuccess ? '#48bb78' : '#f7fafc',
                color: copySuccess ? 'white' : '#4a5568',
                border: '1px solid #e2e8f0',
                borderRadius: '6px',
                fontSize: '14px',
                cursor: 'pointer',
                transition: 'all 0.2s ease'
              }}
            >
              <FaCopy size={12} />
              {copySuccess ? '복사됨!' : '복사'}
            </button>

            {/* 전체화면 버튼 */}
            <button
              onClick={() => setIsFullscreen(!isFullscreen)}
              style={{
                display: 'flex',
                alignItems: 'center',
                padding: '8px',
                backgroundColor: '#f7fafc',
                color: '#4a5568',
                border: '1px solid #e2e8f0',
                borderRadius: '6px',
                cursor: 'pointer',
                transition: 'all 0.2s ease'
              }}
            >
              {isFullscreen ? <FaCompress size={14} /> : <FaExpand size={14} />}
            </button>

            {/* 닫기 버튼 */}
            <button
              onClick={onClose}
              style={{
                display: 'flex',
                alignItems: 'center',
                padding: '8px',
                backgroundColor: '#fed7d7',
                color: '#c53030',
                border: '1px solid #feb2b2',
                borderRadius: '6px',
                cursor: 'pointer',
                transition: 'all 0.2s ease'
              }}
            >
              <FaTimes size={14} />
            </button>
          </div>
        </div>

        {/* 메시지 정보 */}
        <div style={{
          padding: '12px 20px',
          backgroundColor: '#f8fafc',
          borderBottom: '1px solid #e2e8f0',
          fontSize: '14px',
          color: '#4a5568'
        }}>
          <span style={{ fontWeight: '500' }}>{message.senderName}</span>
          <span style={{ margin: '0 8px', color: '#a0aec0' }}>•</span>
          <span>{new Date(message.sendAt).toLocaleString('ko-KR')}</span>
        </div>

        {/* 코드 영역 */}
        <div style={{
          flex: 1,
          overflow: 'auto',
          backgroundColor: '#fafafa'
        }}>
          <div style={{
            padding: '20px',
            height: '100%'
          }}>
            <div style={{
              backgroundColor: '#ffffff',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              overflow: 'hidden',
              height: '100%'
            }}>
              <div style={{
                height: '100%',
                overflow: 'auto',
                fontSize: '14px',
                lineHeight: '1.6',
                fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace'
              }}>
                {/* 라인별 코드 렌더링 */}
                <div style={{ position: 'relative' }}>
                  {codeLines.map((line, index) => {
                    const lineNumber = index + 1;
                    const hasComments = comments[lineNumber] && comments[lineNumber].length > 0;

                    return (
                      <div key={lineNumber}>
                        {/* 코드 라인 */}
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'flex-start',
                            minHeight: '20px',
                            backgroundColor: hoveredLine === lineNumber ? '#f7fafc' : 'transparent',
                            borderLeft: hasComments ? '3px solid #4299e1' : '3px solid transparent',
                            transition: 'all 0.1s ease'
                          }}
                          onMouseEnter={() => setHoveredLine(lineNumber)}
                          onMouseLeave={() => setHoveredLine(null)}
                        >
                          {/* 라인 번호 */}
                          <div style={{
                            width: '50px',
                            padding: '0 8px',
                            color: '#a0aec0',
                            fontSize: '12px',
                            textAlign: 'right',
                            userSelect: 'none',
                            flexShrink: 0,
                            lineHeight: '20px'
                          }}>
                            {lineNumber}
                          </div>

                          {/* + 버튼 영역 */}
                          <div style={{
                            width: '30px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            flexShrink: 0
                          }}>
                            {(hoveredLine === lineNumber || hasComments) && (
                              <button
                                onClick={() => handleAddComment(lineNumber)}
                                style={{
                                  width: '20px',
                                  height: '20px',
                                  borderRadius: '50%',
                                  border: 'none',
                                  backgroundColor: hasComments ? '#4299e1' : '#e2e8f0',
                                  color: hasComments ? 'white' : '#4a5568',
                                  cursor: 'pointer',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '12px',
                                  transition: 'all 0.2s ease'
                                }}
                              >
                                <FaPlus size={8} />
                              </button>
                            )}
                          </div>

                          {/* 코드 내용 */}
                          <div style={{
                            flex: 1,
                            padding: '0 12px 0 0',
                            whiteSpace: 'pre',
                            lineHeight: '20px'
                          }}>
                            <HighlightedCode
                              content={line}
                              language={message.language || 'java'}
                            />
                          </div>
                        </div>

                        {/* 댓글 입력창 */}
                        {activeCommentLine === lineNumber && (
                          <div style={{
                            marginLeft: '80px',
                            marginRight: '12px',
                            marginBottom: '12px',
                            padding: '12px',
                            backgroundColor: '#f8fafc',
                            border: '1px solid #e2e8f0',
                            borderRadius: '6px'
                          }}>
                            <textarea
                              value={commentText}
                              onChange={(e) => setCommentText(e.target.value)}
                              placeholder='Leave a comment...'
                              style={{
                                width: '100%',
                                minHeight: '80px',
                                border: '1px solid #e2e8f0',
                                borderRadius: '4px',
                                padding: '8px',
                                fontSize: '14px',
                                resize: 'vertical',
                                fontFamily: 'inherit'
                              }}
                              autoFocus
                            />
                            <div style={{
                              display: 'flex',
                              justifyContent: 'flex-end',
                              gap: '8px',
                              marginTop: '8px'
                            }}>
                              <button
                                onClick={handleCancelComment}
                                style={{
                                  padding: '6px 12px',
                                  backgroundColor: '#e2e8f0',
                                  color: '#4a5568',
                                  border: 'none',
                                  borderRadius: '4px',
                                  fontSize: '14px',
                                  cursor: 'pointer'
                                }}
                              >
                                취소
                              </button>
                              <button
                                onClick={handleSaveComment}
                                disabled={!commentText.trim()}
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '6px',
                                  padding: '6px 12px',
                                  backgroundColor: commentText.trim() ? '#4299e1' : '#e2e8f0',
                                  color: commentText.trim() ? 'white' : '#a0aec0',
                                  border: 'none',
                                  borderRadius: '4px',
                                  fontSize: '14px',
                                  cursor: commentText.trim() ? 'pointer' : 'not-allowed'
                                }}
                              >
                                <FaCheck size={12} />
                                리뷰 저장
                              </button>
                            </div>
                          </div>
                        )}

                        {/* 기존 댓글들 */}
                        {comments[lineNumber] && comments[lineNumber].map((comment) => (
                          <div
                            key={comment.id}
                            style={{
                              marginLeft: '80px',
                              marginRight: '12px',
                              marginBottom: '8px',
                              padding: '10px',
                              backgroundColor: '#ffffff',
                              border: '1px solid #e2e8f0',
                              borderRadius: '6px',
                              boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
                              cursor: canEdit(comment) && editingCommentId !== comment.id ? 'pointer' : 'default',
                              transition: 'all 0.2s ease'
                            }}
                            onClick={() => {
                              if (canEdit(comment) && editingCommentId !== comment.id) {
                                handleEditComment(comment);
                              }
                            }}
                            onMouseEnter={(e) => {
                              if (canEdit(comment) && editingCommentId !== comment.id) {
                                e.currentTarget.style.backgroundColor = '#f8fafc';
                                e.currentTarget.style.borderColor = '#4299e1';
                              }
                            }}
                            onMouseLeave={(e) => {
                              if (canEdit(comment) && editingCommentId !== comment.id) {
                                e.currentTarget.style.backgroundColor = '#ffffff';
                                e.currentTarget.style.borderColor = '#e2e8f0';
                              }
                            }}
                          >
                            <div style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              marginBottom: '6px'
                            }}>
                              <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '6px'
                              }}>
                                <span style={{
                                  fontSize: '12px',
                                  color: '#4a5568',
                                  fontWeight: '500'
                                }}>
                                  {comment.author}
                                </span>
                                {canEdit(comment) && editingCommentId !== comment.id && (
                                  <span style={{
                                    fontSize: '10px',
                                    color: '#4299e1',
                                    backgroundColor: '#ebf8ff',
                                    padding: '1px 4px',
                                    borderRadius: '2px'
                                  }}>
                                  </span>
                                )}
                              </div>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <span style={{
                                  fontSize: '11px',
                                  color: '#a0aec0'
                                }}>
                                  {new Date(comment.timestamp).toLocaleString('ko-KR')}
                                </span>
                                {canEdit(comment) && (
                                  <button
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      handleDeleteComment(lineNumber, comment.id);
                                    }}
                                    style={{
                                      padding: '2px',
                                      backgroundColor: 'transparent',
                                      border: 'none',
                                      color: '#e53e3e',
                                      cursor: 'pointer',
                                      fontSize: '12px'
                                    }}
                                  >
                                    <FaTrash size={10} />
                                  </button>
                                )}
                              </div>
                            </div>

                            {/* 댓글 내용 또는 수정 입력창 */}
                            {editingCommentId === comment.id ? (
                              <div>
                                <textarea
                                  value={editCommentText}
                                  onChange={(e) => setEditCommentText(e.target.value)}
                                  style={{
                                    width: '100%',
                                    minHeight: '60px',
                                    border: '1px solid #4299e1',
                                    borderRadius: '4px',
                                    padding: '8px',
                                    fontSize: '14px',
                                    resize: 'vertical',
                                    fontFamily: 'inherit',
                                    marginBottom: '8px'
                                  }}
                                  autoFocus
                                />
                                <div style={{
                                  display: 'flex',
                                  justifyContent: 'flex-end',
                                  gap: '8px'
                                }}>
                                  <button
                                    onClick={handleCancelEdit}
                                    style={{
                                      padding: '4px 8px',
                                      backgroundColor: '#e2e8f0',
                                      color: '#4a5568',
                                      border: 'none',
                                      borderRadius: '4px',
                                      fontSize: '12px',
                                      cursor: 'pointer'
                                    }}
                                  >
                                    취소
                                  </button>
                                  <button
                                    onClick={() => handleSaveEdit(lineNumber, comment.id)}
                                    disabled={!editCommentText.trim() || loading}
                                    style={{
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: '4px',
                                      padding: '4px 8px',
                                      backgroundColor: editCommentText.trim() ? '#4299e1' : '#e2e8f0',
                                      color: editCommentText.trim() ? 'white' : '#a0aec0',
                                      border: 'none',
                                      borderRadius: '4px',
                                      fontSize: '12px',
                                      cursor: editCommentText.trim() ? 'pointer' : 'not-allowed'
                                    }}
                                  >
                                    <FaCheck size={10} />
                                    저장
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <div style={{
                                fontSize: '14px',
                                color: '#2d3748',
                                lineHeight: '1.4',
                                whiteSpace: 'pre-wrap'
                              }}>
                                {comment.content}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* 푸터 */}
        <div style={{
          padding: '16px 20px',
          backgroundColor: '#f8fafc',
          borderTop: '1px solid #e2e8f0',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div style={{
            fontSize: '13px',
            color: '#718096'
          }}>
            💬 라인별로 댓글을 달아 코드 리뷰를 진행하세요
          </div>

          <div style={{
            fontSize: '12px',
            color: '#a0aec0'
          }}>
            총 {Object.values(comments).flat().length}개의 댓글
          </div>
        </div>
      </div>
    </div>
  );
};

export default CodeReviewModal;