import React, { useState } from 'react';
import Highlight from 'react-highlight';
import { UserPlus, Pencil, Trash2, GitPullRequest, Bot, ExternalLink, Maximize2, Reply } from 'lucide-react';
import s from './Chat.module.css';
import { splitMentions } from '../../utils/mention';

/* ── 유틸리티 함수 ── */
const formatDate = (dateString) => {
  const date = new Date(dateString);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  if (date.toDateString() === today.toDateString()) return '오늘';
  if (date.toDateString() === yesterday.toDateString()) return '어제';
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
};

const formatTime = (dateString) => {
  return new Date(dateString).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
};

const formatShortTime = (dateString) =>
  new Date(dateString).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: false });

const GROUP_GAP_MS = 5 * 60 * 1000;

// 같은 사람이 5분 안에 연달아 보낸 메시지는 이름/아바타 없이 이어서 보여준다
const isSameGroup = (a, b) => {
  if (!a || !b) return false;
  if (a.type === 'EVENT' || b.type === 'EVENT') return false;
  if (a.senderId == null || a.senderId !== b.senderId) return false;
  const da = new Date(a.createdAt);
  const db = new Date(b.createdAt);
  if (da.toDateString() !== db.toDateString()) return false;
  return Math.abs(db - da) < GROUP_GAP_MS;
};

// 링크와 @멘션을 표시한다. split에 캡처 그룹을 쓰면 홀수 번째 조각이 URL이다
const renderWithLink = (text) => {
  const urlRegex = /(https?:\/\/[^\s]+)/;
  return text.split(urlRegex).map((part, i) =>
    i % 2 === 1 ? (
      <a key={i} href={part} target="_blank" rel="noopener noreferrer" className={s.link}>
        {part}
      </a>
    ) : (
      <React.Fragment key={i}>
        {splitMentions(part).map((p, j) =>
          p.mention ? <span key={j} className={s.mention}>{p.text}</span> : p.text
        )}
      </React.Fragment>
    )
  );
};

/* ── 코드 블록 ── */
const HighlightedCode = ({ content, language, onClick }) => (
  <div className={s.codeBlock} onClick={onClick} role="button" tabIndex={0}>
    <div className={s.codeBlockBar}>
      <span>{language}</span>
      <span className={s.codeBlockOpen}><Maximize2 size={12} /> 리뷰 열기</span>
    </div>
    <div className={s.codeBlockBody}>
      <Highlight className={language}>{content}</Highlight>
    </div>
  </div>
);

/* ── PR 히스토리 서브 아이템 ── */
const PrHistoryItem = ({ msg }) => {
  const firstLine = (msg.content || '').split('\n')[0];
  const match = firstLine.match(/^\[([^\]]+)\]/);
  const label = match ? match[1] : 'PR 업데이트';

  return (
    <div className={s.prHistoryItem}>
      <span className={s.prHistoryArrow}>↳</span>
      <span className={s.prHistoryLabel}>{label}</span>
      <span className={s.prHistoryTime}>{formatTime(msg.createdAt)}</span>
    </div>
  );
};

