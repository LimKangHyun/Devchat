import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

const ErrorPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  
  const { title, message } = location.state || {
    title: '페이지를 찾을 수 없습니다',
    message: '요청하신 페이지가 존재하지 않습니다.'
  };

  return (
    <div style={{ 
      height: '100vh', 
      display: 'flex', 
      flexDirection: 'column',
      justifyContent: 'center', 
      alignItems: 'center',
      backgroundColor: '#f5f7fa',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif'
    }}>
      <div style={{ fontSize: '72px', marginBottom: '24px' }}>😵</div>
      <h1 style={{ 
        fontSize: '28px', 
        color: '#dc3545', 
        marginBottom: '16px',
        textAlign: 'center',
        fontWeight: '600'
      }}>
        {title}
      </h1>
      <p style={{ 
        fontSize: '16px', 
        color: '#666', 
        marginBottom: '32px', 
        textAlign: 'center',
        maxWidth: '500px',
        lineHeight: '1.5'
      }}>
        {message}
      </p>
      <button 
        onClick={() => navigate('/')}
        style={{
          padding: '12px 24px',
          backgroundColor: '#2588F1',
          color: 'white',
          border: 'none',
          borderRadius: '8px',
          cursor: 'pointer',
          fontSize: '16px',
          fontWeight: '500',
          transition: 'background-color 0.2s ease'
        }}
        onMouseEnter={(e) => e.target.style.backgroundColor = '#1c6ec7'}
        onMouseLeave={(e) => e.target.style.backgroundColor = '#2588F1'}
      >
        메인으로 돌아가기
      </button>
    </div>
  );
};

export default ErrorPage;