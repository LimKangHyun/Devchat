import React, { useEffect, useState } from 'react';
import {
  FaInfoCircle,
  FaTimes,
  FaGithub,
  FaUser,
  FaCrown,
  FaChevronDown,
  FaChevronUp,
  FaExternalLinkAlt
} from 'react-icons/fa';
import axiosInstance from "../api/axiosInstance"

const GitHubRepoDisplay = ({ repositoryUrl }) => {
  const [expanded, setExpanded] = useState(false);

  const parseRepo = (url) => {
    try {
      const parts = new URL(url).pathname.split('/').filter(Boolean);
      return { owner: parts[0], repo: parts[1] };
    } catch {
      return null;
    }
  };

  const parsed = parseRepo(repositoryUrl);

  return (
    <div style={{
      marginBottom: '12px',
      backgroundColor: '#f8fafc',
      borderRadius: '10px',
      overflow: 'hidden',
      border: '1px solid #e2e8f0'
    }}>
      <a
        href={repositoryUrl}
        target="_blank"
        rel="noopener noreferrer"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '14px 16px',
          textDecoration: 'none',
          transition: 'background-color 0.15s'
        }}
        onMouseOver={(e) => e.currentTarget.style.backgroundColor = '#edf2f7'}
        onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
      >
        <FaGithub style={{ color: '#2d3748', fontSize: '20px', flexShrink: 0 }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: '13px' }}>
            <span style={{ color: '#718096' }}>{parsed?.owner}/</span>
            <span style={{ color: '#2d3748', fontWeight: '600' }}>{parsed?.repo}</span>
          </div>
          <div style={{ color: '#a0aec0', fontSize: '11px', marginTop: '2px' }}>
            github.com
          </div>
        </div>
        <FaExternalLinkAlt style={{ color: '#a0aec0', fontSize: '12px', flexShrink: 0 }} />
      </a>

      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          padding: '8px 16px',
          borderTop: '1px solid #e2e8f0',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          color: '#a0aec0',
          fontSize: '12px',
          userSelect: 'none'
        }}
        onMouseOver={(e) => e.currentTarget.style.backgroundColor = '#edf2f7'}
        onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
      >
        <span>전체 URL 보기</span>
        {expanded ? <FaChevronUp size={10} /> : <FaChevronDown size={10} />}
      </div>

      {expanded && (
        <div style={{
          padding: '10px 16px 14px',
          borderTop: '1px solid #e2e8f0',
          color: '#718096',
          fontSize: '12px',
          wordBreak: 'break-all',
          lineHeight: '1.5',
          backgroundColor: '#f8fafc'
        }}>
          {repositoryUrl}
        </div>
      )}
    </div>
  );
};

const IndexingStatusBadge = ({ status }) => {
  if (!status || status === 'NONE') return null;

  const config = {
    RUNNING: { label: '인덱싱 중...', bg: '#ebf8ff', color: '#2b6cb0', border: '#bee3f8', dot: '#4299e1', animate: true },
    COMPLETED: { label: '인덱싱 완료 ✅', bg: '#f0fff4', color: '#276749', border: '#c6f6d5', dot: '#48bb78', animate: false },
    FAILED: { label: '인덱싱 실패 ❌', bg: '#fff5f5', color: '#9b2c2c', border: '#fed7d7', dot: '#fc8181', animate: false },
  };

  const c = config[status];
  if (!c) return null;

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
      padding: '8px 12px',
      borderRadius: '8px',
      marginBottom: '16px',
      backgroundColor: c.bg,
      border: `1px solid ${c.border}`,
      fontSize: '12px',
      color: c.color,
      fontWeight: '500',
    }}>
      <div style={{
        width: '8px',
        height: '8px',
        borderRadius: '50%',
        backgroundColor: c.dot,
        flexShrink: 0,
        animation: c.animate ? 'pulse 1.5s ease-in-out infinite' : 'none',
      }} />
      {c.label}
    </div>
  );
};

const RoomInfoModal = ({ room, sidebarRef, onClose, showToast, indexingStatus }) => {
  const [participants, setParticipants] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetchParticipants = async () => {
    setLoading(true);
    try {
      const response = await axiosInstance.get(`/chat-rooms/${room.roomId}/participants`);
      setParticipants(response.data);
    } catch (err) {
      console.error('참가자 목록 가져오기 실패:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (room?.roomId) {
      fetchParticipants();
    }
  }, [room?.roomId]);

  const ProfileImage = ({ participant, isOwner }) => {
    const [imageError, setImageError] = useState(false);

    return (
      <div style={{ position: 'relative', marginRight: '12px' }}>
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
              src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${participant.profileImageUrl}`}
              alt={participant.nickname}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              onError={() => setImageError(true)}
            />
          ) : (
            <FaUser style={{ color: isOwner ? '#FFD700' : '#6c757d', fontSize: '18px' }} />
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
            <FaCrown style={{ color: 'white', fontSize: '8px' }} />
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
              justifyContent: 'center'
            }}
          >
            <FaTimes size={16} />
          </button>
        </div>

        <div style={{ padding: '20px', maxHeight: 'calc(80vh - 80px)', overflowY: 'auto' }}>
          {room.repositoryUrl && (
            <>
              <GitHubRepoDisplay repositoryUrl={room.repositoryUrl} />
              <IndexingStatusBadge status={indexingStatus} />
            </>
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
            <div style={{ padding: '40px', textAlign: 'center', color: '#718096' }}>
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
              {participants.filter(p => p.owner).map((p, idx) => (
                <div key={`owner-${idx}`} style={{
                  display: 'flex',
                  alignItems: 'center',
                  padding: '12px 16px',
                  background: 'linear-gradient(135deg, #fef5e7 0%, #fed7aa 100%)',
                  borderRadius: '12px',
                  border: '2px solid #fed7aa'
                }}>
                  <ProfileImage participant={p} isOwner={true} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: '600', fontSize: '15px', color: '#2d3748', marginBottom: '4px' }}>
                      {p.nickname || '알 수 없음'}
                    </div>
                    <span style={{
                      background: 'linear-gradient(135deg, #f6ad55 0%, #ed8936 100%)',
                      color: 'white',
                      padding: '3px 8px',
                      borderRadius: '6px',
                      fontSize: '11px',
                      fontWeight: '600'
                    }}>
                      👑 방장
                    </span>
                  </div>
                </div>
              ))}

              {participants.filter(p => !p.owner).map((p, idx) => (
                <div key={`member-${idx}`} style={{
                  display: 'flex',
                  alignItems: 'center',
                  padding: '12px 16px',
                  backgroundColor: '#f8fafc',
                  borderRadius: '12px',
                  border: '1px solid #e2e8f0'
                }}>
                  <ProfileImage participant={p} isOwner={false} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: '500', fontSize: '15px', color: '#2d3748', marginBottom: '4px' }}>
                      {p.nickname || '알 수 없음'}
                    </div>
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

      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.3; }
        }
      `}</style>
    </>
  );
};

export default RoomInfoModal;