const PrBadge = ({ number }) => (
  <span className={s.prBadge}>
    <GitPullRequest size={11} />#{number}
  </span>
);

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
  const badgeMatch = firstLine.match(/^[^[]*(\[[^\]]+\])\s*(.*)/);
  const badgeLabel = badgeMatch ? badgeMatch[1].replace(/[[\]]/g, '') : null;
  const titleText = badgeMatch ? badgeMatch[2] : firstLine;
  const cleanTitle = titleText.replace(/\s*by\s+\S+$/, '').trim();
  const byText = titleText.match(/(\s*by\s+\S+)$/)?.[1];

  const bodyLines = (msg.content || '')
    .split('\n')
    .slice(1)
    .filter((line) => !/^https?:\/\//.test(line.trim()));

  return (
    <div className={s.attachment}>
      <div className={s.attachmentInner}>
        {badgeLabel && <div className={s.attachmentPretext}>{badgeLabel}</div>}
        <div className={s.attachmentTitle}>
          {prUrl ? (
            <a href={prUrl} target="_blank" rel="noopener noreferrer" className={s.attachmentLink}>{cleanTitle}</a>
          ) : (
            <span>{titleText}</span>
          )}
          {prNumber && <PrBadge number={prNumber} />}
        </div>
        {prUrl && byText && <div className={s.attachmentMeta}>{byText.trim()}</div>}

        {bodyLines.length > 0 && (
          <div className={s.attachmentBody}>
            {bodyLines.map((line, i) => (
              <div key={i}>{renderWithLink(line)}</div>
            ))}
          </div>
        )}

        {subMessages && subMessages.length > 0 && (
          <div className={s.prHistory}>
            {!expanded && (
              <div className={s.prHistoryCollapsed}>
                <PrHistoryItem msg={subMessages[subMessages.length - 1]} />
                {subMessages.length > 1 && (
                  <button type="button" className={s.textButton} onClick={() => setExpanded(true)}>
                    외 {subMessages.length - 1}개 더 보기
                  </button>
                )}
              </div>
            )}
            {expanded && (
              <>
                {subMessages.map((sub) => (
                  <PrHistoryItem key={sub.messageId} msg={sub} />
                ))}
                <button type="button" className={s.textButton} onClick={() => setExpanded(false)}>
                  접기
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

/* ── AI 리뷰 카드 ── */
const AiReviewContent = ({ msg, onCodeClick, onRetryClick }) => {
  const [retrying, setRetrying] = useState(false);
  const status = msg.aiReviewStatus;
  const prStatus = msg.prStatus;
  const isClosed = prStatus === 'CLOSED' || prStatus === 'MERGED';
  const isFail = status === 'FAIL';

  return (
    <div className={`${s.attachment} ${isFail ? s.attachmentDanger : ''}`}>
      <div className={s.attachmentInner}>
        <div className={s.attachmentTitle}>
          <Bot size={15} className={s.attachmentIcon} />
          <span>AI 코드 리뷰</span>
          <PrBadge number={msg.prNumber} />
          {isClosed && (
            <span className={s.statusPill}>{prStatus === 'MERGED' ? '병합됨' : '닫힘'}</span>
          )}
        </div>

        <div className={s.attachmentFooter}>
          <div className={s.attachmentMeta}>
            {status === 'SUCCESS' && msg.publishedBy && <span>{msg.publishedBy}님이 게시함</span>}
            {status === 'SUCCESS' && !msg.publishedBy && <span>리뷰가 준비됐어요</span>}
            {(status === 'PENDING' || retrying) && (
              <span className={s.pendingText}><span className={s.miniSpinner} /> 리뷰 생성 중...</span>
            )}
            {isFail && !retrying && <span className={s.dangerText}>리뷰 생성에 실패했어요</span>}
            {status === 'SKIPPED' && <span>변경 크기가 커서 리뷰를 건너뛰었어요</span>}
          </div>

          {status === 'SUCCESS' && (
            <button type="button" className="dc-btn" style={{ height: 30, fontSize: 13 }} onClick={() => onCodeClick && onCodeClick(msg)}>
              리뷰 보기 <ExternalLink size={12} />
            </button>
          )}
          {isFail && !retrying && (
            <button
              type="button"
              className="dc-btn"
              style={{ height: 30, fontSize: 13 }}
              onClick={() => { setRetrying(true); onRetryClick?.(msg.prNumber); }}
            >
              다시 시도
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

/* ── 답장 원문 미리보기: 답장한 순간의 원문 그대로 (누르면 원문으로 이동) ── */
const ReplyQuote = ({ reply, onJump }) => (
  <button
    type="button"
    className={s.replyQuote}
    onClick={() => onJump?.(reply.messageId)}
    aria-label={`${reply.senderName || '알 수 없음'}님의 메시지로 이동`}
  >
    <span className={s.replyQuoteName}>{reply.senderName || '알 수 없음'}</span>
    <span className={s.replyQuoteText}>{reply.preview}</span>
  </button>
);

/* ── 시스템 이벤트 (입장 등) ── */
const EventMessage = ({ msg }) => (
  <div className={s.eventRow}>
    <div className={s.gutter}>
      <span className={s.eventIcon}><UserPlus size={14} /></span>
    </div>
    <div className={s.eventText}>
      {msg.content}
      <span className={s.eventTime}>{formatTime(msg.createdAt || msg.joinAt)}</span>
    </div>
  </div>
);

/* ── 단일 메시지 ── */
export const MessageItem = ({
  msg, prevMsg, nextMsg, currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent, handleEditMessage,
  handleDeleteMessage, editMessageId, editContent, onCodeClick, onRetryClick,
  subMessages, onReply, onJumpTo,
}) => {
  const [isHovered, setIsHovered] = useState(false);

  if (msg.type === 'EVENT') return <EventMessage msg={msg} />;

  const isMenuOpen = contextMenuId === msg.messageId;
  const isOwn = currentUser?.id === msg.senderId;
  const isDeleted = msg.status === 'DELETED';
  const isEditing = editMessageId === msg.messageId;
  const isFirstInGroup = !isSameGroup(prevMsg, msg);

  const profileSrc = msg.profileImageUrl
    ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${msg.profileImageUrl}`
    : '/images/not-found-profile.webp';

  const canEdit = isOwn && !isDeleted && msg.type !== 'IMAGE' && msg.type !== 'AI_REVIEW' && msg.type !== 'GIT';
  const canDelete = isOwn && !isDeleted;
  const canReply = !!onReply && !isDeleted && msg.messageId != null;

  const renderBody = () => {
    if (isEditing) {
      return (
        <div className={s.editBox}>
          <textarea
            value={editContent}
            onChange={(e) => setEditContent(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') { setEditMessageId(null); setEditContent(''); }
            }}
            autoFocus
            className={s.editTextarea}
          />
          <div className={s.editActions}>
            <span className={s.editHint}>Esc로 취소</span>
            <button type="button" className="dc-btn" style={{ height: 30, fontSize: 13 }} onClick={() => { setEditMessageId(null); setEditContent(''); }}>취소</button>
            <button type="button" className="dc-btn dc-btn-primary" style={{ height: 30, fontSize: 13 }} onClick={() => handleEditMessage(msg.messageId)}>저장</button>
          </div>
        </div>
      );
    }

    if (isDeleted) {
      return <div className={s.deletedText}>이 메시지는 삭제되었습니다.</div>;
    }

    if (msg.type === 'GIT') return <GitMessageContent msg={msg} subMessages={subMessages} />;
    if (msg.type === 'AI_REVIEW') return <AiReviewContent msg={msg} onCodeClick={onCodeClick} onRetryClick={onRetryClick} />;

    if (msg.type === 'CODE') {
      return (
        <HighlightedCode
          content={msg.content}
          language={msg.language || 'java'}
          onClick={() => onCodeClick && onCodeClick(msg)}
        />
      );
    }

    if (msg.type === 'IMAGE') {
      return (
        <div className={s.imageWrap}>
          <img
            src={`${process.env.REACT_APP_CHAT_IMAGE_URL}/${msg.chatImageUrl}`}
            alt="첨부 이미지"
            className={s.image}
          />
        </div>
      );
    }

    return (
      <div className={s.text}>
        {renderWithLink(msg.content)}
        {msg.status === 'EDITED' && <span className={s.edited}>(편집됨)</span>}
      </div>
    );
  };

  const showToolbar = (canReply || canEdit || canDelete) && !isEditing && (isHovered || isMenuOpen);

  return (
    <div
      id={`message-${msg.messageId}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className={`${s.msgRow} ${isFirstInGroup ? s.msgFirst : s.msgCont} ${isEditing ? s.msgEditing : ''}`}
    >
      <div className={s.gutter}>
        {isFirstInGroup ? (
          <img
            src={profileSrc}
            alt=""
            width={36}
            height={36}
            loading="eager"
            onError={(e) => { e.currentTarget.src = '/images/not-found-profile.webp'; }}
            className={s.avatar}
          />
        ) : (
          <span className={s.hoverTime}>{formatShortTime(msg.createdAt)}</span>
        )}
      </div>

      <div className={s.msgMain}>
        {isFirstInGroup && (
          <div className={s.msgMeta}>
            <span className={s.senderName}>{msg.senderName}</span>
            {isOwn && <span className={s.youTag}>나</span>}
            <span className={s.msgTime}>{formatTime(msg.createdAt)}</span>
          </div>
        )}
        {msg.replyTo && !isDeleted && !isEditing && <ReplyQuote reply={msg.replyTo} onJump={onJumpTo} />}
        {renderBody()}
      </div>

      {showToolbar && (
        <div className={s.toolbar}>
          {canReply && (
            <ActionBtn
              label="답장"
              icon={<Reply size={15} />}
              onClick={() => { onReply(msg); setIsHovered(false); setContextMenuId(null); }}
            />
          )}
          {canEdit && (
            <ActionBtn
              label="메시지 편집"
              icon={<Pencil size={15} />}
              onClick={() => {
                setEditMessageId(msg.messageId);
                setEditContent(msg.content);
                setContextMenuId(null);
              }}
            />
          )}
          {canDelete && (
            <ActionBtn
              label="메시지 삭제"
              danger
              icon={<Trash2 size={15} />}
              onClick={() => {
                if (window.confirm('정말 삭제하시겠습니까?')) handleDeleteMessage(msg.messageId);
                setContextMenuId(null);
              }}
            />
          )}
        </div>
      )}
    </div>
  );
};

/* ── 호버 툴바 버튼 ── */
const ActionBtn = ({ label, icon, onClick, danger = false }) => (
  <button
    type="button"
    onClick={onClick}
    title={label}
    aria-label={label}
    className={`${s.toolbarBtn} ${danger ? s.toolbarBtnDanger : ''}`}
  >
    {icon}
  </button>
);

/* ── 날짜 구분자 ── */
export const DateDivider = ({ label }) => (
  <div className={s.dateDivider}>
    <span className={s.dateDividerLabel}>{label}</span>
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

/* ── 메시지 리스트 (가상 스크롤 없이 쓸 때) ── */
const MessageList = ({
  messages, currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent, editMessageId, editContent,
  handleEditMessage, handleDeleteMessage, onCodeClick, onRetryClick,
}) => {
  if (!messages.length) return null;

  const { subMessagesMap, hiddenIds } = groupGitMessages(messages);
  const visible = messages.filter((m) => !hiddenIds.has(m.messageId));

  const result = [];
  let currentDate = null;

  visible.forEach((msg, index) => {
    const label = formatDate(msg.createdAt || msg.joinAt);
    if (label !== currentDate) {
      currentDate = label;
      result.push(<DateDivider key={`date-${index}`} label={label} />);
    }

    result.push(
      <MessageItem
        key={`msg-${msg.messageId ?? index}`}
        msg={msg}
        prevMsg={visible[index - 1]}
        nextMsg={visible[index + 1]}
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
        subMessages={subMessagesMap.get(msg.messageId) || []}
      />
    );
  });

  return <>{result}</>;
};

export default MessageList;
