import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Loader2, AlertTriangle } from 'lucide-react';
import axiosInstance from '../components/api/axiosInstance';

/**
 * 초대 메일의 "채팅방 입장하기" 링크(/invite/:inviteCode)로 들어오는 화면.
 * 로그인이 안 돼 있으면 axios 인터셉터가 로그인으로 보내고, 로그인 후 이 주소로 돌아온다.
 * 방 정보를 보여주고 입장 버튼을 누르면 참여 후 채팅방으로 이동한다. 이미 참여 중이면 바로 이동한다.
 */
const InviteJoin = () => {
  const { inviteCode } = useParams();
  const navigate = useNavigate();
  const [room, setRoom] = useState(null);
  const [error, setError] = useState(null);
  const [joining, setJoining] = useState(false);

  useEffect(() => {
    let cancelled = false;
    axiosInstance.get(`/chat-rooms/info/${encodeURIComponent(inviteCode)}`)
      .then((res) => { if (!cancelled) setRoom(res.data); })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || '초대 링크가 올바르지 않거나 채팅방이 사라졌어요.');
      });
    return () => { cancelled = true; };
  }, [inviteCode]);

  const goToRoom = () => {
    window.dispatchEvent(new Event('room:read')); // 사이드바 방 목록 갱신
    navigate(`/chat/${inviteCode}`, { replace: true });
  };

  const handleJoin = async () => {
    setJoining(true);
    setError(null);
    try {
      await axiosInstance.post('/chat-rooms/join', { inviteCode });
      goToRoom();
    } catch (err) {
      if (err.response?.status === 409 && err.response?.data?.code === 'CRE-005') {
        goToRoom(); // 이미 참여 중
        return;
      }
      setError(err.response?.data?.message || '채팅방에 입장하지 못했어요. 잠시 후 다시 시도해주세요.');
      setJoining(false);
    }
  };

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100%', padding: 16 }}>
      <div className="dc-modal" style={{ maxWidth: 440, width: '100%', position: 'static' }}>
        <div className="dc-modal-header">
          <h2 className="dc-modal-title">채팅방 초대</h2>
        </div>
        <div className="dc-modal-body">
          {!room && !error && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-2)' }}>
              <Loader2 size={16} style={{ animation: 'dc-spin 1s linear infinite' }} /> 초대 정보를 확인하는 중...
            </div>
          )}
          {room && (
            <p style={{ margin: 0, color: 'var(--text-2)' }}>
              <b style={{ color: 'var(--text)' }}>{room.roomName || '이름 없는 채팅방'}</b>에 초대받았어요. 입장할까요?
            </p>
          )}
          {error && (
            <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start', marginTop: room ? 12 : 0, color: 'var(--danger)', fontSize: 14 }}>
              <AlertTriangle size={15} style={{ flexShrink: 0, marginTop: 2 }} /> <span>{error}</span>
            </div>
          )}
        </div>
        <div className="dc-modal-footer">
          <button type="button" className="dc-btn" onClick={() => navigate('/')} disabled={joining}>홈으로</button>
          {room && (
            <button type="button" className="dc-btn dc-btn-primary" onClick={handleJoin} disabled={joining}>
              {joining ? '입장 중...' : '입장하기'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default InviteJoin;
