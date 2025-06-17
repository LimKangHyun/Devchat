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

     {/* 헤더: 닉네임 + 채팅방 이름 + 닫기 버튼 */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '10px'
      }}>
        <div style={{ display: 'flex', gap: '12px'}}>
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
          <span style={{ fontSize: '13px', color: '#777' }}>({roomName})</span>
        </div>
        <button
          onClick={onClose}
          style={{
            border: 'none',
            background: 'transparent',
            fontSize: '22px',
            cursor: 'pointer',
            lineHeight: '1'
          }}
          aria-label="닫기"
        >
          ×
        </button>
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