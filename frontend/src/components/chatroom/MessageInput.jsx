import React, { useRef, useState } from 'react';
import { FaTrashAlt, FaImage, FaCode, FaPaperPlane } from 'react-icons/fa';

const kbdStyle = {
  display: 'inline-block',
  padding: '1px 4px',
  backgroundColor: '#f4f4f4',
  border: '1px solid #ddd',
  borderRadius: '3px',
  fontSize: '10px',
  color: '#666',
};

const ToolBtn = ({ children, active, onClick, title }) => (
  <button
    onClick={onClick}
    title={title}
    style={{
      width: '28px',
      height: '28px',
      border: 'none',
      borderRadius: '5px',
      backgroundColor: active ? '#e8f0f8' : 'transparent',
      color: active ? '#1264a3' : '#666',
      cursor: 'pointer',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      transition: 'background 0.1s, color 0.1s',
    }}
    onMouseEnter={(e) => {
      if (!active) {
        e.currentTarget.style.backgroundColor = '#f0f0f0';
        e.currentTarget.style.color = '#333';
      }
    }}
    onMouseLeave={(e) => {
      if (!active) {
        e.currentTarget.style.backgroundColor = 'transparent';
        e.currentTarget.style.color = '#666';
      }
    }}
  >
    {children}
  </button>
);

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
}) => {
  const fileInputRef = useRef(null);
  const isComposingRef = useRef(false);
  const [isFocused, setIsFocused] = useState(false);

  const handleModeToggle = (mode) => {
    const next = inputMode === mode ? 'TEXT' : mode;
    setInputMode(next);
    if (next === 'IMAGE' && fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const canSend = inputMode === 'IMAGE' ? !!imagePreviewUrl : content.trim().length > 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>

      {/* 이미지 미리보기 */}
      {inputMode === 'IMAGE' && imagePreviewUrl && (
        <div style={{ padding: '8px 12px 0' }}>
          <div style={{
            position: 'relative',
            display: 'inline-flex',
            borderRadius: '8px',
            overflow: 'hidden',
            border: '1px solid #e8e8e8',
          }}>
            <img src={imagePreviewUrl} alt="미리보기" style={{
              maxHeight: '72px',
              maxWidth: '100px',
              display: 'block',
              objectFit: 'cover',
            }} />
            <button onClick={() => {
              setImageFile(null);
              setImagePreviewUrl(null);
              setInputMode('TEXT');
              if (fileInputRef.current) fileInputRef.current.value = null;
            }} style={{
              position: 'absolute',
              top: '3px',
              right: '3px',
              width: '20px',
              height: '20px',
              backgroundColor: 'rgba(0,0,0,0.55)',
              border: 'none',
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
            }}>
              <FaTrashAlt size={9} color="white" />
            </button>
          </div>
        </div>
      )}

      {/* 입력 박스 */}
      <div style={{
        border: `1px solid ${isFocused ? '#1264a3' : '#dddddd'}`,
        borderRadius: '8px',
        backgroundColor: '#ffffff',
        transition: 'border-color 0.15s, box-shadow 0.15s',
        overflow: 'hidden',
        boxShadow: isFocused ? '0 0 0 3px rgba(18,100,163,0.1)' : 'none',
      }}>

        {/* 상단 툴바 */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '2px',
          padding: '6px 8px',
          borderBottom: '1px solid #f1f1f1',
        }}>
          <ToolBtn active={inputMode === 'IMAGE'} onClick={() => handleModeToggle('IMAGE')} title="이미지">
            <FaImage size={13} />
          </ToolBtn>
          <ToolBtn active={inputMode === 'CODE'} onClick={() => handleModeToggle('CODE')} title="코드">
            <FaCode size={13} />
          </ToolBtn>

          {inputMode === 'CODE' && (
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              style={{
                height: '24px',
                border: '1px solid #e0e0e0',
                borderRadius: '4px',
                backgroundColor: '#f8f8f8',
                color: '#444',
                fontSize: '11px',
                padding: '0 6px',
                cursor: 'pointer',
                marginLeft: '2px',
                fontWeight: '500',
              }}
            >
              <option value="javascript">JavaScript</option>
              <option value="java">Java</option>
              <option value="python">Python</option>
              <option value="html">HTML</option>
              <option value="css">CSS</option>
            </select>
          )}

          <div style={{ marginLeft: 'auto', fontSize: '11px', color: '#aaa' }}>
            <kbd style={kbdStyle}>Enter</kbd> 전송&nbsp;&nbsp;
            <kbd style={kbdStyle}>Shift+Enter</kbd> 줄바꿈
          </div>
        </div>

        {/* textarea + 전송 버튼 */}
        <div style={{ position: 'relative', display: 'flex', alignItems: 'flex-end' }}>
          <textarea
            disabled={inputMode === 'IMAGE'}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            onCompositionStart={() => (isComposingRef.current = true)}
            onCompositionEnd={() => (isComposingRef.current = false)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey && !isComposingRef.current) {
                e.preventDefault();
                handleUnifiedSend();
              }
            }}
            placeholder={
              inputMode === 'CODE' ? '코드를 입력하세요...'
              : inputMode === 'IMAGE' ? '이미지를 선택해주세요.'
              : '메시지를 입력하세요...'
            }
            style={{
              flex: 1,
              minHeight: '52px',
              maxHeight: '200px',
              resize: 'none',
              border: 'none',
              outline: 'none',
              padding: '12px 52px 12px 14px',
              fontSize: '14px',
              lineHeight: '1.6',
              color: inputMode === 'IMAGE' ? '#aaa' : '#1a1a1a',
              backgroundColor: 'transparent',
              fontFamily: inputMode === 'CODE'
                ? '"JetBrains Mono", "Fira Code", monospace'
                : '-apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif',
              cursor: inputMode === 'IMAGE' ? 'not-allowed' : 'text',
              width: '100%',
              boxSizing: 'border-box',
            }}
          />

          <button
            onClick={handleUnifiedSend}
            disabled={!canSend}
            title="전송"
            style={{
              position: 'absolute',
              right: '10px',
              bottom: '10px',
              width: '32px',
              height: '32px',
              borderRadius: '6px',
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: canSend ? 'pointer' : 'not-allowed',
              backgroundColor: canSend ? '#007a5a' : '#f0f0f0',
              color: canSend ? '#fff' : '#b0b0b0',
              transition: 'background-color 0.15s',
            }}
            onMouseEnter={(e) => { if (canSend) e.currentTarget.style.backgroundColor = '#006048'; }}
            onMouseLeave={(e) => { if (canSend) e.currentTarget.style.backgroundColor = '#007a5a'; }}
          >
            <FaPaperPlane size={12} style={{ marginLeft: '1px' }} />
          </button>
        </div>
      </div>

      <input
        type="file"
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