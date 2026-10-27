import React from 'react';
import { FaGithub } from 'react-icons/fa';
import { buildDiffRows, getGitHubFileUrl } from './aiReviewUtils';
import CommentCard from './CommentCard';
import './ReviewModals.css';

const DiffViewer = ({
  file, aiReviewId, isActive, commentStates,
  showReasonPopup, showReactivateConfirm, pendingReason,
  onDeactivateClick, onReasonCancel, onDeactivateConfirm,
  onReactivateClick, onReactivateConfirm, onReactivateCancel,
  onPendingReasonChange,
  repositoryUrl, prNumber, published,
}) => {
  const rows = file.skipped ? [] : buildDiffRows(file.beforeContent, file.fileContent);
  const reviews = file.reviews || [];

  const handlePathClick = async () => {
    if (repositoryUrl && prNumber) {
      const url = await getGitHubFileUrl(repositoryUrl, prNumber, file.filePath);
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  };

  return (
    <div className="rv-diff dc-scroll">
      {/* 파일 경로 헤더 */}
      <div
        onClick={handlePathClick}
        title={repositoryUrl ? 'GitHub에서 열기' : ''}
        className={`rv-diff-path${repositoryUrl ? ' is-link' : ''}`}
      >
        {repositoryUrl && <FaGithub size={13} />}
        {file.filePath}
      </div>

      {file.skipped ? (
        <div className="rv-notice">
          이 파일은 diff 라인 수(250줄)가 초과되어 리뷰가 생략되었습니다.
        </div>
      ) : (
        <div className="rv-diff-cols">
          {/* 변경 전 */}
          <div className="rv-diff-col">
            <SideHeader label="변경 전" />
            {rows.map((row, idx) => (
              <div
                key={idx}
                className={`rv-drow${row.type === 'removed' ? ' is-removed' : row.type === 'added' ? ' is-empty' : ''}`}
              >
                <LineNum>{row.baseNum ?? ''}</LineNum>
                <CodeCell>{row.baseLine ?? ''}</CodeCell>
              </div>
            ))}
          </div>

          {/* 변경 후 + AI 코멘트 */}
          <div className="rv-diff-col">
            <SideHeader label="변경 후" />
            {rows.map((row, idx) => {
              const lineReviews = row.headNum ? reviews.filter(r => r.lineNumber === row.headNum) : [];
              const hasActive = lineReviews.some(r => isActive(r));
              const typeClass = row.type === 'added' ? ' is-added' : row.type === 'removed' ? ' is-empty' : '';

              return (
                <div key={idx}>
                  <div className={`rv-drow${typeClass}${hasActive ? ' has-comment' : ''}`}>
                    <LineNum>{row.headNum ?? ''}</LineNum>
                    <CodeCell>{row.headLine ?? ''}</CodeCell>
                  </div>

                  {lineReviews.map((review, rIdx) => (
                    <CommentCard
                      key={rIdx}
                      review={review}
                      aiReviewId={aiReviewId}
                      active={isActive(review)}
                      state={review.commentId != null ? commentStates[review.commentId] : null}
                      isShowingPopup={review.commentId != null && !!showReasonPopup[review.commentId]}
                      isShowingReactivateConfirm={review.commentId != null && !!showReactivateConfirm[review.commentId]}
                      pending={review.commentId != null ? (pendingReason[review.commentId] || {}) : {}}
                      onDeactivateClick={onDeactivateClick}
                      onReasonCancel={onReasonCancel}
                      onDeactivateConfirm={onDeactivateConfirm}
                      onReactivateClick={onReactivateClick}
                      onReactivateConfirm={onReactivateConfirm}
                      onReactivateCancel={onReactivateCancel}
                      onPendingReasonChange={onPendingReasonChange}
                      published={published}
                    />
                  ))}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

const SideHeader = ({ label }) => (
  <div className="rv-diff-side">{label}</div>
);

const LineNum = ({ children }) => (
  <div className="rv-dnum">{children}</div>
);

const CodeCell = ({ children }) => (
  <div className="rv-dcode">{children}</div>
);

export default DiffViewer;
