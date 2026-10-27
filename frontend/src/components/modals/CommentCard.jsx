import React from 'react';
import { FaBan, FaRedo, FaCheck, FaTimes } from 'react-icons/fa';
import { INACTIVE_REASONS } from './aiReviewUtils';
import './ReviewModals.css';

const ReasonLabel = ({ reason, otherReason }) => {
  const found = INACTIVE_REASONS.find(r => r.value === reason);
  return (
    <span className="rv-reason-label">
      사유: {found?.label ?? reason}
      {reason === 'OTHER' && otherReason ? ` — ${otherReason}` : ''}
    </span>
  );
};

const CommentCard = ({
  review, aiReviewId, active, state,
  isShowingPopup, isShowingReactivateConfirm, pending,
  onDeactivateClick, onReasonCancel, onDeactivateConfirm,
  onReactivateClick, onReactivateConfirm, onReactivateCancel,
  onPendingReasonChange,
  published,
}) => {
  const isGeneralComment = review.diffLine == null;

  return (
    <div className={`rv-ai-comment${active ? '' : ' is-inactive'}`}>
      <div className="rv-avatar is-bot" aria-hidden="true">AI</div>

      <div className="rv-msg-body">
        {/* 이름 + 버튼/배지 */}
        <div className="rv-msg-head">
          <span className="rv-msg-name">AI 리뷰</span>

          <span className="rv-msg-tools">
            {/* published 상태에 따라 배지 or 토글 버튼 */}
            {published ? (
              active ? (
                <span className="rv-badge is-ok">
                  <FaCheck size={9} /> 등록됨
                </span>
              ) : (
                <span className="rv-badge">
                  <FaTimes size={9} /> 미등록
                </span>
              )
            ) : (
              review.commentId != null && (
                active ? (
                  <button
                    type="button"
                    onClick={() => onDeactivateClick(review.commentId)}
                    title="비활성화"
                    className="rv-chip-btn is-danger"
                  >
                    <FaBan size={10} /> 비활성화
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => onReactivateClick(review.commentId)}
                    title="재활성화"
                    className="rv-chip-btn"
                  >
                    <FaRedo size={10} /> 재활성화
                  </button>
                )
              )
            )}
          </span>
        </div>

        {/* 코멘트 */}
        <div className="rv-msg-text">{review.comment}</div>

        {isGeneralComment && (
          <div className="rv-inline-note">
            diff 변경 범위에서 멀어 GitHub에는 이 줄이 아닌 전체 코멘트로 등록됩니다
          </div>
        )}

        {/* 비활성화 정보 - published 아닐 때만 */}
        {!published && !active && state?.reason && (
          <div className="rv-reason">
            <ReasonLabel reason={state.reason} otherReason={state.otherReason} />
            {state.changedBy && state.changedBy !== 'SYSTEM' && <span>비활성화: {state.changedBy}</span>}
          </div>
        )}

        {/* 재활성화 확인 팝업 - published 아닐 때만 */}
        {!published && isShowingReactivateConfirm && (
          <div className="rv-popover">
            <div className="rv-popover-text">이 리뷰를 다시 활성화하시겠습니까?</div>
            <div className="rv-popover-actions">
              <button type="button" onClick={() => onReactivateCancel(review.commentId)} className="dc-btn dc-btn-ghost">
                취소
              </button>
              <button type="button" onClick={() => onReactivateConfirm(review.commentId, aiReviewId)} className="dc-btn dc-btn-primary">
                재활성화
              </button>
            </div>
          </div>
        )}

        {/* 비활성화 사유 팝업 - published 아닐 때만 */}
        {!published && isShowingPopup && (
          <div className="rv-popover">
            <div className="rv-popover-title">비활성화 사유 선택</div>
            <div className="rv-options">
              {INACTIVE_REASONS.map(opt => (
                <button
                  type="button"
                  key={opt.value}
                  onClick={() => onPendingReasonChange(review.commentId, 'reason', opt.value)}
                  className={`rv-option${pending.reason === opt.value ? ' is-selected' : ''}`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
            {pending.reason === 'OTHER' && (
              <input
                type="text" maxLength={100}
                placeholder="기타 사유를 입력하세요 (100자 이내)"
                value={pending.otherReason || ''}
                onChange={e => onPendingReasonChange(review.commentId, 'otherReason', e.target.value)}
                className="dc-input"
              />
            )}
            <div className="rv-popover-actions">
              <button type="button" onClick={() => onReasonCancel(review.commentId)} className="dc-btn dc-btn-ghost">
                취소
              </button>
              <button type="button" onClick={() => onDeactivateConfirm(review.commentId, aiReviewId)} className="dc-btn dc-btn-danger">
                비활성화
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CommentCard;
