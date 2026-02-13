import React, { useState, useEffect } from 'react';
import 'highlight.js/styles/github.css';

const SearchSidebar = ({ 
  searchKeyword,
  searchResults,
  isSearching,
  errorMessage,
  hasNext,
  totalCount,
  onClose,
  onSearch,
  onMessageClick
}) => {
  const [hoveredIndex, setHoveredIndex] = useState(null);
  
  // 커서 스택: 각 페이지의 마지막 메시지 ID를 저장
  const [cursorStack, setCursorStack] = useState([null]); // [null, cursor1, cursor2, ...]
  const [currentPageIndex, setCurrentPageIndex] = useState(0);
  
  // 총 페이지 수 계산 (예상치)
  const estimatedTotalPages = totalCount ? Math.ceil(totalCount / 10) : '?';
  
  // 검색어 변경 시 커서 스택 초기화
  useEffect(() => {
    setCursorStack([null]);
    setCurrentPageIndex(0);
  }, [searchKeyword]);
  
  // 다음 페이지로 이동
  const handleNextPage = () => {
    if (!hasNext || searchResults.length === 0) return;
    
    // 현재 페이지의 마지막 메시지 ID를 커서로 저장
    const lastMessageId = searchResults[searchResults.length - 1].messageId;
    
    // 새로운 커서를 스택에 추가
    const newStack = [...cursorStack.slice(0, currentPageIndex + 1), lastMessageId];
    setCursorStack(newStack);
    setCurrentPageIndex(currentPageIndex + 1);
    
    // 다음 페이지 검색
    onSearch(searchKeyword, lastMessageId);
  };
  
  // 이전 페이지로 이동
  const handlePrevPage = () => {
    if (currentPageIndex === 0) return;
    
    const prevPageIndex = currentPageIndex - 1;
    setCurrentPageIndex(prevPageIndex);
    
    // 이전 페이지의 커서로 검색 (0번째 페이지는 null)
    const prevCursor = cursorStack[prevPageIndex];
    onSearch(searchKeyword, prevCursor);
  };
  
  // 현재 페이지 번호 (1부터 시작)
  const currentPageNumber = currentPageIndex + 1;
  
  return (
    <div className="search-sidebar" style={{ 
      width: '350px',
      marginLeft: '20px',
      backgroundColor: '#fff',
      borderRadius: '8px',
      boxShadow: '0 0 10px rgba(0, 0, 0, 0.1)',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      position: 'relative'
    }}>
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        padding: '15px',
        borderBottom: '1px solid #eee', 
        paddingBottom: '10px' 
      }}>
        <h3 style={{ margin: 0 }}>"{searchKeyword}" 검색 결과</h3>
        <button 
          onClick={onClose}
          style={{ 
            padding: '5px 10px', 
            backgroundColor: '#f0f0f0', 
            border: '1px solid #ccc',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
        >
          닫기
        </button>
      </div>

      <div style={{ 
        fontSize: '14px', 
        color: '#666', 
        padding: '10px 15px 5px',
        borderBottom: '1px solid #f0f0f0'
      }}>
        최신순으로 정렬됨 • {totalCount ? `전체 ${totalCount}개의 결과` : '검색 중...'}
      </div>

      <div style={{ 
        overflowY: 'auto', 
        flex: 1,
        padding: '0 15px' 
      }}>
      {isSearching ? (
        <div style={{ textAlign: 'center', padding: '20px' }}>검색 중...</div>
      ) : errorMessage ? (
        <div style={{ 
          textAlign: 'center', 
          padding: '20px', 
          color: '#d93025', 
          backgroundColor: '#fce8e6', 
          borderRadius: '4px',
          border: '1px solid #f7c6c5',
          margin: '15px 0'
        }}>
          {errorMessage}
        </div>
      ) : !searchResults || searchResults.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '20px' }}>검색 결과가 없습니다.</div>
      ) : (
        <>
          {searchResults.map((msg, index) => (
            <div 
              key={msg.messageId} 
              onClick={() => onMessageClick && onMessageClick(msg.messageId)}
              onMouseEnter={() => setHoveredIndex(index)}
              onMouseLeave={() => setHoveredIndex(null)}
              style={{ 
                marginBottom: '15px', 
                padding: '15px',
                backgroundColor: hoveredIndex === index ? '#e8f4fd' : '#f8f8f8', 
                borderRadius: '5px',
                border: hoveredIndex === index ? '1px solid #1976d2' : '1px solid #eee',
                cursor: 'pointer',
                transition: 'all 0.2s ease',
                transform: hoveredIndex === index ? 'translateX(2px)' : 'translateX(0)',
                position: 'relative'
              }}
            >
              {/* 호버 시 나타나는 툴팁 */}
              {hoveredIndex === index && (
                <div style={{
                  position: 'absolute',
                  top: '50px',
                  right: '8px',
                  backgroundColor: '#1976d2',
                  color: 'white',
                  padding: '4px 8px',
                  borderRadius: '12px',
                  fontSize: '11px',
                  fontWeight: '500',
                  zIndex: 10,
                  animation: 'fadeIn 0.2s ease'
                }}>
                  이동하기 ➜
                </div>
              )}
              
              <div style={{ display: 'flex' }}>
                <img
                  src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${msg.profileImageUrl}`}
                  alt="profile"
                  style={{
                    width: '40px',
                    height: '40px',
                    borderRadius: '50%',
                    objectFit: 'cover',
                    marginRight: '12px',
                    flexShrink: 0
                  }}
                />
                <div style={{ width: '100%' }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
                    <strong style={{ fontSize: '16px', color: '#333' }}>{msg.senderName}</strong>
                    <span style={{ fontSize: '12px', color: '#888' }}>
                      {new Date(msg.sendAt).toLocaleString('ko-KR', { 
                        year: 'numeric', 
                        month: '2-digit', 
                        day: '2-digit', 
                        hour: '2-digit', 
                        minute: '2-digit' 
                      })}
                    </span>
                  </div>
                  <div style={{ 
                    fontSize: '14px', 
                    marginTop: '8px', 
                    color: '#333',
                    maxHeight: '200px',
                    overflowY: 'auto'
                  }}>
                    {msg.type === 'CODE' || (msg.content && msg.content.startsWith('```')) ? (
                      <div style={{ 
                        backgroundColor: '#f1f3f4', 
                        padding: '10px', 
                        borderRadius: '4px', 
                        fontFamily: 'monospace',
                        maxHeight: '180px',
                        overflowY: 'auto'
                      }}>
                        <code style={{ whiteSpace: 'pre-wrap', fontSize: '13px' }}>
                          {msg.content ? msg.content.replace(/```/g, '') : ''}
                        </code>
                      </div>
                    ) : (
                      <div style={{ lineHeight: '1.5' }}>{msg.content}</div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </>
      )}
      </div>

      {/* 페이지네이션 버튼 - 결과가 있을 때만 표시 */}
      {searchResults && searchResults.length > 0 && (
        <div style={{ 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center',
          padding: '15px',
          borderTop: '1px solid #f0f0f0',
          gap: '10px'
        }}>
          <button
            onClick={handlePrevPage}
            disabled={currentPageIndex === 0}
            style={{ 
              padding: '8px 15px',
              cursor: currentPageIndex === 0 ? 'not-allowed' : 'pointer', 
              opacity: currentPageIndex === 0 ? 0.5 : 1,
              border: '1px solid #ccc',
              borderRadius: '4px',
              backgroundColor: 'white',
              fontWeight: '500'
            }}
          >
            ← 이전
          </button>
          
          <span style={{ 
            fontSize: '14px',
            color: '#333',
            minWidth: '80px',
            textAlign: 'center'
          }}>
            {currentPageNumber} / {estimatedTotalPages}
          </span>
          
          <button
            onClick={handleNextPage}
            disabled={!hasNext}
            style={{ 
              padding: '8px 15px',
              cursor: !hasNext ? 'not-allowed' : 'pointer', 
              opacity: !hasNext ? 0.5 : 1,
              border: '1px solid #ccc',
              borderRadius: '4px',
              backgroundColor: 'white',
              fontWeight: '500'
            }}
          >
            다음 →
          </button>
        </div>
      )}
      
      {/* CSS 애니메이션을 위한 스타일 */}
      <style jsx>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: scale(0.8); }
          to { opacity: 1; transform: scale(1); }
        }
      `}</style>
    </div>
  );
};

export default SearchSidebar;