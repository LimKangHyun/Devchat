import React from 'react';
import { FaBan, FaRedo } from 'react-icons/fa';
import { UI_FONT, INACTIVE_REASONS } from './aiReviewUtils';

const ReasonLabel = ({ reason, otherReason }) => {
  const found = INACTIVE_REASONS.find(r => r.value === reason);
  return (
    <span style={{ color: '#e53e3e', fontSize: '11px', fontFamily: UI_FONT }}>
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
}) => (
  <div style={{
    marginLeft: '44px',
    backgroundColor: active ? '#ebf8ff' : '#f7f7f7',
    borderLeft: `4px solid ${active ? '#4299e1' : '#cbd5e0'}`,
    padding: '8px 12px',
    borderBottom: '1px solid #e2e8f0',
    opacity: active ? 1 : 0.6,
    transition: 'opacity 0.2s',
    fontFamily: UI_FONT,
  }}>
    {/* 코멘트 + 버튼/배지 */}
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
      <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start', flex: 1 }}>
        <span style={{ fontSize: '11px', color: active ? '#2b6cb0' : '#a0aec0', fontWeight: '600', flexShrink: 0, fontFamily: UI_FONT }}>
          🤖 AI
        </span>
        <span style={{
          fontSize: '13px', fontFamily: UI_FONT,
          color: active ? '#24292f' : '#a0aec0',
          lineHeight: '1.6', letterSpacing: '-0.01em',
          textDecoration: active ? 'none' : 'line-through',
        }}>
          {review.comment}
        </span>
      </div>

      {/* published 상태에 따라 배지 or 토글 버튼 */}
      {published ? (
        active ? (
          <span style={{
            padding: '3px 8px', borderRadius: '4px',
            backgroundColor: '#f0fff4', border: '1px solid #c6f6d5',
            color: '#276749', fontSize: '11px', fontFamily: UI_FONT,
            flexShrink: 0, whiteSpace: 'nowrap',
          }}>
            ✅ 등록됨
          </span>
        ) : (
          <span style={{
            padding: '3px 8px', borderRadius: '4px',
            backgroundColor: '#f7f7f7', border: '1px solid #e2e8f0',
            color: '#a0aec0', fontSize: '11px', fontFamily: UI_FONT,
            flexShrink: 0, whiteSpace: 'nowrap',
          }}>
            ✕ 미등록
          </span>
        )
      ) : (
        review.commentId != null && (
          active ? (
            <button onClick={() => onDeactivateClick(review.commentId)} title="비활성화" style={btnStyle('#fed7d7', '#e53e3e')}>
              <FaBan size={9} /> 비활성화
            </button>
          ) : (
            <button onClick={() => onReactivateClick(review.commentId)} title="재활성화" style={btnStyle('#c6f6d5', '#276749')}>
              <FaRedo size={9} /> 재활성화
            </button>
          )
        )
      )}
    </div>

    {/* 비활성화 정보 - published 아닐 때만 */}
    {!published && !active && state?.reason && (
      <div style={{ marginTop: '6px', display: 'flex', gap: '10px', fontSize: '11px', color: '#a0aec0', fontFamily: UI_FONT }}>
        <ReasonLabel reason={state.reason} otherReason={state.otherReason} />
        {state.changedBy && state.changedBy !== 'SYSTEM' && <span>비활성화: {state.changedBy}</span>}
      </div>
    )}

    {/* 재활성화 확인 팝업 - published 아닐 때만 */}
    {!published && isShowingReactivateConfirm && (
      <div style={popupStyle}>
        <div style={{ fontSize: '13px', color: '#4a5568', marginBottom: '8px', lineHeight: '1.5', fontFamily: UI_FONT }}>
          이 리뷰를 다시 활성화하시겠습니까?
        </div>
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <button onClick={() => onReactivateCancel(review.commentId)} style={cancelBtnStyle}>취소</button>
          <button onClick={() => onReactivateConfirm(review.commentId, aiReviewId)} style={{ ...actionBtnStyle, backgroundColor: '#276749' }}>재활성화</button>
        </div>
      </div>
    )}

    {/* 비활성화 사유 팝업 - published 아닐 때만 */}
    {!published && isShowingPopup && (
      <div style={popupStyle}>
        <div style={{ fontSize: '12px', fontWeight: '600', color: '#4a5568', marginBottom: '8px', fontFamily: UI_FONT }}>
          비활성화 사유 선택
        </div>
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '8px' }}>
          {INACTIVE_REASONS.map(opt => (
            <button
              key={opt.value}
              onClick={() => onPendingReasonChange(review.commentId, 'reason', opt.value)}
              style={{
                padding: '4px 10px', borderRadius: '12px', border: '1px solid', fontSize: '12px',
                cursor: 'pointer', fontFamily: UI_FONT,
                borderColor: pending.reason === opt.value ? '#4299e1' : '#e2e8f0',
                backgroundColor: pending.reason === opt.value ? '#ebf4ff' : '#fff',
                color: pending.reason === opt.value ? '#2b6cb0' : '#4a5568',
                fontWeight: pending.reason === opt.value ? '600' : '400',
              }}
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
            style={{ width: '100%', padding: '6px 10px', fontSize: '12px', fontFamily: UI_FONT, border: '1px solid #e2e8f0', borderRadius: '4px', marginBottom: '8px', boxSizing: 'border-box', outline: 'none' }}
          />
        )}
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <button onClick={() => onReasonCancel(review.commentId)} style={cancelBtnStyle}>취소</button>
          <button onClick={() => onDeactivateConfirm(review.commentId, aiReviewId)} style={{ ...actionBtnStyle, backgroundColor: '#e53e3e' }}>비활성화</button>
        </div>
      </div>
    )}
  </div>
);

const btnStyle = (borderColor, color) => ({
  padding: '3px 8px', backgroundColor: 'transparent',
  border: `1px solid ${borderColor}`, borderRadius: '4px',
  color, cursor: 'pointer', flexShrink: 0,
  display: 'flex', alignItems: 'center', gap: '4px',
  fontSize: '11px', fontFamily: UI_FONT,
});

const popupStyle = {
  marginTop: '8px', backgroundColor: '#fff',
  border: '1px solid #e2e8f0', borderRadius: '6px',
  padding: '10px 12px', boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
  fontFamily: UI_FONT,
};

const cancelBtnStyle = {
  padding: '4px 10px', fontSize: '12px',
  border: '1px solid #e2e8f0', borderRadius: '4px',
  backgroundColor: '#fff', color: '#718096',
  cursor: 'pointer', fontFamily: UI_FONT,
};

const actionBtnStyle = {
  padding: '4px 10px', fontSize: '12px',
  border: 'none', borderRadius: '4px',
  color: '#fff', cursor: 'pointer',
  fontWeight: '600', fontFamily: UI_FONT,
};

export default CommentCard;