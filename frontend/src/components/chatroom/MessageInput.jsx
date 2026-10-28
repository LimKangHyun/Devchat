import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ImagePlus, Code2, SendHorizontal, X, Reply } from 'lucide-react';
import s from './Chat.module.css';
import { handleCodeKeyDown } from './codeEditorKeys';
import { getMentionQuery } from '../../utils/mention';
import { compressImage, CHAT_IMAGE_OPTIONS, MAX_UPLOAD_BYTES, formatBytes } from '../../utils/imageCompress';
import { MAX_MESSAGE_LENGTH, SHOW_COUNT_FROM, TOO_LONG_MESSAGE, isTooLong } from '../../utils/messageLimits';
import { replyPreviewOf } from '../../utils/replyPreview';

const MAX_MENTION_CANDIDATES = 6;

// 손가락으로 쓰는 기기(휴대폰·태블릿). 리턴을 줄바꿈으로 쓰고 전송은 버튼으로 한다
const isTouchDevice = typeof window !== 'undefined' && !!window.matchMedia?.('(pointer: coarse)').matches;

const LANGUAGES = [
  ['javascript', 'JavaScript'],
  ['java', 'Java'],
  ['python', 'Python'],
  ['html', 'HTML'],
  ['css', 'CSS'],
];

const MessageInput = ({
  inputMode,
  setInputMode,
  content,
  setContent,
  language,
  setLanguage,
  handleUnifiedSend,
  setImageFile,
  imagePreviewUrl,
  setImagePreviewUrl,
  roomName,
  mentionCandidates = [],
  blockedSeconds = 0, // 너무 빠르게 보내 막힌 동안 남은 초
  replyTarget = null, // 답장할 메시지 (없으면 null)
  onCancelReply,
  onTyping, // 입력 내용이 바뀔 때 (입력 중 표시용)
}) => {
  const fileInputRef = useRef(null);
  const textareaRef = useRef(null);
  const isComposingRef = useRef(false);
  const [isFocused, setIsFocused] = useState(false);
  // @멘션 자동완성: { start: '@' 위치, query }
  const [mention, setMention] = useState(null);
  const [activeIndex, setActiveIndex] = useState(0);

  // 답장을 고르면 바로 입력할 수 있게 입력창에 커서를 둔다
  useEffect(() => {
    if (replyTarget && textareaRef.current && !textareaRef.current.disabled) textareaRef.current.focus();
  }, [replyTarget]);

  const candidates = useMemo(() => {
    if (!mention) return [];
    const q = mention.query.toLowerCase();
    return mentionCandidates
      .filter((p) => p.nickname.toLowerCase().includes(q))
      .slice(0, MAX_MENTION_CANDIDATES);
  }, [mention, mentionCandidates]);
  const mentionOpen = inputMode === 'TEXT' && candidates.length > 0;

  const updateMention = (text, caret) => {
    const next = inputMode === 'TEXT' ? getMentionQuery(text, caret) : null;
    setMention(next);
    if (!next || next.query !== mention?.query) setActiveIndex(0);
  };

  const selectMention = (p) => {
    const el = textareaRef.current;
    const caret = el ? el.selectionStart : content.length;
    const inserted = `@${p.nickname} `;
    const next = content.slice(0, mention.start) + inserted + content.slice(caret);
    setContent(next);
    setMention(null);
    const pos = mention.start + inserted.length;
    requestAnimationFrame(() => {
      if (!textareaRef.current) return;
      textareaRef.current.focus();
      textareaRef.current.setSelectionRange(pos, pos);
    });
  };

  const handleModeToggle = (mode) => {
    const next = inputMode === mode ? 'TEXT' : mode;
    setInputMode(next);
    if (next === 'IMAGE' && fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const clearImage = () => {
    setImageFile(null);
    setImagePreviewUrl(null);
    setInputMode('TEXT');
    if (fileInputRef.current) fileInputRef.current.value = null;
  };

  const tooLong = inputMode !== 'IMAGE' && isTooLong(content);
  const showCount = inputMode !== 'IMAGE' && content.length >= SHOW_COUNT_FROM;
  const blocked = blockedSeconds > 0;
  const canSend = !blocked && (inputMode === 'IMAGE' ? !!imagePreviewUrl : content.trim().length > 0 && !tooLong);
  const isCode = inputMode === 'CODE';
  const target = roomName || '이 방';

  return (
    <div className={`${s.composer} ${isFocused ? s.composerFocused : ''}`}>
      {replyTarget && (
        <div className={s.replyBar}>
          <Reply size={14} className={s.replyBarIcon} />
          <div className={s.replyBarBody}>
            <span className={s.replyBarName}>{replyTarget.senderName}님에게 답장</span>
            <span className={s.replyBarText}>{replyPreviewOf(replyTarget)}</span>
          </div>
          <button type="button" className={s.replyBarClose} onClick={onCancelReply} aria-label="답장 취소" title="답장 취소 (Esc)">
            <X size={14} />
          </button>
        </div>
      )}
      {inputMode === 'IMAGE' && imagePreviewUrl && (
        <div className={s.composerAttachments}>
          <div className={s.previewThumb}>
            <img src={imagePreviewUrl} alt="미리보기" />
            <button type="button" onClick={clearImage} aria-label="이미지 제거" className={s.previewRemove}>
              <X size={12} />
            </button>
          </div>
        </div>
      )}

      {mentionOpen && (
        <ul className={s.mentionMenu} role="listbox" aria-label="멘션할 참여자">
          {candidates.map((p, i) => (
            <li
              key={p.memberId}
              role="option"
              aria-selected={i === activeIndex}
              className={`${s.mentionItem} ${i === activeIndex ? s.mentionItemActive : ''}`}
              // blur보다 먼저 처리해야 선택이 사라지지 않는다
              onMouseDown={(e) => { e.preventDefault(); selectMention(p); }}
              onMouseEnter={() => setActiveIndex(i)}
            >
              <img
                className={s.mentionAvatar}
                src={p.profileImageUrl ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${p.profileImageUrl}` : '/images/not-found-profile.webp'}
                alt=""
                onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = '/images/not-found-profile.webp'; }}
              />
              <span>{p.nickname}</span>
            </li>
          ))}
        </ul>
      )}

      <textarea
        ref={textareaRef}
        rows={1}
        disabled={inputMode === 'IMAGE'}
        // 채팅 메시지 칸임을 알려 휴대폰 키보드가 연락처·주소 자동완성을 띄우지 않게 한다
        // (오타 교정·단어 추천은 카톡처럼 그대로 둔다)
        name="chat-message"
        autoComplete="off"
        aria-autocomplete="none"
        data-form-type="other"
        data-lpignore="true"
        enterKeyHint={isTouchDevice ? 'enter' : 'send'}
        value={content}
        onChange={(e) => {
          setContent(e.target.value);
          updateMention(e.target.value, e.target.selectionStart);
          onTyping?.(e.target.value);
        }}
        onSelect={(e) => updateMention(e.target.value, e.target.selectionStart)}
        onFocus={() => setIsFocused(true)}
        onBlur={() => { setIsFocused(false); setMention(null); }}
        onCompositionStart={() => (isComposingRef.current = true)}
        onCompositionEnd={() => (isComposingRef.current = false)}
        wrap={isCode ? 'off' : undefined}
        spellCheck={isCode ? false : undefined}
        onKeyDown={(e) => {
          if (isCode) {
            if (isComposingRef.current) return;
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
              e.preventDefault();
              handleUnifiedSend();
              return;
            }
            // Esc 로 입력창을 빠져나갈 수 있게 (Tab 이 들여쓰기로 쓰이므로)
            if (e.key === 'Escape') {
              e.currentTarget.blur();
              return;
            }
            handleCodeKeyDown(e, language);
            return;
          }
          if (mentionOpen && !isComposingRef.current) {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
              e.preventDefault();
              const step = e.key === 'ArrowDown' ? 1 : -1;
              setActiveIndex((i) => (i + step + candidates.length) % candidates.length);
              return;
            }
            if (e.key === 'Enter' || e.key === 'Tab') {
              e.preventDefault();
              selectMention(candidates[Math.min(activeIndex, candidates.length - 1)]);
              return;
            }
            if (e.key === 'Escape') {
              e.preventDefault();
              setMention(null);
              return;
            }
          }
          if (e.key === 'Escape' && replyTarget && !isComposingRef.current) {
            e.preventDefault();
            onCancelReply?.();
            return;
          }
          // 휴대폰(터치)에서는 카톡처럼 리턴이 줄바꿈이고 전송은 버튼으로 한다.
          // 한글 조합 중에 누른 리턴이 전송 대신 줄바꿈만 되어 "전송이 안 된다"고 느끼는 것도 막는다
          if (e.key === 'Enter' && !e.shiftKey && !isComposingRef.current && !isTouchDevice) {
            e.preventDefault();
            handleUnifiedSend();
          }
        }}
        placeholder={
          isCode ? '코드를 붙여 넣으세요'
          : inputMode === 'IMAGE' ? (imagePreviewUrl ? '이미지를 보내려면 Enter 또는 전송 버튼을 누르세요' : '이미지를 선택해 주세요')
          : `${target}에 메시지 보내기`
        }
        className={`${s.composerInput} ${isCode ? s.composerInputCode : ''} dc-scroll`}
      />

      <div className={s.composerBar}>
        <button
          type="button"
          className={`${s.composerTool} ${inputMode === 'IMAGE' ? s.composerToolActive : ''}`}
          onClick={() => handleModeToggle('IMAGE')}
          title="이미지 첨부"
          aria-label="이미지 첨부"
        >
          <ImagePlus size={17} />
        </button>
        <button
          type="button"
          className={`${s.composerTool} ${isCode ? s.composerToolActive : ''}`}
          onClick={() => handleModeToggle('CODE')}
          title="코드 블록"
          aria-label="코드 블록"
        >
          <Code2 size={17} />
        </button>

        {isCode && (
          <div className={s.langList}>
            {LANGUAGES.map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => setLanguage(value)}
                className={`${s.langChip} ${language === value ? s.langChipActive : ''}`}
              >
                {label}
              </button>
            ))}
          </div>
        )}

        {blocked ? (
          <span className={`${s.charCount} ${s.charCountOver}`} role="status">
            너무 빠르게 보내고 있어요 · {blockedSeconds}초 후에 보낼 수 있어요
          </span>
        ) : showCount ? (
          <span className={`${s.charCount} ${tooLong ? s.charCountOver : ''}`} role={tooLong ? 'alert' : undefined}>
            {tooLong && <>{TOO_LONG_MESSAGE} · </>}
            {content.length.toLocaleString()} / {MAX_MESSAGE_LENGTH.toLocaleString()}
          </span>
        ) : (
          <span className={s.composerHint}>
            {isCode ? (
              <><b>Tab</b> 들여쓰기 · <b>Ctrl + Enter</b> 전송</>
            ) : (
              <><b>Shift + Enter</b> 줄바꿈</>
            )}
          </span>
        )}

        <button
          type="button"
          // 누르는 순간 입력창 포커스가 빠지면 휴대폰 키보드가 내려가며 버튼이 움직여
          // 탭이 버튼에 닿지 않는다. 포커스를 유지해 키보드도 그대로 두고 바로 보낸다
          onPointerDown={(e) => e.preventDefault()}
          onMouseDown={(e) => e.preventDefault()}
          onClick={handleUnifiedSend}
          disabled={!canSend}
          title="전송"
          aria-label="전송"
          className={`${s.sendBtn} ${canSend ? s.sendBtnReady : ''}`}
        >
          <SendHorizontal size={16} />
        </button>
      </div>

      <input
        type="file"
        accept="image/*"
        ref={fileInputRef}
        onChange={async (e) => {
          const picked = e.target.files?.[0];
          e.target.value = '';
          if (!picked) return;
          const file = await compressImage(picked, CHAT_IMAGE_OPTIONS);
          if (file.size > MAX_UPLOAD_BYTES) {
            alert(`이미지 용량이 너무 큽니다 (${formatBytes(file.size)}). ${formatBytes(MAX_UPLOAD_BYTES)} 이하로 올려주세요.`);
            return;
          }
          setImageFile(file);
          const reader = new FileReader();
          reader.onloadend = () => setImagePreviewUrl(reader.result);
          reader.readAsDataURL(file);
        }}
        style={{ display: 'none' }}
      />
    </div>
  );
};

export default MessageInput;
