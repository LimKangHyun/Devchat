import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosInstance from '../components/api/axiosInstance';
import styles from './Community.module.css';

const Community = () => {
  const navigate = useNavigate();
  const [posts, setPosts] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [filter, setFilter] = useState('hot'); // 'latest' | 'hot'

  useEffect(() => {
    const fetchPosts = async () => {
      try {
        setIsLoading(true);
        const res = await axiosInstance.get(`/community?sort=${filter}`);
        setPosts(res.data.content);
      } catch (error) {
        console.error('게시글 로딩 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchPosts();
  }, [filter]);

  const getDaysLeft = (deadline) => {
    if (!deadline) return null;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const end = new Date(deadline);
    end.setHours(0, 0, 0, 0);
    return Math.ceil((end - today) / (1000 * 60 * 60 * 24));
  };

  return (
    <div className={styles.container}>
      {/* 상단 헤더 영역 */}
      <div className={styles.pageHeader}>
        <div className={styles.pageHeaderLeft}>
          <h1 className={styles.pageTitle}>Community</h1>
          <p className={styles.pageSubtitle}>스터디원을 모집하고 함께 성장해요</p>
        </div>
        <button
          className={styles.writeButton}
          onClick={() => navigate('/community/write')}
        >
          + 글 작성
        </button>
      </div>

      {/* 필터 탭 */}
      <div className={styles.filterTabs}>
        <button
          className={`${styles.filterTab} ${filter === 'hot' ? styles.filterTabActive : ''}`}
          onClick={() => setFilter('hot')}
        >
          🔥 인기순
        </button>
        <button
          className={`${styles.filterTab} ${filter === 'latest' ? styles.filterTabActive : ''}`}
          onClick={() => setFilter('latest')}
        >
          🆕 최신순
        </button>
      </div>

      {/* 게시글 목록 */}
      {isLoading ? (
        <div className={styles.loadingState}>로딩 중...</div>
      ) : posts.length === 0 ? (
        <div className={styles.emptyState}>
          <p>아직 게시글이 없어요</p>
          <button
            className={styles.writeButton}
            onClick={() => navigate('/community/write')}
          >
            첫 글 작성하기
          </button>
        </div>
      ) : (
        <div className={styles.postList}>
        {posts.map((post) => {
          const daysLeft = getDaysLeft(post.deadline);
          const isUrgent = !post.isClosed && daysLeft !== null && daysLeft <= 3 && daysLeft >= 0;

          return (
            <div
              key={post.id}
              className={`${styles.postCard} ${post.isClosed ? styles.postCardClosed : ''} ${isUrgent ? styles.postCardUrgent : ''}`}
              onClick={() => navigate(`/community/${post.id}`)}
            >
              {post.isClosed && <div className={styles.closedOverlay} />}

              <div className={styles.postCardHeader}>
                <div className={styles.postMeta}>
                  <span className={styles.postTag}>{post.tag || '스터디'}</span>
                  {post.isClosed && <span className={styles.closedTag}>마감</span>}
                  {isUrgent && (
                    <span className={styles.urgentTag}>🔥 D-{daysLeft}</span>
                  )}
                  {post.deadline && !post.isClosed && !isUrgent && (
                    <span className={styles.deadlineTag}>{post.deadline} 마감</span>
                  )}
                </div>
                <span className={styles.viewCount}>👁 {post.viewCount ?? 0}</span>
              </div>

              <h2 className={styles.postTitle}>{post.title}</h2>
              <p className={styles.postPreview}>{post.content}</p>

              <div className={styles.postCardFooter}>
                <div className={styles.authorInfo}>
                  <img
                    src={
                      post.authorProfileImg
                        ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${post.authorProfileImg}`
                        : '/images/not-found-profile.png'
                    }
                    alt={post.authorNickname}
                    className={styles.authorAvatar}
                    onError={(e) => { e.target.src = '/images/not-found-profile.png'; }}
                  />
                  <span className={styles.authorName}>{post.authorNickname}</span>
                </div>
                <div className={styles.postStats}>
                  <span>👥 {post.currentCount ?? 0} / {post.maxCount ?? '?'}명</span>
                  <span className={styles.dot}>·</span>
                  <span>{post.createdAt?.slice(0, 10)}</span>
                </div>
              </div>
            </div>
          );
        })}
        </div>
      )}
    </div>
  );
};

export default Community;