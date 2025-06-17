import React from 'react';

const NewMessageAlert = ({ roomName, content, senderProfile, senderNickname, onClose, onNavigate }) => {
  return (
    <div style={{
      position: 'fixed',
      bottom: '24px',
      right: '24px',
      width: '300px',
      backgroundColor: '#fff',
      color: '#333',
      boxShadow: '0 2px 12px rgba(0,0,0,0.2)',
      borderRadius: '8px',
      padding: '16px',
      zIndex: 1000,
      transition: 'opacity 0.3s ease-in-out',
      fontSize: '14px'
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '8px'
      }}>
        <strong style={{ display: 'flex', alignItems: 'center', gap: '13px' }}>
          {roomName}
        </strong>

        <button
          onClick={onClose}
          style={{
            border: 'none',
            background: 'transparent',
            fontSize: '25px',
            cursor: 'pointer',
            lineHeight: '1'
          }}
          aria-label="닫기"
        >
          ×
        </button>
      </div>

      {/* 발신자 */}
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: '6px', gap: '8px' }}>
        <img
          src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${senderProfile}`}
          alt="sender profile"
          style={{
            width: '26px',
            height: '26px',
            borderRadius: '50%',
            objectFit: 'cover'
          }}
        />
        <span style={{ fontWeight: 600 }}>{senderNickname || '알 수 없음'}</span>
      </div>

      <div style={{
        marginBottom: '12px',
        color: '#555',
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis'
      }}>
        {content}        
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <button
          onClick={onNavigate}
          style={{
            backgroundColor: '#2588F1',
            color: '#fff',
            border: 'none',
            borderRadius: '4px',
            padding: '6px 12px',
            cursor: 'pointer',
            fontSize: '13px'
          }}
        >
          채팅방으로 이동
        </button>
      </div>
    </div>
  );
};

export default NewMessageAlert;