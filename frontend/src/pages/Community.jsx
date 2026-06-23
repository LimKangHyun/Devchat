import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosInstance from '../components/api/axiosInstance';
import styles from './Community.module.css';

const Community = () => {
  const navigate = useNavigate();
  const [posts, setPosts] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [filter, setFilter] = useState('hot');         // 'latest' | 'hot'
  const [activeOnly, setActiveOnly] = useState(false); // 모집중만 보기
  const [myApplied, setMyApplied] = useState(false);   // 내 신청 보기 (PENDING)

  useEffect(() => {
    const fetchPosts = async () => {
      try {
        setIsLoading(true);
        const res = await axiosInstance.get(
          `/community?sort=${filter}&activeOnly=${activeOnly}&myApplied=${myApplied}`
        );
        setPosts(res.data.content);
      } catch (error) {
        console.error('게시글 로딩 실패:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchPosts();
  }, [filter, activeOnly, myApplied]);

  const getDaysLeft = (deadline) => {
    if (!deadline) return null;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const end = new Date(deadline);
    end.setHours(0, 0, 0, 0);
    return Math.ceil((end - today) / (1000 * 60 * 60 * 24));
  };

  // 내 신청 ON → activeOnly 해제 (상호 배타)
  const handleMyAppliedToggle = () => {
    setMyApplied((prev) => {
      if (!prev) setActiveOnly(false);
      return !prev;
    });
  };

  // 모집중 ON → myApplied 해제 (상호 배타)
  const handleActiveOnlyToggle = () => {
    setActiveOnly((prev) => {
      if (!prev) setMyApplied(false);
      return !prev;
    });
  };

  const emptyMessage = myApplied
    ? '승인 대기중인 신청이 없어요'
    : activeOnly
    ? '모집중인 게시글이 없어요'
    : '아직 게시글이 없어요';

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

      {/* 필터 영역 */}
      <div className={styles.filterRow}>
        {/* 정렬 탭: 내 신청 보기 중엔 흐리게 */}
        <div className={styles.filterTabs}>
          <button
            className={`${styles.filterTab} ${!myApplied && filter === 'hot' ? styles.filterTabActive : ''} ${myApplied ? styles.filterTabDisabled : ''}`}
            onClick={() => { if (!myApplied) setFilter('hot'); }}
          >
            🔥 인기순
          </button>
          <button
            className={`${styles.filterTab} ${!myApplied && filter === 'latest' ? styles.filterTabActive : ''} ${myApplied ? styles.filterTabDisabled : ''}`}
            onClick={() => { if (!myApplied) setFilter('latest'); }}
          >
            🆕 최신순
          </button>
        </div>

        {/* 우측 토글 */}
        <div className={styles.toggleGroup}>
          <button
            className={`${styles.activeOnlyToggle} ${activeOnly ? styles.activeOnlyToggleOn : ''}`}
            onClick={handleActiveOnlyToggle}
          >
            <span className={styles.activeOnlyDot} />
            모집중만 보기
          </button>
          <button
            className={`${styles.activeOnlyToggle} ${myApplied ? styles.myAppliedToggleOn : ''}`}
            onClick={handleMyAppliedToggle}
          >
            <span className={`${styles.activeOnlyDot} ${myApplied ? styles.myAppliedDot : ''}`} />
            내 신청 보기
          </button>
        </div>
      </div>

      {/* 게시글 목록 */}
      {isLoading ? (
        <div className={styles.loadingState}>로딩 중...</div>
      ) : posts.length === 0 ? (
        <div className={styles.emptyState}>
          <p>{emptyMessage}</p>
          {!myApplied && (
            <button
              className={styles.writeButton}
              onClick={() => navigate('/community/write')}
            >
              첫 글 작성하기
            </button>
          )}
        </div>
      ) : (
        <div className={styles.postList}>
          {posts.map((post) => {
            const daysLeft = getDaysLeft(post.deadline);
            const isFull = post.maxCount != null && post.currentCount >= post.maxCount;
            const isExpired = daysLeft !== null && daysLeft < 0;
            const isClosed = post.isClosed || isFull || isExpired;
            const isUrgent = !isClosed && daysLeft !== null && daysLeft <= 3 && daysLeft >= 0;

            const closedType = isExpired ? 'expired' : isFull ? 'full' : post.isClosed ? 'manual' : null;

            return (
              <div
                key={post.id}
                className={`
                  ${styles.postCard}
                  ${closedType === 'expired' ? styles.postCardExpired : ''}
                  ${(closedType === 'full' || closedType === 'manual') ? styles.postCardClosed : ''}
                  ${isUrgent ? styles.postCardUrgent : ''}
                `}
                onClick={() => navigate(`/community/${post.id}`)}
              >
                {closedType === 'expired' && <div className={styles.expiredOverlay} />}
                {(closedType === 'full' || closedType === 'manual') && <div className={styles.closedOverlay} />}

                <div className={styles.postCardHeader}>
                  <div className={styles.postMeta}>
                    <span className={styles.postTag}>{post.tag || '스터디'}</span>
                    {closedType === 'expired' && <span className={styles.expiredTag}>기간마감</span>}
                    {closedType === 'full' && <span className={styles.closedTag}>정원마감</span>}
                    {closedType === 'manual' && <span className={styles.closedTag}>마감</span>}
                    {isUrgent && (
                      <span className={styles.urgentTag}>🔥 D-{daysLeft}</span>
                    )}
                    {post.deadline && !isClosed && !isUrgent && (
                      <span className={styles.deadlineTag}>{post.deadline} 마감</span>
                    )}
                    {/* 내 신청 보기 중일 때 대기중 뱃지 */}
                    {myApplied && (
                      <span className={styles.pendingTag}>⏳ 승인 대기중</span>
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
                          : '/images/not-found-profile.webp'
                      }
                      alt={post.authorNickname}
                      className={styles.authorAvatar}
                      onError={(e) => { e.target.src = '/images/not-found-profile.webp'; }}
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