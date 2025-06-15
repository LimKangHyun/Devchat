import React, { useState } from 'react';
import Highlight from 'react-highlight';
import { FaTimes, FaCopy, FaExpand, FaCompress } from 'react-icons/fa';

const CodeReviewModal = ({ message, onClose }) => {
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);

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
  React.useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [onClose]);

  // 배경 클릭으로 모달 닫기
  const handleBackgroundClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
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
              코드 뷰어
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
                lineHeight: '1.6'
              }}>
                <Highlight className={message.language || 'java'}>
                  {message.content}
                </Highlight>
              </div>
            </div>
          </div>
        </div>

        {/* 푸터 (향후 API 호출 버튼들 추가 예정) */}
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
            💡 향후 AI 코드 리뷰 기능이 추가될 예정입니다
          </div>
          
          <div style={{ display: 'flex', gap: '8px' }}>
            {/* 향후 AI 리뷰 버튼들이 여기에 추가될 예정 */}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CodeReviewModal;