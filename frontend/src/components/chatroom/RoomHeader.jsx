import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { FaBell, FaRegBell } from 'react-icons/fa';

const RoomHeader = ({
  roomName, inviteCode, onSearch, onLeaveRoom, onDeleteRoom,
  isOwner, toggleAlarm, alarmEnabled,
  aiReviewEnabled, onToggleAiReview, repositoryUrl
}) => {
  const navigate = useNavigate();
  const [showNotification, setShowNotification] = useState(false);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [showLeaveSuccess, setShowLeaveSuccess] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMenuOpen(false);
      }
    };
    if (menuOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuOpen]);

  const handleLeave = async () => {
    const result = await onLeaveRoom();
    if (result.success) {
      setShowLeaveConfirm(false);
      setShowLeaveSuccess(true);
      setTimeout(() => { setShowLeaveSuccess(false); navigate('/'); }, 500);
    } else {
      alert(result.error);
    }
  };

  const handleDelete = async () => {
    const result = await onDeleteRoom();
    if (result.success) {
      setShowDeleteConfirm(false);
      alert('채팅방이 삭제되었습니다.');
      navigate('/');
    } else {
      alert(result.error);
      setShowDeleteConfirm(false);
    }
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(inviteCode);
      setShowNotification(true);
      setTimeout(() => setShowNotification(false), 2000);
    } catch {
      alert('초대 코드 복사 중 오류가 발생했습니다.');
    }
  };

  const plainBtn = {
    display: 'flex', alignItems: 'center', gap: '6px',
    padding: '6px 12px', borderRadius: '8px', border: 'none',
    fontSize: '13px', cursor: 'pointer', fontWeight: '500',
    background: 'transparent', color: '#3b82f6', transition: 'background 0.15s, color 0.15s',
  };

  const iconBtn = (active = true) => ({
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    width: '32px', height: '32px', borderRadius: '8px', border: 'none',
    background: 'transparent', color: active ? '#3b82f6' : '#94a3b8',
    cursor: 'pointer', transition: 'background 0.15s, color 0.15s',
  });

  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      padding: '10px 20px', backgroundColor: '#fff',
      borderBottom: '0.5px solid #eaedf0', gap: '12px',
    }}>
      {/* 왼쪽 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', position: 'relative' }}>
        <span style={{ fontSize: '15px', fontWeight: '500', color: '#1e293b', marginRight: '4px' }}>
          {roomName}
        </span>

        {/* 초대 코드 복사 */}
        <button
          onClick={handleCopy}
          style={plainBtn}
          onMouseEnter={e => e.currentTarget.style.background = '#f0f6ff'}
          onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
        >
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
          초대 코드 복사
        </button>

        {/* AI 리뷰 토글 */}
        {isOwner && repositoryUrl && (
          <div
            onClick={onToggleAiReview}
            style={{
              display: 'flex', alignItems: 'center', gap: '7px',
              padding: '5px 10px', borderRadius: '8px',
              cursor: 'pointer', transition: 'background 0.15s',
            }}
            onMouseEnter={e => e.currentTarget.style.background = '#f0f6ff'}
            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          >
            <span style={{ fontSize: '13px', color: '#3b82f6', fontWeight: '500', whiteSpace: 'nowrap' }}>
              AI 리뷰
            </span>
            <div style={{
              width: '30px', height: '17px', borderRadius: '9px',
              background: aiReviewEnabled ? '#3b82f6' : '#cbd5e1',
              position: 'relative', transition: 'background 0.2s', flexShrink: 0,
            }}>
              <div style={{
                position: 'absolute', top: '2px',
                left: aiReviewEnabled ? '15px' : '2px',
                width: '13px', height: '13px',
                borderRadius: '50%', background: 'white',
                transition: 'left 0.2s',
                boxShadow: '0 1px 3px rgba(0,0,0,0.15)',
              }} />
            </div>
          </div>
        )}

        {/* 알람 */}
        <button
          onClick={toggleAlarm}
          title={alarmEnabled ? '알림 끄기' : '알림 켜기'}
          style={iconBtn(alarmEnabled)}
          onMouseEnter={e => e.currentTarget.style.background = alarmEnabled ? '#f0f6ff' : '#f1f5f9'}
          onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
        >
          {alarmEnabled ? <FaBell size={15} /> : <FaRegBell size={15} />}
        </button>

        {/* 더보기 */}
        <div ref={menuRef} style={{ position: 'relative' }}>
          <button
            onClick={() => setMenuOpen(prev => !prev)}
            style={iconBtn(false)}
            onMouseEnter={e => e.currentTarget.style.background = '#f1f5f9'}
            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor">
              <circle cx="12" cy="5" r="1.5" /><circle cx="12" cy="12" r="1.5" /><circle cx="12" cy="19" r="1.5" />
            </svg>
          </button>

          {menuOpen && (
            <div style={{
              position: 'absolute', top: '38px', left: '0',
              background: 'white', border: '0.5px solid #e2e8f0',
              borderRadius: '10px', boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
              zIndex: 1000, minWidth: '140px', overflow: 'hidden',
            }}>
              <button
                onClick={() => { setShowLeaveConfirm(true); setMenuOpen(false); }}
                style={{ padding: '10px 16px', background: 'none', border: 'none', width: '100%', textAlign: 'left', cursor: 'pointer', fontSize: '13px', color: '#e53e3e' }}
                onMouseEnter={e => e.currentTarget.style.background = '#fef2f2'}
                onMouseLeave={e => e.currentTarget.style.background = 'none'}
              >
                채팅방 나가기
              </button>
              {isOwner && (
                <button
                  onClick={() => { setShowDeleteConfirm(true); setMenuOpen(false); }}
                  style={{ padding: '10px 16px', background: 'none', borderTop: '0.5px solid #f1f5f9', border: 'none', width: '100%', textAlign: 'left', cursor: 'pointer', fontSize: '13px', color: '#dc2626', fontWeight: '500' }}
                  onMouseEnter={e => e.currentTarget.style.background = '#fef2f2'}
                  onMouseLeave={e => e.currentTarget.style.background = 'none'}
                >
                  채팅방 삭제
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 검색 */}
      <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
          style={{ position: 'absolute', left: '10px', pointerEvents: 'none' }}>
          <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          type="text"
          placeholder="메시지 검색"
          onKeyDown={(e) => { if (e.key === 'Enter' && onSearch) onSearch(e.target.value); }}
          style={{
            paddingLeft: '32px', paddingRight: '12px', paddingTop: '7px', paddingBottom: '7px',
            borderRadius: '8px', border: '1px solid #e2e8f0',
            background: '#f8fafc', fontSize: '13px', color: '#1e293b',
            width: '200px', outline: 'none', transition: 'border-color 0.15s, background 0.15s',
          }}
          onFocus={e => { e.target.style.borderColor = '#93c5fd'; e.target.style.background = 'white'; }}
          onBlur={e => { e.target.style.borderColor = '#e2e8f0'; e.target.style.background = '#f8fafc'; }}
        />
      </div>

      {/* 복사 완료 토스트 */}
      {showNotification && (
        <div style={{
          position: 'fixed', top: '15px', right: '15px',
          backgroundColor: '#1e293b', color: '#fff',
          padding: '10px 16px', borderRadius: '8px',
          fontSize: '13px', zIndex: 1000,
          boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
        }}>
          초대 코드가 복사되었습니다.
        </div>
      )}

      {/* 나가기 확인 모달 */}
      {showLeaveConfirm && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.35)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
          <div style={{ backgroundColor: 'white', padding: '28px 24px', borderRadius: '12px', minWidth: '300px', textAlign: 'center', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
            <p style={{ fontSize: '15px', color: '#1e293b', marginBottom: '6px', fontWeight: '500' }}>채팅방을 나가시겠습니까?</p>
            <p style={{ fontSize: '13px', color: '#94a3b8', marginBottom: '24px' }}>나가면 다시 초대 코드로 입장할 수 있습니다.</p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '10px' }}>
              <button onClick={() => setShowLeaveConfirm(false)} style={{ padding: '8px 20px', backgroundColor: '#f1f5f9', border: 'none', borderRadius: '8px', cursor: 'pointer', fontSize: '13px', color: '#64748b' }}>취소</button>
              <button onClick={handleLeave} style={{ padding: '8px 20px', backgroundColor: '#e53e3e', color: 'white', border: 'none', borderRadius: '8px', cursor: 'pointer', fontSize: '13px' }}>나가기</button>
            </div>
          </div>
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {showDeleteConfirm && (
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', backgroundColor: 'rgba(0,0,0,0.35)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
          <div style={{ backgroundColor: 'white', padding: '28px 24px', borderRadius: '12px', minWidth: '320px', textAlign: 'center', boxShadow: '0 8px 32px rgba(0,0,0,0.12)' }}>
            <p style={{ fontSize: '15px', color: '#1e293b', marginBottom: '6px', fontWeight: '500' }}>채팅방을 삭제하시겠습니까?</p>
            <p style={{ fontSize: '13px', color: '#94a3b8', marginBottom: '24px' }}>삭제된 채팅방과 모든 메시지는 복구할 수 없습니다.</p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '10px' }}>
              <button onClick={() => setShowDeleteConfirm(false)} style={{ padding: '8px 20px', backgroundColor: '#f1f5f9', border: 'none', borderRadius: '8px', cursor: 'pointer', fontSize: '13px', color: '#64748b' }}>취소</button>
              <button onClick={handleDelete} style={{ padding: '8px 20px', backgroundColor: '#dc2626', color: 'white', border: 'none', borderRadius: '8px', cursor: 'pointer', fontSize: '13px', fontWeight: '500' }}>삭제</button>
            </div>
          </div>
        </div>
      )}

      {/* 나가기 완료 */}
      {showLeaveSuccess && (
        <div style={{ position: 'fixed', top: '20px', right: '20px', backgroundColor: '#1e293b', color: 'white', padding: '10px 16px', borderRadius: '8px', fontSize: '13px', boxShadow: '0 4px 12px rgba(0,0,0,0.15)', zIndex: 2000 }}>
          채팅방에서 나갔습니다.
        </div>
      )}
    </div>
  );
};

export default RoomHeader;