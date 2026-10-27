import React from 'react';
import { useNavigate } from 'react-router-dom';

const RoomDeletedModal = ({ isOpen, message, onClose }) => {
  const navigate = useNavigate();

  if (!isOpen) return null;

  const handleConfirm = () => {
    onClose(); // 모달 닫기
    navigate('/'); // 메인 페이지 이동
  };

  return (
    <div className="dc-modal-backdrop" style={{ zIndex: 3000 }}>
      <div className="dc-modal" style={{ maxWidth: 420 }} role="alertdialog" aria-modal="true">
        <div className="dc-modal-header">
          <h3 className="dc-modal-title">채팅방이 삭제되었습니다</h3>
        </div>
        <div className="dc-modal-body">
          <p style={{ margin: 0, color: 'var(--text-2)' }}>{message}</p>
        </div>
        <div className="dc-modal-footer">
          <button type="button" className="dc-btn dc-btn-primary" onClick={handleConfirm}>
            확인
          </button>
        </div>
      </div>
    </div>
  );
};

export default RoomDeletedModal;
