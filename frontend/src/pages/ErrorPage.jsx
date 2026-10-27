import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import styles from './Auth.module.css';

const ErrorPage = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const { title, message } = location.state || {
    title: '페이지를 찾을 수 없습니다',
    message: '요청하신 페이지가 존재하지 않습니다.'
  };

  return (
    <div className={`${styles.page} ${styles.errorPage}`}>
      <a href="/" className={styles.brand}>
        <span className={styles.brandName}><span className={styles.brandMark}>dev</span>chat</span>
      </a>
      <div className={styles.card}>
        <div className={styles.errorCode}>Error</div>
        <h1 className={`${styles.heading} ${styles.errorHeading}`}>
          {title}
        </h1>
        <p className={styles.sub}>
          {message}
        </p>
        <div className={styles.errorActions}>
          <button
            type="button"
            className="dc-btn dc-btn-primary"
            onClick={() => navigate('/')}
          >
            메인으로 돌아가기
          </button>
        </div>
      </div>
    </div>
  );
};

export default ErrorPage;
