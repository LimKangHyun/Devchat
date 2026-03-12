import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axiosInstance from '../components/api/axiosInstance';
import styles from './CommunityApplicants.module.css';

const CommunityApplicants = () => {
  const navigate = useNavigate();
  const { postId } = useParams();

  const [applicants, setApplicants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState(null);

  useEffect(() => {
    const fetchApplicants = async () => {
      try {
        const res = await axiosInstance.get(`/community/${postId}/applicants`);
        setApplicants(res.data);
      } catch (err) {
        alert(err.response?.data?.message || '신청자 목록을 불러올 수 없습니다.');
        navigate(`/community/${postId}`);
      } finally {
        setLoading(false);
      }
    };
    fetchApplicants();
  }, [postId]);

  const handleApprove = async (applicantId) => {
    try {
      setProcessingId(applicantId);
      await axiosInstance.post(`/community/${postId}/applicants/${applicantId}/approve`);
      setApplicants(prev => prev.filter(a => a.id !== applicantId));
      alert('승인되었습니다.');
    } catch (err) {
      alert(err.response?.data?.message || '승인에 실패했습니다.');
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (applicantId) => {
    if (!window.confirm('거절하시겠습니까?')) return;
    try {
      setProcessingId(applicantId);
      await axiosInstance.post(`/community/${postId}/applicants/${applicantId}/reject`);
      setApplicants(prev => prev.filter(a => a.id !== applicantId));
      alert('거절되었습니다.');
    } catch (err) {
      alert(err.response?.data?.message || '거절에 실패했습니다.');
    } finally {
      setProcessingId(null);
    }
  };

  if (loading) return <div className={styles.loading}>로딩 중...</div>;

  return (
    <div className={styles.container}>
      <div className={styles.card}>

        <div className={styles.cardHeader}>
          <h1 className={styles.title}>신청자 관리</h1>
          <p className={styles.subtitle}>
            총 <strong>{applicants.length}명</strong>이 참여를 신청했습니다
          </p>
        </div>

        <div className={styles.body}>
          {applicants.length === 0 ? (
            <div className={styles.empty}>
              <span className={styles.emptyIcon}>📭</span>
              <p>아직 신청자가 없습니다.</p>
            </div>
          ) : (
            <ul className={styles.list}>
              {applicants.map(applicant => (
                <li key={applicant.id} className={styles.item}>
                  <div className={styles.userInfo}>
                    <img
                      src={applicant.profileImage
                        ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${applicant.profileImage}`
                        : '/images/not-found-profile.png'}
                      alt={applicant.nickname}
                      className={styles.avatar}
                      onError={(e) => { e.target.src = '/images/not-found-profile.png'; }}
                    />
                    <div className={styles.userMeta}>
                      <span className={styles.nickname}>{applicant.nickname}</span>
                      <span className={styles.appliedAt}>
                        {applicant.appliedAt?.slice(0, 10) ?? ''}
                      </span>
                    </div>
                  </div>

                  <div className={styles.actions}>
                    <button
                      className={styles.approveButton}
                      onClick={() => handleApprove(applicant.id)}
                      disabled={processingId === applicant.id}
                    >
                      {processingId === applicant.id ? '처리 중...' : '승인'}
                    </button>
                    <button
                      className={styles.rejectButton}
                      onClick={() => handleReject(applicant.id)}
                      disabled={processingId === applicant.id}
                    >
                      거절
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

      </div>

      <button className={styles.backButton} onClick={() => navigate(`/community/${postId}`)}>
        ← 게시글로
      </button>
    </div>
  );
};

export default CommunityApplicants;