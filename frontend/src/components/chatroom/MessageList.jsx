import React, { useState } from 'react';
import Highlight from 'react-highlight';
import { FaUserPlus } from 'react-icons/fa';

/* ── 유틸리티 함수 ── */
const formatDate = (dateString) => {
  const date = new Date(dateString);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  if (date.toDateString() === today.toDateString()) return '오늘';
  if (date.toDateString() === yesterday.toDateString()) return '어제';
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
};

const formatTime = (dateString) => {
  return new Date(dateString).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
};

const renderWithLink = (text) => {
  const urlRegex = /(https?:\/\/[^\s]+)/g;
  return text.split(urlRegex).map((part, i) =>
    urlRegex.test(part) ? (
      <a key={i} href={part} target="_blank" rel="noopener noreferrer"
        style={{ color: '#1264a3', textDecoration: 'underline' }}>
        {part}
      </a>
    ) : part
  );
};

/* ── 코드 블록 컴포넌트 ── */
const HighlightedCode = ({ content, language, onClick }) => (
  <div
    onClick={onClick}
    style={{ cursor: 'pointer', position: 'relative', borderRadius: '6px', overflow: 'hidden' }}
    onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.92')}
    onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
  >
    <Highlight className={language}>{content}</Highlight>
    <div style={{
      position: 'absolute', top: '8px', right: '10px',
      background: 'rgba(0,0,0,0.55)', color: 'white',
      padding: '3px 8px', borderRadius: '4px', fontSize: '11px',
    }}>
      클릭하여 자세히 보기
    </div>
  </div>
);

/* ── PR 히스토리 서브 아이템 ── */
const PrHistoryItem = ({ msg }) => {
  const firstLine = (msg.content || '').split('\n')[0];
  const match = firstLine.match(/^\[([^\]]+)\]/);
  const label = match ? match[1] : 'PR 업데이트';

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: '6px',
      fontSize: '12px', color: '#616061',
      paddingLeft: '12px', marginTop: '2px',
    }}>
      <span style={{ color: '#d1d2d3' }}>↳</span>
      <span style={{ color: '#1d1c1d', fontWeight: '600' }}>[{label}]</span>
      <span style={{ color: '#868686', fontSize: '11px' }}>{formatTime(msg.createdAt)}</span>
    </div>
  );
};

const getEventIcon = (content) => {
  if (!content) return '📦';
  if (content.includes('[ISSUE')) return '📋';
  if (content.includes('✅')) return '✅';
  if (content.includes('❌')) return '❌';
  if (content.includes('⚠️')) return '⚠️';
  if (content.includes('[PR merged]')) return '🔀';
  if (content.includes('[PR opened]')) return '📬';
  if (content.includes('[PR closed]')) return '📭';
  return '📦';
};

