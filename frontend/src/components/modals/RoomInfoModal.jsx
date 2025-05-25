import React, { useEffect, useRef, useState } from 'react';
import {
  FaInfoCircle,
  FaTimes,
  FaGithub,
  FaUser,
  FaCrown
} from 'react-icons/fa';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import axiosInstance from "../api/axiosInstance"

const RoomInfoModal = ({ room, sidebarRef, onClose, showToast }) => {
  const [participants, setParticipants] = useState([]);
  const [loading, setLoading] = useState(false);
  const stompClientRef = useRef(null);
  const hasLoadedInitialData = useRef(false);

  const fetchParticipants = async () => {
    setLoading(true);
    try {
      const response = await axiosInstance.get(`/chat-rooms/${room.roomId}/participants`);
      console.log('참가자 응답:', response.data);
      setParticipants(response.data);
    } catch (err) {
      console.error('참가자 목록 가져오기 실패:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (room?.roomId && !hasLoadedInitialData.current) {
      fetchParticipants();
      hasLoadedInitialData.current = true;
    }

    if (!room?.roomId) return;

    const socket = new SockJS('http://localhost:8080/ws');
    const stomp = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        stomp.subscribe(`/topic/chat/${room.roomId}/refresh`, () => {
          fetchParticipants();
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame);
      }
    });

    stomp.activate();
    stompClientRef.current = stomp;

    return () => {
      stomp.deactivate();
    };
  }, [room?.roomId]);

  // 프로필 이미지 컴포넌트
  const ProfileImage = ({ participant, isOwner }) => {
    const [imageError, setImageError] = useState(false);
    
    return (
      <div style={{ 
        position: 'relative',
        marginRight: '12px'
      }}>
        <div style={{
          width: '40px',
          height: '40px',
          borderRadius: '50%',
          overflow: 'hidden',
          border: isOwner ? '2px solid #FFD700' : '2px solid #e9ecef',
          backgroundColor: '#f8f9fa',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          {participant.profileImageUrl && !imageError ? (
            <img
              src={`http://localhost:8080/images/profile/${participant.profileImageUrl}`}
              alt={participant.nickname}
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover'
              }}
              onError={() => setImageError(true)}
            />
          ) : (
            <FaUser style={{ 
              color: isOwner ? '#FFD700' : '#6c757d',
              fontSize: '18px'
            }} />
          )}
        </div>
        {isOwner && (
          <div style={{
            position: 'absolute',
            top: '-2px',
            right: '-2px',
            backgroundColor: '#FFD700',
            borderRadius: '50%',
            width: '16px',
            height: '16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: '2px solid white'
          }}>
            <FaCrown style={{ 
              color: 'white',
              fontSize: '8px'
            }} />
          </div>
        )}
      </div>
    );
  };

  return (
    <>
      <div
        className="modal-backdrop"
        onClick={onClose}
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          backgroundColor: 'rgba(0,0,0,0.3)',
          zIndex: 990
        }}
      />
      <div style={{
        position: 'fixed',
        top: '50%',
        left: sidebarRef.current ? `calc(${sidebarRef.current.offsetWidth}px + 20px)` : '280px',
        transform: 'translateY(-50%)',
        width: '360px',
        maxHeight: '80vh',
        backgroundColor: 'white',
        boxShadow: '0 10px 25px rgba(0,0,0,0.15)',
        zIndex: 1000,
        borderRadius: '12px',
        overflow: 'hidden'
      }}>
        <div style={{
          padding: '16px 20px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          color: 'white'
        }}>
          <h3 style={{ margin: 0, fontSize: '18px', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <FaInfoCircle size={18} />
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontWeight: '600' }}>채팅방 정보</span>
              <span style={{ fontSize: '13px', opacity: 0.9, fontWeight: '400' }}>
                {room.name || room.roomName || `Room ${room.uniqueId}`}
              </span>
            </div>
          </h3>
          <button
            onClick={onClose}
            style={{
              background: 'rgba(255,255,255,0.2)',
              border: 'none',
              cursor: 'pointer',
              color: 'white',
              borderRadius: '6px',
              width: '32px',
              height: '32px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'background-color 0.2s'
            }}
            onMouseOver={(e) => e.target.style.backgroundColor = 'rgba(255,255,255,0.3)'}
            onMouseOut={(e) => e.target.style.backgroundColor = 'rgba(255,255,255,0.2)'}
          >
            <FaTimes size={16} />
          </button>
        </div>

        <div style={{ padding: '20px', maxHeight: 'calc(80vh - 80px)', overflowY: 'auto' }}>
          {room.repositoryUrl && (
            <div style={{
              marginBottom: '20px',
              padding: '16px',
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderRadius: '10px'
            }}>
              <div style={{ 
                fontWeight: '600', 
                marginBottom: '10px', 
                fontSize: '14px',
                color: '#2d3748'
              }}>
                GitHub Repository
              </div>
              <a
                href={room.repositoryUrl}
                target="_blank"
                rel="noopener noreferrer"
                style={{
                  color: '#4299e1',
                  textDecoration: 'none',
                  fontSize: '14px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  wordBreak: 'break-all',
                  padding: '8px',
                  backgroundColor: 'white',
                  borderRadius: '6px',
                  transition: 'background-color 0.2s'
                }}
                onMouseOver={(e) => e.target.style.backgroundColor = '#f7fafc'}
                onMouseOut={(e) => e.target.style.backgroundColor = 'white'}
              >
                <FaGithub />
                {room.repositoryUrl}
              </a>
            </div>
          )}
          
          <div style={{ 
            fontSize: '16px', 
            fontWeight: '600', 
            marginBottom: '16px',
            color: '#2d3748',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <span>채팅방 멤버</span>
            {participants && (
              <span style={{
                backgroundColor: '#e2e8f0',
                color: '#4a5568',
                padding: '2px 8px',
                borderRadius: '12px',
                fontSize: '12px',
                fontWeight: '500'
              }}>
                {participants.length}명
              </span>
            )}
          </div>

          {loading ? (
            <div style={{ 
              padding: '40px', 
              textAlign: 'center',
              color: '#718096'
            }}>
              <div style={{
                width: '24px',
                height: '24px',
                border: '3px solid #e2e8f0',
                borderTop: '3px solid #4299e1',
                borderRadius: '50%',
                animation: 'spin 1s linear infinite',
                margin: '0 auto 12px'
              }}></div>
              로딩 중...
            </div>
          ) : participants && participants.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {/* 방장(owner) 먼저 표시 */}
              {participants.filter(p => p.owner).map((p, idx) => (
                <div key={`owner-${idx}`} style={{
                  display: 'flex',
                  alignItems: 'center',
                  padding: '12px 16px',
                  background: 'linear-gradient(135deg, #fef5e7 0%, #fed7aa 100%)',
                  borderRadius: '12px',
                  border: '2px solid #fed7aa',
                  transition: 'transform 0.2s, box-shadow 0.2s',
                  cursor: 'pointer'
                }}
                onMouseOver={(e) => {
                  e.currentTarget.style.transform = 'translateY(-2px)';
                  e.currentTarget.style.boxShadow = '0 4px 12px rgba(254, 215, 170, 0.4)';
                }}
                onMouseOut={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = 'none';
                }}
                >
                  <ProfileImage participant={p} isOwner={true} />
                  <div style={{ flex: 1 }}>
                    <div style={{ 
                      fontWeight: '600', 
                      fontSize: '15px',
                      color: '#2d3748',
                      marginBottom: '4px'
                    }}>
                      {p.nickname || '알 수 없음'}
                    </div>
                    <div style={{ fontSize: '12px' }}>
                      <span style={{
                        background: 'linear-gradient(135deg, #f6ad55 0%, #ed8936 100%)',
                        color: 'white',
                        padding: '3px 8px',
                        borderRadius: '6px',
                        fontSize: '11px',
                        fontWeight: '600',
                        textShadow: '0 1px 2px rgba(0,0,0,0.1)'
                      }}>
                        👑 방장
                      </span>
                    </div>
                  </div>
                </div>
              ))}

              {/* 일반 멤버 표시 */}
              {participants.filter(p => !p.owner).map((p, idx) => (
                <div key={`member-${idx}`} style={{
                  display: 'flex',
                  alignItems: 'center',
                  padding: '12px 16px',
                  backgroundColor: '#f8fafc',
                  borderRadius: '12px',
                  border: '1px solid #e2e8f0',
                  transition: 'transform 0.2s, box-shadow 0.2s, border-color 0.2s',
                  cursor: 'pointer'
                }}
                onMouseOver={(e) => {
                  e.currentTarget.style.transform = 'translateY(-1px)';
                  e.currentTarget.style.boxShadow = '0 2px 8px rgba(226, 232, 240, 0.6)';
                  e.currentTarget.style.borderColor = '#cbd5e0';
                }}
                onMouseOut={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = 'none';
                  e.currentTarget.style.borderColor = '#e2e8f0';
                }}
                >
                  <ProfileImage participant={p} isOwner={false} />
                  <div style={{ flex: 1 }}>
                    <div style={{ 
                      fontWeight: '500', 
                      fontSize: '15px',
                      color: '#2d3748',
                      marginBottom: '4px'
                    }}>
                      {p.nickname || '알 수 없음'}
                    </div>
                    <div style={{ fontSize: '12px' }}>
                      <span style={{
                        backgroundColor: '#e2e8f0',
                        color: '#4a5568',
                        padding: '3px 8px',
                        borderRadius: '6px',
                        fontSize: '11px',
                        fontWeight: '500'
                      }}>
                        멤버
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div style={{
              padding: '32px 20px',
              textAlign: 'center',
              color: '#718096',
              backgroundColor: '#f8fafc',
              borderRadius: '12px',
              border: '2px dashed #e2e8f0',
              fontSize: '14px'
            }}>
              <FaUser style={{ fontSize: '24px', marginBottom: '12px', opacity: 0.5 }} />
              <div>멤버 정보를 불러올 수 없습니다</div>
            </div>
          )}
        </div>
      </div>
      
      <style>
        {`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}
      </style>
    </>
  );
};

export default RoomInfoModal;