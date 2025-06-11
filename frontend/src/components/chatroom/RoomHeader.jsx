import React, { useState } from 'react';
import { FaCopy } from 'react-icons/fa';
import { useNavigate } from 'react-router-dom';

const RoomHeader= ({roomName, inviteCode, onSearch, onLeaveRoom, onDeleteRoom, isOwner}) => {

    const navigate = useNavigate();  
    const [showNotification, setShowModal] = useState(false);
    const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
    const [showLeaveSuccess, setShowLeaveSuccess] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false); 

    //채팅방 나가기 핸들러 (API는 chatRoom에서 호출)
    const handleLeave = async () => {
        const result = await onLeaveRoom(); // ChatRoom에서 props로 전달

        if (result.success) {
            setShowLeaveConfirm(false);
            setShowLeaveSuccess(true);
            setTimeout(() => {
                setShowLeaveSuccess(false);
                navigate('/');
            }, 500);
        } else {
            alert(result.error);
        }
    };

     // 채팅방 삭제 핸들러 (추가)
    const handleDelete = async () => {
        const result = await onDeleteRoom();

        if (result.success) {
            setShowDeleteConfirm(false);
            alert("채팅방이 삭제되었습니다.");
            navigate(`/`);
        } else {
            alert(result.error);
            setShowDeleteConfirm(false);
        }
    };

    //메세지 검색 핸들러 (API는 ChatRoom에서 호출)
    const handleSearch = (keyword) => {
        if (onSearch && typeof onSearch === 'function') {
            onSearch(keyword); // ChatRoom에서 전달받은 검색 로직 실행
        }
    };

    //초대 코드 복사
    const handleCopy = async () => {
        try {
        await navigator.clipboard.writeText(inviteCode); // 백엔드 없이 바로 복사
        setShowModal(true);
        setTimeout(() => setShowModal(false), 2000);
        } catch (err) {
        console.error(err);
        alert('초대 코드 복사 중 오류가 발생했습니다.');
        }
    };

    return (
        <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '6px 20px',
            borderBottom: '1px solid #eaedf0',
            backgroundColor: '#fff'
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              position: 'relative'
            }}>
              <span style={{
                fontWeight: '600',
                fontSize: '18px',
                color: '#2d3748'
              }}>
                {roomName}
              </span>

              {/* 초대 코드 복사 버튼 */}
              <button
                onClick={handleCopy}
                style={{
                  backgroundColor: '#2588F1',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  padding: '6px 12px',
                  fontSize: '13px',
                  fontWeight: '500',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}
              >
                <FaCopy size={14} />
                초대 코드 복사
              </button>

              <button
                onClick={() => setMenuOpen(prev => !prev)}
                style={{
                  fontSize: '25px',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  color: '#94a3b8',    
              }}
              >
                ⋮
              </button>

              {/* 드롭다운 메뉴 */}
              {menuOpen && (
                <div style={{
                  position: 'absolute',
                  top: '0',
                  left: '100%',
                  marginLeft: '8px',  
                  backgroundColor: 'white',  
                  border: '1px solid #ccc',
                  borderRadius: '6px',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                  zIndex: 1000,
                  minWidth: '100px' 
                }}>
                  <button
                    onClick={() => setShowLeaveConfirm(true)}
                    style={{
                      padding: '10px 16px',
                      background: 'none',
                      border: 'none',
                      width: '100%',
                      textAlign: 'left',
                      cursor: 'pointer',
                      fontSize: '14px',
                      color: '#e53e3e',
                      whiteSpace: 'nowrap',
                      display: 'flex'
                    }}
                  >
                    채팅방 나가기
                  </button>

                  {/* 방장만 삭제 버튼 표시 */}
                  {isOwner && (
                    <button
                      onClick={() => {
                        setShowDeleteConfirm(true);
                        setMenuOpen(false);
                      }}
                      style={{
                        padding: '10px 16px',
                        background: 'none',
                        border: 'none',
                        width: '100%',
                        textAlign: 'left',
                        cursor: 'pointer',
                        fontSize: '14px',
                        color: '#dc2626',
                        whiteSpace: 'nowrap',
                        display: 'flex',
                        fontWeight: '600'
                      }}
                    >
                      채팅방 삭제
                    </button>
                  )}
                </div>
              )}
            </div>

            <div>
              <input
                type="text"
                placeholder="메시지 검색"
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    handleSearch(e.target.value);
                  }
                }}
                style={{
                  width: '220px',
                  backgroundColor: '#f9fafc',
                  fontSize: '14px'
                }}
              />
            </div>

            {/* 초대 코드 복사 완료 알림 */}
            {showNotification && (
            <div style={{
              position: 'fixed',
              top: '15px',
              right: '15px',
              backgroundColor: '#333',
              color: '#fff',
              padding: '10px 16px',
              borderRadius: '6px',
              boxShadow: '0 4px 8px rgba(0, 0, 0, 0.2)',
              zIndex: 1000
            }}>
              초대 코드가 복사되었습니다.
            </div>
          )}

            {/* 나가기 확인 모달 */}
            {showLeaveConfirm && (
            <div style={{
              position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh',
              backgroundColor: 'rgba(0, 0, 0, 0.4)', display: 'flex',
              alignItems: 'center', justifyContent: 'center', zIndex: 2000
            }}>
              <div style={{
                backgroundColor: 'white', padding: '24px', borderRadius: '8px',
                minWidth: '280px', textAlign: 'center', boxShadow: '0 4px 12px rgba(0,0,0,0.2)'
              }}>
                <p style={{ fontSize: '16px', marginBottom: '20px' }}>
                  정말 이 채팅방을 나가시겠습니까?
                </p>
                <div style={{ display: 'flex', justifyContent: 'center', gap: '12px' }}>
                  <button
                    onClick={() => setShowLeaveConfirm(false)}
                    style={{
                      padding: '8px 16px', backgroundColor: '#eee',
                      border: 'none', borderRadius: '4px', cursor: 'pointer'
                    }}
                  >
                    취소
                  </button>
                  <button
                    onClick={handleLeave}
                    style={{
                      padding: '8px 16px', backgroundColor: '#e53e3e', color: 'white',
                      border: 'none', borderRadius: '4px', cursor: 'pointer'
                    }}
                  >
                    나가기
                  </button>
                </div>
              </div>
            </div>)}

             {/* 삭제 확인 모달 (추가) */}
            {showDeleteConfirm && (
            <div style={{
              position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh',
              backgroundColor: 'rgba(0, 0, 0, 0.4)', display: 'flex',
              alignItems: 'center', justifyContent: 'center', zIndex: 2000
            }}>
              <div style={{
                backgroundColor: 'white', padding: '24px', borderRadius: '8px',
                minWidth: '320px', textAlign: 'center', boxShadow: '0 4px 12px rgba(0,0,0,0.2)'
              }}>
                <p style={{ fontSize: '16px', marginBottom: '8px', fontWeight: '600' }}>
                  채팅방을 삭제하시겠습니까?
                </p>
                <p style={{ fontSize: '14px', marginBottom: '20px', color: '#666' }}>
                  삭제된 채팅방과 모든 메시지는 복구할 수 없습니다.
                </p>
                <div style={{ display: 'flex', justifyContent: 'center', gap: '12px' }}>
                  <button
                    onClick={() => setShowDeleteConfirm(false)}
                    style={{
                      padding: '8px 16px', backgroundColor: '#eee',
                      border: 'none', borderRadius: '4px', cursor: 'pointer'
                    }}
                  >
                    취소
                  </button>
                  <button
                    onClick={handleDelete}
                    style={{
                      padding: '8px 16px', backgroundColor: '#dc2626', color: 'white',
                      border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: '600'
                    }}
                  >
                    삭제
                  </button>
                </div>
              </div>
            </div>)}

            {/* 나가기 완료 모달 */}
            {showLeaveSuccess && (
                <div style={{
                position: 'fixed', top: '20px', right: '20px',
                backgroundColor: '#333', color: 'white',
                padding: '12px 20px', borderRadius: '6px',
                boxShadow: '0 4px 8px rgba(0,0,0,0.2)', zIndex: 2000
                }}>
                채팅방에서 나갔습니다.
                </div>
            )}
          </div>
    )
}

export default RoomHeader;