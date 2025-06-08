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
    <div style={{
      position: 'fixed', 
      top: 0, 
      left: 0, 
      width: '100vw', 
      height: '100vh',
      backgroundColor: 'rgba(0, 0, 0, 0.5)', 
      display: 'flex',
      alignItems: 'center', 
      justifyContent: 'center', 
      zIndex: 3000
    }}>
      <div style={{
        backgroundColor: 'white', 
        padding: '32px', 
        borderRadius: '12px',
        textAlign: 'center', 
        boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
        minWidth: '320px',
        maxWidth: '400px'
      }}>
        <div style={{
          fontSize: '20px',
          marginBottom: '8px'
        }}>
          🗑️
        </div>
        <p style={{ 
          fontSize: '16px', 
          marginBottom: '24px',
          lineHeight: '1.5',
          color: '#2d3748'
        }}>
          {message}
        </p>
        <button 
          onClick={handleConfirm}
          style={{
            padding: '12px 24px', 
            backgroundColor: '#2588F1', 
            color: 'white',
            border: 'none', 
            borderRadius: '6px', 
            cursor: 'pointer',
            fontSize: '14px',
            fontWeight: '500'
          }}
        >
          확인
        </button>
      </div>
    </div>
  );
};

export default RoomDeletedModal;