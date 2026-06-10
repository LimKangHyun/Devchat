import React from 'react';
import { FaGithub } from 'react-icons/fa';
import { UI_FONT, CODE_FONT, buildDiffRows, getGitHubFileUrl } from './aiReviewUtils';
import CommentCard from './CommentCard';

const DiffViewer = ({
  file, aiReviewId, isActive, commentStates,
  showReasonPopup, showReactivateConfirm, pendingReason,
  onDeactivateClick, onReasonCancel, onDeactivateConfirm,
  onReactivateClick, onReactivateConfirm, onReactivateCancel,
  onPendingReasonChange,
  repositoryUrl, prNumber, published,
}) => {
  const rows = file.skipped ? [] : buildDiffRows(file.baseContent, file.fileContent);
  const reviews = file.reviews || [];

  const handlePathClick = async () => {
    if (repositoryUrl && prNumber) {
      const url = await getGitHubFileUrl(repositoryUrl, prNumber, file.filePath);
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  };

  return (
    <div style={{ flex: 1, overflow: 'auto', backgroundColor: '#fff', fontFamily: UI_FONT }}>
      {/* 파일 경로 헤더 */}
      <div
        onClick={handlePathClick}
        title={repositoryUrl ? 'GitHub에서 열기' : ''}
        onMouseEnter={e => { if (repositoryUrl) e.currentTarget.style.backgroundColor = '#eaf0f6'; }}
        onMouseLeave={e => { e.currentTarget.style.backgroundColor = '#f6f8fa'; }}
        style={{
          padding: '8px 16px', backgroundColor: '#f6f8fa',
          borderBottom: '1px solid #e2e8f0',
          fontSize: '12.5px', color: '#57606a',
          fontFamily: CODE_FONT, fontWeight: '400', letterSpacing: '-0.02em',
          position: 'sticky', top: 0, zIndex: 10,
          cursor: repositoryUrl ? 'pointer' : 'default',
          display: 'flex', alignItems: 'center', gap: '6px',
          transition: 'background-color 0.15s',
        }}
      >
        {repositoryUrl && <FaGithub size={12} color="#57606a" style={{ flexShrink: 0 }} />}
        {file.filePath}
      </div>

      {file.skipped ? (
        <div style={{
          padding: '12px 16px', margin: '12px',
          backgroundColor: '#fffbeb', border: '1px solid #fbd38d',
          borderRadius: '6px', fontSize: '12px', color: '#b7791f',
        }}>
          ⏭️ 이 파일은 diff 라인 수(250줄)가 초과되어 리뷰가 생략되었습니다.
        </div>
      ) : (
        <div style={{ display: 'flex', minWidth: 0 }}>
          {/* 변경 전 */}
          <div style={{ flex: 1, borderRight: '2px solid #e2e8f0', minWidth: 0 }}>
            <SideHeader label="변경 전" bg="#fff5f5" border="#fed7d7" />
            {rows.map((row, idx) => (
              <div key={idx} style={{
                display: 'flex', minHeight: '22px',
                backgroundColor: row.type === 'removed' ? '#fff5f5' : row.type === 'added' ? '#f7f8fa' : 'transparent',
              }}>
                <LineNum>{row.baseNum ?? ''}</LineNum>
                <CodeCell color={row.type === 'added' ? 'transparent' : '#24292f'}>
                  {row.baseLine ?? ''}
                </CodeCell>
              </div>
            ))}
          </div>

          {/* 변경 후 + AI 코멘트 */}
          <div style={{ flex: 1, minWidth: 0 }}>
            <SideHeader label="변경 후" bg="#f0fff4" border="#c6f6d5" />
            {rows.map((row, idx) => {
              const lineReviews = row.headNum ? reviews.filter(r => r.lineNumber === row.headNum) : [];
              const hasActive = lineReviews.some(r => isActive(r));

              return (
                <div key={idx}>
                  <div style={{
                    display: 'flex', minHeight: '22px',
                    backgroundColor: hasActive ? '#ebf4ff' : row.type === 'added' ? '#f0fff4' : row.type === 'removed' ? '#f7f8fa' : 'transparent',
                    borderLeft: hasActive ? '3px solid #4299e1' : '3px solid transparent',
                  }}>
                    <LineNum>{row.headNum ?? ''}</LineNum>
                    <CodeCell color={row.type === 'removed' ? 'transparent' : '#24292f'}>
                      {row.headLine ?? ''}
                    </CodeCell>
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

const SideHeader = ({ label, bg, border }) => (
  <div style={{
    padding: '4px 12px', fontSize: '11px', color: '#a0aec0',
    backgroundColor: bg, fontWeight: '500', fontFamily: UI_FONT,
    borderBottom: `1px solid ${border}`, textAlign: 'center',
  }}>
    {label}
  </div>
);

const LineNum = ({ children }) => (
  <div style={{
    width: '44px', flexShrink: 0,
    padding: '0 8px', color: '#a0aec0',
    fontSize: '11px', textAlign: 'right',
    lineHeight: '22px', userSelect: 'none',
    borderRight: '1px solid #f0f0f0',
    backgroundColor: 'rgba(0,0,0,0.02)',
    fontFamily: CODE_FONT,
  }}>
    {children}
  </div>
);

const CodeCell = ({ children, color }) => (
  <div style={{
    flex: 1, padding: '0 12px',
    fontFamily: CODE_FONT, fontSize: '12.5px',
    lineHeight: '22px', whiteSpace: 'pre', overflow: 'hidden',
    letterSpacing: '-0.02em', color,
  }}>
    {children}
  </div>
);

export default DiffViewer;