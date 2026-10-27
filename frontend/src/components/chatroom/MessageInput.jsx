import React, { useMemo, useRef, useState } from 'react';
import { ImagePlus, Code2, SendHorizontal, X } from 'lucide-react';
import s from './Chat.module.css';
import { handleCodeKeyDown } from './codeEditorKeys';
import { getMentionQuery } from '../../utils/mention';

const MAX_MENTION_CANDIDATES = 6;

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
}) => {
  const fileInputRef = useRef(null);
  const textareaRef = useRef(null);
  const isComposingRef = useRef(false);
  const [isFocused, setIsFocused] = useState(false);
  // @멘션 자동완성: { start: '@' 위치, query }
  const [mention, setMention] = useState(null);
  const [activeIndex, setActiveIndex] = useState(0);

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

  const canSend = inputMode === 'IMAGE' ? !!imagePreviewUrl : content.trim().length > 0;
  const isCode = inputMode === 'CODE';
  const target = roomName || '이 방';

  return (
    <div className={`${s.composer} ${isFocused ? s.composerFocused : ''}`}>
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
        value={content}
        onChange={(e) => {
          setContent(e.target.value);
          updateMention(e.target.value, e.target.selectionStart);
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
          if (e.key === 'Enter' && !e.shiftKey && !isComposingRef.current) {
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

        <span className={s.composerHint}>
          {isCode ? (
            <><b>Tab</b> 들여쓰기 · <b>Ctrl + Enter</b> 전송</>
          ) : (
            <><b>Shift + Enter</b> 줄바꿈</>
          )}
        </span>

        <button
          type="button"
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
        onChange={(e) => {
          if (e.target.files?.[0]) {
            const file = e.target.files[0];
            setImageFile(file);
            const reader = new FileReader();
            reader.onloadend = () => setImagePreviewUrl(reader.result);
            reader.readAsDataURL(file);
          }
        }}
        style={{ display: 'none' }}
      />
    </div>
  );
};

export default MessageInput;
