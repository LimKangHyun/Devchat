//입력 모드 전환과 입력 필드
import React, { useRef } from 'react';
import { FaTrashAlt } from 'react-icons/fa';

const MessageInput = ({
  inputMode,
  setInputMode,
  content,
  setContent,
  language,
  setLanguage,
  sendMessage,
  handleUnifiedSend,
  setImageFile,
  imagePreviewUrl,
  setImagePreviewUrl,
}) => {
    const fileInputRef = useRef(null);
    const isComposingRef = useRef(false);
    
    return (
        <>
          <div style={{ marginBottom: '12px', display: 'flex', alignItems: 'center'}}>
              <div style={{ display: 'flex', backgroundColor: '#f1f5f9', borderRadius: '6px', padding: '2px', marginRight: '12px' }}>
                <span
                  onClick={() => {
                    const nextMode = inputMode === 'IMAGE' ? 'TEXT' : 'IMAGE';
                    setInputMode(nextMode);

                    // 모드가 IMAGE로 바뀌면 파일 선택창 자동 오픈
                    if (nextMode === 'IMAGE' && fileInputRef.current) {
                      fileInputRef.current.click();
                    }
                  }}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '4px',
                    cursor: 'pointer',
                    fontWeight: '500',
                    fontSize: '13px',
                    backgroundColor: inputMode === 'IMAGE' ? '#ffffff' : 'transparent',
                    color: inputMode === 'IMAGE' ? '#4a6cf7' : '#64748b',
                    boxShadow: inputMode === 'IMAGE' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                    transition: 'all 0.2s'
                  }}
                >
                  사진
                </span>
                <span
                  onClick={() => {
                    const nextMode = inputMode === 'CODE' ? 'TEXT' : 'CODE';
                    setInputMode(nextMode);
                  }}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '4px',
                    cursor: 'pointer',
                    fontWeight: '500',
                    fontSize: '13px',
                    backgroundColor: inputMode === 'CODE' ? '#ffffff' : 'transparent',
                    color: inputMode === 'CODE' ? '#4a6cf7' : '#64748b',
                    boxShadow: inputMode === 'CODE' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                    transition: 'all 0.2s'
                  }}
                >
                  코드
                </span>
              </div>

              {/* 언어 선택 옵션을 코드 버튼 바로 옆으로 이동 */}
              {inputMode === 'CODE' && (
                <select
                  value={language}
                  onChange={(e) => setLanguage(e.target.value)}
                  style={{
                    boxShadow: 'inset 0 1px 2px rgba(0,0,0,0.05)',
                    backgroundColor: '#f8fafc',
                    border: '1px solid #e2e8f0',
                    borderRadius: '4px',
                    padding: '6px 10px',
                    fontSize: '13px',
                    color: '#475569',
                    cursor: 'pointer'
                  }}
                >
                  <option value="javascript">JavaScript</option>
                  <option value="java">Java</option>
                  <option value="python">Python</option>
                  <option value="html">HTML</option>
                  <option value="css">CSS</option>
                </select>
              )}
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>

                {/* 썸네일 미리보기 이미지 */}
                {inputMode === 'IMAGE' && imagePreviewUrl && (
                  <div style={{
                    position: 'relative',
                    marginBottom: '10px',
                    padding: '8px',
                    backgroundColor: '#f8fafc',
                    border: '2px solid #e2e8f0',
                    borderRadius: '8px',
                    maxWidth: '10%',
                    boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
                    display: 'inline-block'
                  }}>
                    <img
                      src={imagePreviewUrl}
                      alt="미리보기"
                      style={{
                        maxWidth: '100%',
                        objectFit: 'contain',
                        borderRadius: '6px',
                        display: 'block'
                      }}
                    />

                    <button
                      onClick={() => {
                        setImageFile(null);
                        setImagePreviewUrl(null);
                        setInputMode('TEXT');

                        if (fileInputRef.current) {
                          fileInputRef.current.value = null;
                        }
                      }}
                      title="삭제"
                      style={{
                        position: 'absolute',
                        top: '2px',
                        right: '2px',
                        backgroundColor: 'rgba(255, 255, 255, 0.9)',
                        border: '1px solid #e2e8f0',
                        borderRadius: '50%',
                        width: '28px',
                        height: '28px',
                        color: '#e53e3e',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        boxShadow: '0 1px 4px rgba(0,0,0,0.15)'
                      }}
                    >
                      <FaTrashAlt color="#e53e3e" size={16} />
                    </button>
                  </div>
                )}

                <div style={{ display: 'flex', alignItems: 'flex-end', gap: '10px' }}>
                  <textarea
                    disabled={inputMode === 'IMAGE'}
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    onCompositionStart={() => (isComposingRef.current = true)}
                    onCompositionEnd={() => (isComposingRef.current = false)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey && !isComposingRef.current) {
                        e.preventDefault();
                        sendMessage(e.target.value);
                      }
                    }}
                    placeholder={inputMode === 'CODE' ? '코드를 입력하세요.' : inputMode === 'IMAGE' ? '이미지를 업로드 해주세요.' : '메시지를 입력하세요.'}
                    style={{
                      flex: 1,
                      height: '80px',
                      resize: 'none',
                      padding: '12px 16px',
                      fontSize: '14px',
                      backgroundColor: inputMode === 'IMAGE' ? '#f1f5f9' : inputMode === 'CODE' ? '#f8fafc' : 'white',
                      border: '1px solid #e2e8f0',
                      borderRadius: '8px',
                      lineHeight: '1.5',
                      color: '#4a5568',
                      boxShadow: 'inset 0 1px 2px rgba(0,0,0,0.05)',
                      transition: 'border-color 0.2s',
                      cursor: inputMode === 'IMAGE' ? 'not-allowed' : 'text'
                    }}
                  />

                  <input
                    type="file"
                    ref={fileInputRef}
                    // accept="image/*"
                    onChange={(e) => {
                      if (e.target.files?.[0]) {
                        const file = e.target.files[0];
                        setImageFile(file);

                        // 파일 URL 생성
                        const reader = new FileReader();
                        reader.onloadend = () => setImagePreviewUrl(reader.result);
                        reader.readAsDataURL(file);
                      }
                    }}
                    style={{ display: 'none' }} // 숨김
                  />

                  <button
                    onClick={handleUnifiedSend}
                    style={{
                      backgroundColor: '#4a6cf7',
                      color: 'white',
                      border: 'none',
                      borderRadius: '4px',
                      padding: '0 20px',
                      height: '40px',
                      fontSize: '14px',
                      fontWeight: '500',
                      cursor: 'pointer',
                      transition: 'background-color 0.2s',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      boxShadow: '0 2px 4px rgba(0, 0, 0, 0.1)',
                      height: '80px'
                    }}
                  >
                    전송
                  </button>
                </div>
              </div>
            </div>
        </>
    );
};
export default MessageInput;
