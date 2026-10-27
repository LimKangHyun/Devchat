import sleepingCat from '../sleeping_cat.gif';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import CreateRoomModal from '../components/modals/CreateRoomModal';
import JoinRoomModal from '../components/modals/JoinRoomModal';
import axiosInstance from '../components/api/axiosInstance';
import { LogIn, Plus, ChevronRight } from 'lucide-react';

const BlankRoom = () => {
  const navigate = useNavigate();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showJoinModal, setShowJoinModal] = useState(false);

  // 채팅방 생성 핸들러
  const handleCreateRoom = async (roomName, repoUrl) => {
    try {
      const res = await axiosInstance.post('/chat-rooms', {
        name: roomName,
        repositoryUrl: repoUrl
      });

      const created = res.data;
      setShowCreateModal(false);

      if (created?.id) {
        navigate(`/chat/${created.inviteCode}`);
        window.dispatchEvent(new Event('room:read'));
      }
    } catch (err) {
      const backendMessage = err.response?.data?.message;

      alert(
        backendMessage ||            // 백엔드에서 내려준 메시지
             // JS 오류 메시지
        '방 생성에 실패했습니다.'    // 기본 메시지
      );

      throw err;
    }
  };


  // 채팅방 참여 핸들러
  const handleJoinRoom = async (inviteCode) => {
    try {
      const res = await axiosInstance.post('/chat-rooms/join', { inviteCode });
      const data = res.data;
      setShowJoinModal(false);
      navigate(`/chat/${data.inviteCode}`);
      window.dispatchEvent(new Event('room:read'))  // 사이드바 갱신 트리거
    } catch (err) {
      alert(err.response?.data?.message || err.message || "방 입장에 실패했습니다.");
      throw err;
    }
  };

  const cardStyle = {
    display: 'flex',
    alignItems: 'center',
    gap: '14px',
    width: '100%',
    padding: '14px 16px',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius)',
    background: 'var(--bg)',
    textAlign: 'left',
    cursor: 'pointer',
  };
  const iconStyle = {
    width: '40px',
    height: '40px',
    borderRadius: 'var(--radius)',
    background: 'var(--bg-muted)',
    color: 'var(--text)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  };

  const ActionCard = ({ icon, title, desc, onClick }) => (
    <button
      type="button"
      style={cardStyle}
      onClick={onClick}
      onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-soft)')}
      onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--bg)')}
    >
      <span style={iconStyle}>{icon}</span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontSize: '15px', fontWeight: 700, color: 'var(--text)' }}>{title}</span>
        <span style={{ display: 'block', fontSize: '13px', color: 'var(--text-2)', marginTop: '2px' }}>{desc}</span>
      </span>
      <ChevronRight size={18} color="var(--text-3)" />
    </button>
  );

  return (
    <div style={{
      minHeight: '100%',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '32px 20px',
      boxSizing: 'border-box',
    }}>
      <div style={{ width: '100%', maxWidth: '440px' }}>
        <img src={sleepingCat} alt="" style={{ width: '120px', display: 'block', marginBottom: '12px' }} />
        <h2 style={{ fontSize: '28px', fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.5px', margin: '0 0 6px' }}>
          아직 참여한 방이 없어요
        </h2>
        <p style={{ fontSize: '15px', color: 'var(--text-2)', margin: '0 0 24px', lineHeight: 1.5 }}>
          GitHub 레포를 연결해 방을 만들면 PR 소식과 AI 코드 리뷰가 대화에 함께 올라와요.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <ActionCard
            icon={<Plus size={18} />}
            title="새 방 만들기"
            desc="이름을 정하고 레포지토리를 연결하세요"
            onClick={() => setShowCreateModal(true)}
          />
          <ActionCard
            icon={<LogIn size={17} />}
            title="초대 코드로 들어가기"
            desc="팀원에게 받은 초대 코드를 입력하세요"
            onClick={() => setShowJoinModal(true)}
          />
        </div>
      </div>

      {showCreateModal && (
        <CreateRoomModal
          onClose={() => setShowCreateModal(false)}
          onSubmit={handleCreateRoom}
        />
      )}

      {showJoinModal && (
        <JoinRoomModal
          onClose={() => setShowJoinModal(false)}
          onSubmit={handleJoinRoom}
        />
      )}
    </div>
  );
};

export default BlankRoom;
