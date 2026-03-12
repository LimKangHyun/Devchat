import React, { useState } from 'react';
import Highlight from 'react-highlight';
import { FaUserPlus } from 'react-icons/fa';

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

/* ── 코드 블록 ── */
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

/* ── 메시지 본문 ── */
const MessageContent = ({ msg, editMessageId, editContent, setEditContent, handleEditMessage, setEditMessageId, onCodeClick }) => {

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
    return (
      <div style={{
        display: 'flex', backgroundColor: '#f6f8fa',
        borderRadius: '6px', overflow: 'hidden',
        border: '1px solid #e8e8e8',
      }}>
        <div style={{ width: '4px', backgroundColor: '#24292e', flexShrink: 0 }} />
        <div style={{ whiteSpace: 'pre-wrap', lineHeight: '1.6', padding: '10px 14px', fontSize: '13px', color: '#24292e' }}>
          {msg.content.split('\n').map((line, i) => (
            <div key={i}>{i === 0 ? <strong>{line}</strong> : renderWithLink(line)}</div>
          ))}
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
            {formatTime(msg.sendAt || msg.joinAt)}
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

/* ── 메시지 아이템 ── */
const MessageItem = ({
  msg, currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent, handleEditMessage,
  handleDeleteMessage, editMessageId, editContent, onCodeClick,
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

  return (
    <div
      id={`message-${msg.messageId}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => { setIsHovered(false); }}
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
      {/* 프로필 이미지 */}
      <img
        src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${msg.profileImageUrl}`}
        alt="프로필"
        width={36}
        height={36}
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
        {/* 발신자 + 시간 */}
        <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px', marginBottom: '3px' }}>
          <span style={{ fontWeight: '700', fontSize: '14px', color: '#1d1c1d' }}>
            {msg.senderName}
          </span>
          <span style={{ fontSize: '11px', color: '#aaa' }}>
            {formatTime(msg.sendAt)}
          </span>
        </div>

        {/* 메시지 본문 */}
        <MessageContent
          msg={msg}
          editMessageId={editMessageId}
          editContent={editContent}
          setEditContent={setEditContent}
          handleEditMessage={handleEditMessage}
          setEditMessageId={setEditMessageId}
          onCodeClick={onCodeClick}
        />
      </div>

      {/* 호버 액션 버튼 (본인 메시지만) */}
      {isOwn && !(msg.status === 'DELETED') && (isHovered || isMenuOpen) && (
        <div style={{
          position: 'absolute',
          top: '4px',
          right: '16px',
          display: 'flex',
          gap: '2px',
          zIndex: 10,
        }}>
          {msg.type !== 'IMAGE' && (
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

/* ── 호버 액션 버튼 ── */
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

/* ── 날짜 구분선 ── */
const DateDivider = ({ label }) => (
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

/* ── 메시지 리스트 ── */
const MessageList = ({
  messages, currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent, editMessageId, editContent,
  handleEditMessage, handleDeleteMessage, onCodeClick,
}) => {
  if (!messages.length) return null;

  const result = [];
  let currentDate = null;

  messages.forEach((msg, index) => {
    const label = formatDate(msg.sendAt || msg.joinAt);
    if (label !== currentDate) {
      currentDate = label;
      result.push(<DateDivider key={`date-${index}`} label={label} />);
    }
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
      />
    );
  });

  return <>{result}</>;
};

/* ── 버튼 스타일 ── */
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