/* ── GIT 메시지 본문 ── */
const GitMessageContent = ({ msg, subMessages }) => {
  const [expanded, setExpanded] = useState(false);

  const prNumber = msg.prNumber
    || (() => {
      const match = (msg.content || '').match(/\/pull\/(\d+)/);
      return match ? parseInt(match[1]) : null;
    })();

  const prUrl = (() => {
    const match = (msg.content || '').match(/(https?:\/\/[^\s]+)/);
    return match ? match[1] : null;
  })();

  const firstLine = (msg.content || '').split('\n')[0];
  const badgeMatch = firstLine.match(/^[^\[]*(\[[^\]]+\])\s*(.*)/);
  const badgeLabel = badgeMatch ? badgeMatch[1] : null;
  const titleText  = badgeMatch ? badgeMatch[2] : firstLine;
  const cleanTitle = titleText.replace(/\s*by\s+\S+$/, '').trim();

  const isIssue = (msg.content || '').includes('[ISSUE');
  const isWorkflow = (msg.content || '').includes('[Workflow') || (msg.content || '').match(/✅|❌|⚠️/);
  const linkLabel = isIssue ? '이슈 보기' : isWorkflow ? '워크플로우 보기' : 'PR 보기';

  const bodyLines = (msg.content || '')
    .split('\n')
    .slice(1)
    .filter((line) => !/^https?:\/\//.test(line.trim()));

  return (
    <div style={{
      display: 'flex',
      backgroundColor: '#f0f4f8',
      borderRadius: '6px',
      overflow: 'hidden',
      boxShadow: '0 2px 5px rgba(18, 100, 163, 0.06), 0 1px 2px rgba(0, 0, 0, 0.02)',
      maxWidth: '600px',
      margin: '6px 0',
    }}>
      <div style={{ width: '3px', backgroundColor: '#24292e', flexShrink: 0 }} />

      <div style={{
        lineHeight: '1.5',
        padding: '10px 14px', fontSize: '13px',
        color: '#212529',
        flex: 1, minWidth: 0,
      }}>
        {/* 상단: 아이콘+제목 영역 / PR 보기 버튼 */}
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '12px' }}>

          {/* 왼쪽: 아이콘 고정 + 제목 블록 */}
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '6px', flex: 1, minWidth: 0 }}>
            <span style={{ fontSize: '13px', flexShrink: 0, marginTop: '2px' }}>{getEventIcon(msg.content)}</span>

            {/* 제목 한 줄: [PR edited] 제목텍스트 PR#46 — 모두 inline으로 자연스럽게 흐름 */}
            <div style={{ minWidth: 0, wordBreak: 'break-word' }}>
              <span style={{ fontSize: '13px', color: '#1a1d20', fontWeight: '700' }}>
                {badgeLabel && <span style={{ marginRight: '4px' }}>{badgeLabel}</span>}
                {prUrl ? (
                  <>
                    <a
                      href={prUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{ color: '#0073ca', textDecoration: 'none', fontWeight: '700' }}
                      onMouseEnter={(e) => (e.currentTarget.style.textDecoration = 'underline')}
                      onMouseLeave={(e) => (e.currentTarget.style.textDecoration = 'none')}
                    >
                      {cleanTitle}
                    </a>
                    <span style={{ color: '#1a1d20' }}>
                      {titleText.match(/(\s*by\s+\S+)$/)?.[1]}
                    </span>
                  </>
                ) : (
                  titleText
                )}
              </span>
              {prNumber && (
                <span style={{
                  display: 'inline-block',
                  marginLeft: '6px',
                  fontSize: '12px', fontWeight: '600',
                  backgroundColor: '#18a1e0', color: '#ffffff',
                  borderRadius: '999px', padding: '1px 9px',
                  whiteSpace: 'nowrap',
                  verticalAlign: 'middle',
                }}>
                  PR #{prNumber}
                </span>
              )}
            </div>
          </div>
        </div>

        {bodyLines.length > 0 && (
          <div style={{ marginTop: '6px', color: '#495057', fontSize: '12.5px' }}>
            {bodyLines.map((line, i) => (
              <div key={i}>{renderWithLink(line)}</div>
            ))}
          </div>
        )}

        {subMessages && subMessages.length > 0 && (
          <div style={{ marginTop: '8px', borderTop: '1px dashed #dee2e6', paddingTop: '6px' }}>
            {!expanded && (
              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
                <PrHistoryItem msg={subMessages[subMessages.length - 1]} />
                {subMessages.length > 1 && (
                  <span
                    onClick={() => setExpanded(true)}
                    style={{ fontSize: '11px', color: '#1264a3', cursor: 'pointer', userSelect: 'none' }}
                  >
                    (외 {subMessages.length - 1}개 더 보기)
                  </span>
                )}
              </div>
            )}
            {expanded && (
              <>
                {subMessages.map((sub) => (
                  <PrHistoryItem key={sub.messageId} msg={sub} />
                ))}
                <div
                  onClick={() => setExpanded(false)}
                  style={{
                    fontSize: '11px', color: '#868686',
                    paddingLeft: '12px', marginTop: '2px',
                    cursor: 'pointer', display: 'inline-block', userSelect: 'none',
                  }}
                >
                  접기
                </div>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

/* ── 공통 메시지 분기 필터 컴포넌트 ── */
const MessageContent = ({
  msg, editMessageId, editContent, setEditContent,
  handleEditMessage, setEditMessageId, onCodeClick, onRetryClick,
  subMessages,
}) => {
  const [retrying, setRetrying] = useState(false);

  if (editMessageId === msg.messageId) {
    return (
      <div style={{ display: 'flex', gap: '8px', flexDirection: 'column', marginTop: '4px' }}>
        <textarea
          value={editContent}
          onChange={(e) => setEditContent(e.target.value)}
          style={{
            width: '100%', minHeight: '72px',
            border: '1px solid #1264a3', borderRadius: '6px',
            padding: '8px 10px', fontSize: '14px',
            resize: 'vertical', outline: 'none',
            boxShadow: '0 0 0 3px rgba(18,100,163,0.1)',
            fontFamily: 'inherit', lineHeight: '1.5',
            boxSizing: 'border-box',
          }}
        />
        <div style={{ display: 'flex', gap: '8px' }}>
          <button onClick={() => handleEditMessage(msg.messageId)} style={btnSave}>저장</button>
          <button onClick={() => { setEditMessageId(null); setEditContent(''); }} style={btnCancel}>취소</button>
          <span style={{ fontSize: '12px', color: '#aaa', alignSelf: 'center', marginLeft: '4px' }}>
            Esc로 취소
          </span>
        </div>
      </div>
    );
  }

  if (msg.status === 'DELETED') {
    return (
      <div style={{ fontSize: '14px', color: '#bbb', fontStyle: 'italic' }}>
        이 메시지는 삭제되었습니다.
      </div>
    );
  }

  if (msg.type === 'GIT') {
    return <GitMessageContent msg={msg} subMessages={subMessages} />;
  }

  if (msg.type === 'AI_REVIEW') {
    const status = msg.aiReviewStatus;
    const prStatus = msg.prStatus;
    const isClosed = prStatus === 'CLOSED' || prStatus === 'MERGED';

    const accentColor = status === 'FAIL' ? '#e53e3e' : '#007aff';
    const shadowColor = status === 'FAIL' ? 'rgba(229, 62, 62, 0.12)' : 'rgba(18, 100, 163, 0.1)';
    const bgColor = status === 'FAIL' ? '#fff5f5' : '#f0f4f8';

    return (
      <div style={{
        display: 'flex',
        backgroundColor: bgColor,
        borderRadius: '6px',
        overflow: 'hidden',
        boxShadow: `0 2px 5px ${shadowColor}, 0 1px 2px rgba(0, 0, 0, 0.02)`,
        maxWidth: '600px',
        margin: '6px 0',
      }}>
        <div style={{ width: '3px', backgroundColor: accentColor, flexShrink: 0 }} />

        <div style={{ padding: '10px 14px', fontSize: '13px', color: '#212529', lineHeight: '1.5', flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
            <span style={{ fontWeight: '700', color: '#1a1d20', fontSize: '13px' }}>
              🤖 AI Code Review
            </span>
            <span style={{
              fontSize: '12px', fontWeight: '600',
              backgroundColor: '#18a1e0', color: '#ffffff',
              borderRadius: '999px', padding: '1px 9px',
              flexShrink: 0, whiteSpace: 'nowrap',
            }}>
              PR #{msg.prNumber}
            </span>
            {isClosed && (
              <span style={{
                backgroundColor: prStatus === 'MERGED' ? '#6f42c1' : '#e53e3e',
                color: 'white',
                padding: '2px 8px', borderRadius: '12px',
                fontSize: '11px', fontWeight: '500',
              }}>
                {prStatus === 'MERGED' ? '🔀 병합됨' : '🚫 닫힘'}
              </span>
            )}
          </div>

          <div style={{ marginTop: '6px' }}>
            <div style={{ fontSize: '12px', color: '#495057', fontWeight: '500', marginBottom: '6px' }}>
              {status === 'SUCCESS' && msg.publishedBy && (
                <span>Posted by {msg.publishedBy}</span>
              )}
              {(status === 'PENDING' || retrying) && (
                <span style={{ color: '#6c757d' }}>⏳ 리뷰 생성 중...</span>
              )}
              {status === 'FAIL' && !retrying && (
                <span style={{ color: '#cf222e', fontWeight: '600' }}>❌ 리뷰 생성 실패</span>
              )}
              {status === 'SKIPPED' && (
                <span style={{ color: '#9a6700' }}>⏭️ 크기 제한 초과로 생략됨</span>
              )}
            </div>

            <div style={{ display: 'flex' }}>
              {status === 'SUCCESS' && (
                <button
                  onClick={() => onCodeClick && onCodeClick(msg)}
                  style={{
                    backgroundColor: '#18a1e0', color: '#ffffff',
                    border: 'none', borderRadius: '4px',
                    padding: '4px 12px', fontSize: '11.5px',
                    fontWeight: '700', cursor: 'pointer',
                    boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
                    transition: 'all 0.1s',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#0b4d7c')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#18a1e0')}
                >
                  리뷰 보기
                </button>
              )}
              {status === 'FAIL' && !retrying && (
                <button
                  onClick={() => {
                    setRetrying(true);
                    onRetryClick?.(msg.prNumber);
                  }}
                  style={{
                    backgroundColor: '#e53e3e', color: '#ffffff',
                    border: 'none', borderRadius: '4px',
                    padding: '4px 12px', fontSize: '11.5px',
                    fontWeight: '700', cursor: 'pointer',
                    boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#c53030')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#e53e3e')}
                >
                  재시도
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (msg.type === 'CODE') {
    return (
      <div>
        <div style={{ border: '1px solid #e8e8e8', borderRadius: '6px', overflow: 'hidden' }}>
          <HighlightedCode
            content={msg.content}
            language={msg.language || 'java'}
            onClick={() => onCodeClick && onCodeClick(msg)}
          />
        </div>
        {msg.status === 'EDITED' && <EditedLabel />}
      </div>
    );
  }

  if (msg.type === 'IMAGE') {
    return (
      <div style={{ marginTop: '4px', display: 'inline-block', maxWidth: '360px' }}>
        <img
          src={`${process.env.REACT_APP_CHAT_IMAGE_URL}/${msg.chatImageUrl}`}
          alt="이미지"
          style={{
            display: 'block', width: '100%', maxHeight: '360px',
            objectFit: 'contain', borderRadius: '8px',
            border: '1px solid #f0f0f0',
          }}
        />
      </div>
    );
  }

  if (msg.type === 'EVENT') {
    return (
      <div style={{
        display: 'flex', justifyContent: 'center',
        margin: '10px 0', padding: '0 16px',
      }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: '7px',
          backgroundColor: '#f8f9fa', borderRadius: '20px',
          padding: '6px 14px', fontSize: '12px', color: '#666',
          border: '1px solid #ebebeb',
        }}>
          <FaUserPlus size={11} color="#aaa" />
          <span>{msg.content}</span>
          <span style={{ color: '#ccc', marginLeft: '2px' }}>
            {formatTime(msg.createdAt || msg.joinAt)}
          </span>
        </div>
      </div>
    );
  }

  return (
    <div style={{ fontSize: '14px', lineHeight: '1.6', color: '#1d1c1d', wordBreak: 'break-word', whiteSpace: 'pre-wrap' }}>
      {renderWithLink(msg.content)}
      {msg.status === 'EDITED' && <EditedLabel />}
    </div>
  );
};

const EditedLabel = () => (
  <span style={{ marginLeft: '5px', fontSize: '11px', color: '#ccc', fontStyle: 'italic' }}>(수정됨)</span>
);

/* ── 단일 메시지 레이아웃 아이템 ── */
export const MessageItem = ({
  msg, currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent, handleEditMessage,
  handleDeleteMessage, editMessageId, editContent, onCodeClick, onRetryClick,
  subMessages,
}) => {
  const [isHovered, setIsHovered] = useState(false);

  if (msg.type === 'EVENT') {
    return (
      <MessageContent
        msg={msg}
        editMessageId={editMessageId}
        editContent={editContent}
        setEditContent={setEditContent}
        handleEditMessage={handleEditMessage}
        setEditMessageId={setEditMessageId}
      />
    );
  }

  const isMenuOpen = contextMenuId === msg.messageId;
  const isOwn = currentUser?.id === msg.senderId;

  const profileSrc = msg.profileImageUrl
    ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${msg.profileImageUrl}`
    : '/images/not-found-profile.png';

  return (
    <div
      id={`message-${msg.messageId}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        padding: '3px 16px',
        borderRadius: '4px',
        backgroundColor: isMenuOpen ? '#f8f8f8' : isHovered ? '#f8f8f8' : 'transparent',
        transition: 'background-color 0.1s',
        position: 'relative',
        marginBottom: '2px',
      }}
    >
      <img
        src={profileSrc}
        alt="프로필"
        width={36}
        height={36}
        loading="eager"
        onError={(e) => { e.currentTarget.src = '/images/not-found-profile.png'; }}
        style={{
          borderRadius: '4px',
          marginRight: '10px',
          objectFit: 'cover',
          flexShrink: 0,
          marginTop: '2px',
        }}
      />

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', marginBottom: '3px' }}>
          <span style={{ fontWeight: '700', fontSize: '14px', color: '#1d1c1d' }}>
            {msg.senderName}
          </span>
          <span style={{ fontSize: '11px', color: '#aaa' }}>
            {formatTime(msg.createdAt)}
          </span>
        </div>

        <MessageContent
          msg={msg}
          editMessageId={editMessageId}
          editContent={editContent}
          setEditContent={setEditContent}
          handleEditMessage={handleEditMessage}
          setEditMessageId={setEditMessageId}
          onCodeClick={onCodeClick}
          onRetryClick={onRetryClick}
          subMessages={subMessages}
        />
      </div>

      {isOwn && !(msg.status === 'DELETED') && (isHovered || isMenuOpen) && (
        <div style={{
          position: 'absolute',
          top: '4px',
          right: '16px',
          display: 'flex',
          gap: '2px',
          zIndex: 10,
        }}>
          {msg.type !== 'IMAGE' && msg.type !== 'AI_REVIEW' && (
            <ActionBtn
              label="수정"
              onClick={() => {
                setEditMessageId(msg.messageId);
                setEditContent(msg.content);
                setContextMenuId(null);
              }}
            />
          )}
          <ActionBtn
            label="삭제"
            danger
            onClick={() => {
              if (window.confirm('정말 삭제하시겠습니까?')) handleDeleteMessage(msg.messageId);
              setContextMenuId(null);
            }}
          />
        </div>
      )}
    </div>
  );
};

/* ── 호버 제어 버튼 ── */
const ActionBtn = ({ label, onClick, danger = false }) => {
  const [hovered, setHovered] = useState(false);
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        padding: '4px 10px',
        fontSize: '12px',
        fontWeight: '500',
        border: `1px solid ${danger ? (hovered ? '#e01e5a' : '#f0d0d8') : (hovered ? '#aaa' : '#e8e8e8')}`,
        borderRadius: '4px',
        backgroundColor: danger ? (hovered ? '#e01e5a' : '#fff') : (hovered ? '#f0f0f0' : '#fff'),
        color: danger ? (hovered ? '#fff' : '#e01e5a') : '#555',
        cursor: 'pointer',
        transition: 'all 0.1s',
        lineHeight: 1,
      }}
    >
      {label}
    </button>
  );
};

/* ── 날짜 선 구분자 ── */
export const DateDivider = ({ label }) => (
  <div style={{
    display: 'flex', alignItems: 'center',
    margin: '20px 16px 12px',
  }}>
    <div style={{ flex: 1, height: '1px', backgroundColor: '#f0f0f0' }} />
    <span style={{
      margin: '0 14px', padding: '3px 12px',
      backgroundColor: '#fff', border: '1px solid #ebebeb',
      borderRadius: '12px', fontSize: '12px', color: '#888',
      fontWeight: '500', whiteSpace: 'nowrap',
    }}>
      {label}
    </span>
    <div style={{ flex: 1, height: '1px', backgroundColor: '#f0f0f0' }} />
  </div>
);

/* ── GIT 피드 메시지 그룹핑 유틸 함수 ── */
const groupGitMessages = (messages) => {
  const prFirstMessageId = new Map();
  const subMessagesMap = new Map();
  const hiddenIds = new Set();

  messages.forEach((msg) => {
    if (msg.type !== 'GIT' || msg.prNumber == null) return;

    const existingId = prFirstMessageId.get(msg.prNumber);
    if (existingId == null) {
      prFirstMessageId.set(msg.prNumber, msg.messageId);
      subMessagesMap.set(msg.messageId, []);
    } else {
      subMessagesMap.get(existingId).push(msg);
      hiddenIds.add(msg.messageId);
    }
  });

  return { subMessagesMap, hiddenIds };
};

/* ── 최상위 메인 메시지 리스트 컨테이너 컴포넌트 ── */
const MessageList = ({
  messages, currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent, editMessageId, editContent,
  handleEditMessage, handleDeleteMessage, onCodeClick, onRetryClick,
}) => {
  if (!messages.length) return null;

  const { subMessagesMap, hiddenIds } = groupGitMessages(messages);

  const result = [];
  let currentDate = null;

  messages.forEach((msg, index) => {
    if (hiddenIds.has(msg.messageId)) return;

    const label = formatDate(msg.createdAt || msg.joinAt);
    if (label !== currentDate) {
      currentDate = label;
      result.push(<DateDivider key={`date-${index}`} label={label} />);
    }

    const subMessages = subMessagesMap.get(msg.messageId) || [];

    result.push(
      <MessageItem
        key={`msg-${msg.messageId ?? index}`}
        msg={msg}
        currentUser={currentUser}
        contextMenuId={contextMenuId}
        setContextMenuId={setContextMenuId}
        setEditMessageId={setEditMessageId}
        setEditContent={setEditContent}
        handleDeleteMessage={handleDeleteMessage}
        editMessageId={editMessageId}
        editContent={editContent}
        handleEditMessage={handleEditMessage}
        onCodeClick={onCodeClick}
        onRetryClick={onRetryClick}
        subMessages={subMessages}
      />
    );
  });

  return <>{result}</>;
};

/* ── 하단 공통 제어 스타일 컴포넌트 스펙 ── */
const btnSave = {
  backgroundColor: '#007a5a', color: 'white',
  border: 'none', borderRadius: '4px',
  padding: '6px 14px', fontSize: '13px',
  fontWeight: '600', cursor: 'pointer',
};
const btnCancel = {
  backgroundColor: 'transparent', color: '#555',
  border: '1px solid #e0e0e0', borderRadius: '4px',
  padding: '6px 14px', fontSize: '13px',
  cursor: 'pointer',
};

export default MessageList;