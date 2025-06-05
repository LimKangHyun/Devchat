import React, { useState } from 'react';
import axiosInstance from "../api/axiosInstance"
import { 
  FaTimes,
  FaLink,
  FaComments,
  FaExclamationTriangle,
  FaSpinner
} from 'react-icons/fa';

const JoinRoomModal = ({ onClose, onSubmit }) => {
  const [inviteCode, setInviteCode] = useState('');
  const [roomInfo, setRoomInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // 초대 코드로 방 정보 가져오기
  const checkRoomInfo = async (e) => {
    e.preventDefault();
    
    if (!inviteCode.trim()) {
      setError('초대 코드를 입력해주세요.');
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      const res = await axiosInstance.get(`/chat-rooms/info/${inviteCode}`);
      setRoomInfo(res.data);
    } catch (err) {
      console.error('초대 코드 확인 오류:', err);
      const message =
        err.response?.data?.message || // 백엔드에서 내려준 에러 메시지
        err.message ||                // 일반 JS 에러 메시지
        '방 정보를 가져올 수 없습니다. 초대 코드를 확인해주세요.'; // 기본 메시지
      setError(message);
    } finally {
      setLoading(false);
    }
  }

  // 최종 참여 확인
  const confirmJoin = async() => {
    if (inviteCode.trim()) {
      setLoading(true);
      try {
        await onSubmit(inviteCode.trim());
      } catch (error) {
        console.error("참여 실패:", error);
        onClose(); // 실패 시 모달 닫기
      } finally {
        setLoading(false);
      }
    }
  };

  // 이전 단계로 돌아가기
  const goBack = () => {
    setRoomInfo(null);
  };

  return (
    <>
      <div className="modal-backdrop" 
        onClick={onClose}
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          backgroundColor: 'rgba(0,0,0,0.5)',
          zIndex: 990
        }}
      />
      <div style={{
        position: 'fixed', 
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        width: '400px',
        backgroundColor: 'white', 
        boxShadow: '0 5px 15px rgba(0,0,0,0.2)', 
        zIndex: 1000,
        borderRadius: '10px',
        overflow: 'hidden'
      }}>
        <div style={{
          padding: '16px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          backgroundColor: '#2588F1',
          color: 'white'
        }}>
          <h3 style={{ margin: 0, fontSize: '18px' }}>
            {roomInfo ? '채팅방 입장 확인' : '채팅방 참여하기'}
          </h3>
          <button 
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'white'
            }}
          >
            <FaTimes size={18} />
          </button>
        </div>
        
        {/* 방 정보 있을 때는 확인 화면 표시 */}
        {roomInfo ? (
          <div style={{ padding: '20px' }}>
            <div style={{ 
              backgroundColor: '#f8f9fa', 
              padding: '15px', 
              borderRadius: '6px',
              marginBottom: '20px'
            }}>
              <div style={{ 
                fontWeight: 'bold', 
                marginBottom: '12px',
                fontSize: '15px',
                color: '#333'
              }}>
                입장할 채팅방 정보:
              </div>
              <div style={{
                backgroundColor: '#e9ecef',
                padding: '12px',
                borderRadius: '4px',
                marginBottom: '15px'
              }}>
                <div style={{ marginBottom: '8px' }}>
                  <span style={{ 
                    fontSize: '13px', 
                    color: '#6c757d', 
                    display: 'block', 
                    marginBottom: '3px' 
                  }}>
                    방 이름
                  </span>
                  <span style={{ 
                    fontSize: '16px', 
                    fontWeight: '500', 
                    color: '#343a40',
                    wordBreak: 'break-all'
                  }}>
                    {roomInfo.roomName || '이름 없는 채팅방'}
                  </span>
                </div>
                <div>
                  <span style={{ 
                    fontSize: '13px', 
                    color: '#6c757d', 
                    display: 'block', 
                    marginBottom: '3px' 
                  }}>
                    초대 코드
                  </span>
                  <span style={{ 
                    fontSize: '14px', 
                    fontFamily: 'monospace', 
                    color: '#495057',
                    wordBreak: 'break-all'
                  }}>
                    {inviteCode}
                  </span>
                </div>
                {roomInfo.participantCount !== undefined && (
                  <div style={{ marginTop: '8px' }}>
                    <span style={{ 
                      fontSize: '13px', 
                      color: '#6c757d', 
                      display: 'block', 
                      marginBottom: '3px' 
                    }}>
                      참여자 수
                    </span>
                    <span style={{ 
                      fontSize: '14px', 
                      color: '#495057'
                    }}>
                      {roomInfo.participantCount}명
                    </span>
                  </div>
                )}
              </div>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                color: '#0d6efd',
                fontWeight: '500'
              }}>
                <FaComments style={{ marginRight: '8px' }} />
                <span>이 채팅방에 입장하시겠습니까?</span>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button
                type="button"
                onClick={goBack}
                style={{
                  padding: '10px 16px',
                  borderRadius: '6px',
                  backgroundColor: '#f8f9fa',
                  border: '1px solid #ced4da',
                  cursor: 'pointer',
                  fontSize: '14px',
                  color: '#495057'
                }}
              >
                이전으로
              </button>
              <button
                type="button"
                onClick={confirmJoin}
                style={{
                  padding: '10px 16px',
                  borderRadius: '6px',
                  backgroundColor: '#2588F1',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: '500',
                  color: 'white'
                }}
              >
                입장하기
              </button>
            </div>
          </div>
        ) : (
          /* 초대 코드 입력 화면 */
          <form onSubmit={checkRoomInfo} style={{ padding: '20px' }}>
            <div style={{ marginBottom: '16px' }}>
              <label 
                htmlFor="inviteCode" 
                style={{ 
                  display: 'block', 
                  marginBottom: '8px', 
                  fontWeight: '500',
                  fontSize: '14px',
                  color: '#333' 
                }}
              >
                초대 코드 <span style={{ color: '#dc3545' }}>*</span>
              </label>
              <div style={{ 
                display: 'flex', 
                alignItems: 'center',
                width: '100%',
                border: '1px solid #ced4da',
                borderRadius: '6px',
                overflow: 'hidden'
              }}>
                <div style={{
                  backgroundColor: '#f8f9fa', 
                  padding: '10px 12px', 
                  borderRight: '1px solid #ced4da'
                }}>
                  <FaLink size={16} color="#6c757d" />
                </div>
                <input
                  id="inviteCode"
                  type="text"
                  value={inviteCode}
                  onChange={(e) => setInviteCode(e.target.value)}
                  placeholder="초대 코드를 입력하세요"
                  style={{
                    flex: 1,
                    padding: '10px 12px',
                    border: 'none',
                    fontSize: '14px',
                    outline: 'none'
                  }}
                  required
                />
              </div>
              {error && (
                <div style={{ 
                  color: '#dc3545', 
                  fontSize: '13px', 
                  marginTop: '6px',
                  display: 'flex',
                  alignItems: 'center'
                }}>
                  <FaExclamationTriangle style={{ marginRight: '5px' }} size={12} />
                  {error}
                </div>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button
                type="button"
                onClick={onClose}
                style={{
                  padding: '10px 16px',
                  borderRadius: '6px',
                  backgroundColor: '#f8f9fa',
                  border: '1px solid #ced4da',
                  cursor: 'pointer',
                  fontSize: '14px',
                  color: '#495057'
                }}
              >
                취소
              </button>
              <button
                type="submit"
                disabled={loading}
                style={{
                  padding: '10px 16px',
                  borderRadius: '6px',
                  backgroundColor: '#2588F1',
                  border: 'none',
                  cursor: loading ? 'not-allowed' : 'pointer',
                  fontSize: '14px',
                  fontWeight: '500',
                  color: 'white',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                {loading ? (
                  <>
                    <FaSpinner 
                      size={14} 
                      style={{ 
                        marginRight: '8px',
                        animation: 'spin 1s linear infinite'
                      }} 
                    />
                    확인 중...
                  </>
                ) : (
                  '다음'
                )}
              </button>
            </div>
            
            {/* 스피너 애니메이션 */}
            <style>
              {`
                @keyframes spin {
                  0% { transform: rotate(0deg); }
                  100% { transform: rotate(360deg); }
                }
              `}
            </style>
          </form>
        )}
      </div>
    </>
  );
};

export default JoinRoomModal;