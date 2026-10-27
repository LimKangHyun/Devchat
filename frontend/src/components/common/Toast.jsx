import React from 'react';
import { AlertCircle } from 'lucide-react';

const Toast = ({ open, message }) => {
  if (!open) return null;

  return (
    <div
      role="status"
      style={{
        position: 'fixed',
        bottom: 'calc(28px + env(safe-area-inset-bottom))',
        left: '50%',
        transform: 'translateX(-50%)',
        maxWidth: 'calc(100vw - 32px)',
        backgroundColor: 'var(--text)',
        color: '#fff',
        padding: '10px 16px',
        borderRadius: 'var(--radius)',
        fontSize: '14px',
        fontWeight: 500,
        zIndex: 2100,
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
      }}
    >
      <AlertCircle size={16} style={{ flexShrink: 0 }} />
      {message}
    </div>
  );
};

export default Toast;
