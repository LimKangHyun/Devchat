import React, { useState, useEffect } from 'react';
import { X, ChevronLeft, ChevronRight } from 'lucide-react';
import 'highlight.js/styles/github.css';
import s from './chatroom/Chat.module.css';
import useBackClose from '../hooks/useBackClose';

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
  useBackClose(true, onClose);
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
    const lastMessageId = searchResults[searchResults.length - 1].messageId;
    const newStack = [...cursorStack.slice(0, currentPageIndex + 1), lastMessageId];
    setCursorStack(newStack);
    setCurrentPageIndex(currentPageIndex + 1);
    onSearch(searchKeyword, lastMessageId);
  };

  // 이전 페이지로 이동
  const handlePrevPage = () => {
    if (currentPageIndex === 0) return;
    const prevPageIndex = currentPageIndex - 1;
    setCurrentPageIndex(prevPageIndex);
    const prevCursor = cursorStack[prevPageIndex];
    onSearch(searchKeyword, prevCursor);
  };

  const currentPageNumber = currentPageIndex + 1;

  return (
    <aside className={s.searchPanel}>
      <div className={s.searchPanelHeader}>
        <div style={{ minWidth: 0 }}>
          <div className={s.searchPanelTitle}>검색 결과</div>
          <div className={s.searchPanelSub}>“{searchKeyword}”</div>
        </div>
        <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="검색 닫기">
          <X size={18} />
        </button>
      </div>

      <div className={s.searchPanelMeta}>
        최신순 · {totalCount != null ? `${totalCount}개의 결과` : '검색 중...'}
      </div>

      <div className={`${s.searchPanelList} dc-scroll`}>
        {isSearching ? (
          <div className={s.searchPanelEmpty}>검색 중...</div>
        ) : errorMessage ? (
          <div className={s.searchPanelError}>{errorMessage}</div>
        ) : !searchResults || searchResults.length === 0 ? (
          <div className={s.searchPanelEmpty}>검색 결과가 없습니다.</div>
        ) : (
          searchResults.map((msg) => (
            <button
              type="button"
              key={msg.messageId}
              onClick={() => onMessageClick && onMessageClick(msg.messageId)}
              className={s.searchResult}
            >
              <img
                src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${msg.profileImageUrl}`}
                alt=""
                className={s.searchResultAvatar}
                onError={(e) => { e.currentTarget.src = '/images/not-found-profile.webp'; }}
              />
              <div className={s.searchResultBody}>
                <div className={s.msgMeta}>
                  <span className={s.senderName}>{msg.senderName}</span>
                  <span className={s.msgTime}>
                    {new Date(msg.createdAt).toLocaleString('ko-KR', {
                      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
                    })}
                  </span>
                </div>
                {msg.type === 'CODE' || (msg.content && msg.content.startsWith('```')) ? (
                  <pre className={s.searchResultCode}>{msg.content ? msg.content.replace(/```/g, '') : ''}</pre>
                ) : (
                  <div className={s.searchResultText}>{msg.content}</div>
                )}
                <span className={s.searchResultJump}>메시지로 이동</span>
              </div>
            </button>
          ))
        )}
      </div>

      {searchResults && searchResults.length > 0 && (
        <div className={s.searchPager}>
          <button type="button" className="dc-btn" onClick={handlePrevPage} disabled={currentPageIndex === 0}>
            <ChevronLeft size={15} /> 이전
          </button>
          <span className={s.searchPagerText}>{currentPageNumber} / {estimatedTotalPages}</span>
          <button type="button" className="dc-btn" onClick={handleNextPage} disabled={!hasNext}>
            다음 <ChevronRight size={15} />
          </button>
        </div>
      )}
    </aside>
  );
};

export default SearchSidebar;
