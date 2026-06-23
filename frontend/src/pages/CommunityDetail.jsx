import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axiosInstance from '../components/api/axiosInstance';
import styles from './CommunityDetail.module.css';
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useUser } from '../context/UserContext';

const CommunityDetail = () => {
  const navigate = useNavigate();
  const { postId } = useParams();
  const { currentUser } = useUser()

  const [post, setPost] = useState(null);
  const [isApplying, setIsApplying] = useState(false);
  const [hasApplied, setHasApplied] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const postRes = await axiosInstance.get(`/community/${postId}`)
        setPost(postRes.data);
      } catch (err) {
        alert('게시글을 불러올 수 없습니다.');
        navigate('/community');
      }
    };
    fetchData();
  }, [postId]);

  const isOwner = post && currentUser && post.authorNickname === currentUser.nickname;

  const handleApply = async () => {
    try {
      setIsApplying(true);
      await axiosInstance.post(`/community/${postId}/apply`);
      setHasApplied(true);
      alert('신청이 완료되었습니다!');
    } catch (err) {
      alert(err.response?.data?.message || '신청에 실패했습니다.');
    } finally {
      setIsApplying(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('정말 삭제하시겠습니까?')) return;
    try {
      await axiosInstance.delete(`/community/${postId}`);
      navigate('/community');
    } catch (err) {
      alert(err.response?.data?.message || '삭제에 실패했습니다.');
    }
  };

  if (!post) return <div className={styles.loading}>로딩 중...</div>;

  const techStacks = post.techStacks?.length > 0 ? post.techStacks : [];

  return (
    <div className={styles.container}>
      <div className={styles.card}>

        <div className={styles.cardHeader}>
          <div className={styles.metaRow}>
            <div className={styles.tags}>
              {post.tag && <span className={styles.tag}>{post.tag}</span>}
              {post.mode && <span className={styles.modeTag}>{post.mode}</span>}
              {post.closed
                ? <span className={styles.closedTag}>마감</span>
                : post.deadline && <span className={styles.deadlineTag}>~{post.deadline} 마감</span>
              }
            </div>
            <span className={styles.viewCount}>👁 {post.viewCount}</span>
          </div>

          <h1 className={styles.title}>{post.title}</h1>

          <div className={styles.authorRow}>
            <div className={styles.authorInfo}>
              <img
                src={post.authorProfileImg
                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${post.authorProfileImg}`
                  : '/images/not-found-profile.webp'}
                alt={post.authorNickname}
                className={styles.avatar}
                onError={(e) => { e.target.src = '/images/not-found-profile.webp'; }}
              />
              <span className={styles.authorName}>{post.authorNickname}</span>
              <span className={styles.dot}>·</span>
              <span className={styles.createdAt}>{post.createdAt?.slice(0, 10)}</span>
            </div>

            {isOwner && (
              <div className={styles.ownerButtons}>
                <button className={styles.editButton} onClick={() => navigate(`/community/${postId}/edit`)}>
                  수정
                </button>
                <button className={styles.deleteButton} onClick={handleDelete}>
                  삭제
                </button>
              </div>
            )}
          </div>
        </div>

        <div className={styles.infoGrid}>
          <div className={styles.infoItem}>
            <span className={styles.infoLabel}>모집 인원</span>
            <span className={styles.infoValue}>{post.currentCount} / {post.maxCount}명</span>
          </div>
          {post.deadline && (
            <div className={styles.infoItem}>
              <span className={styles.infoLabel}>마감일</span>
              <span className={styles.infoValue}>{post.deadline}</span>
            </div>
          )}
          {post.mode && (
            <div className={styles.infoItem}>
              <span className={styles.infoLabel}>진행 방식</span>
              <span className={styles.infoValue}>{post.mode}</span>
            </div>
          )}
        </div>

        {techStacks.length > 0 && (
          <div className={styles.techSection}>
            <p className={styles.sectionLabel}>기술 스택</p>
            <div className={styles.techStacks}>
              {techStacks.map(stack => (
                <span key={stack} className={styles.techChip}>{stack}</span>
              ))}
            </div>
          </div>
        )}

        <div className={styles.content}>
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              table: ({node, ...props}) => (
                <div className={styles.tableWrapper}>
                  <table className={styles.table} {...props} />
                </div>
              ),
              thead: ({node, ...props}) => <thead className={styles.thead} {...props} />,
              th: ({node, ...props}) => <th className={styles.th} {...props} />,
              td: ({node, ...props}) => <td className={styles.td} {...props} />,
              tr: ({node, ...props}) => <tr className={styles.tr} {...props} />,
              input: ({node, ...props}) => (
                <input className={styles.checkbox} {...props} readOnly />
              ),
            }}
          >
            {post.content ?? ""}
          </ReactMarkdown>
        </div>

        {!isOwner && (
          <div className={styles.applySection}>
            <div className={styles.applyInfo}>
              <span className={styles.applyCount}>
                현재 {post.currentCount}명 참여 중 · {post.maxCount - post.currentCount}명 모집 중
              </span>
            </div>
            <button
              className={`${styles.applyButton} ${(post.closed || hasApplied) ? styles.applyButtonDisabled : ''}`}
              onClick={handleApply}
              disabled={post.closed || hasApplied || isApplying}
            >
              {post.closed ? '마감된 모집글' : hasApplied ? '신청 완료' : isApplying ? '신청 중...' : '스터디 신청하기'}
            </button>
          </div>
        )}

        {isOwner && (
          <div className={styles.applicantSection}>
            <button className={styles.applicantButton} onClick={() => navigate(`/community/${postId}/applicants`)}>
              신청자 관리
            </button>
          </div>
        )}
      </div>

      <button className={styles.backButton} onClick={() => navigate('/community')}>
        ← 목록으로
      </button>
    </div>
  );
};

export default CommunityDetail;