import React, { useState } from 'react';
import { AlertTriangle, Check, RotateCcw, X } from 'lucide-react';
import s from './Chat.module.css';

// 단계별로 전체 진행률에서 차지하는 구간. 저장(임베딩) 단계가 가장 오래 걸려서 가장 넓게 잡는다.
const PHASE_RANGE = {
  CLONING: [0, 5],
  CHUNKING: [5, 20],
  EMBEDDING: [20, 100],
};

const overallPercent = ({ phase, done, total }) => {
  const range = PHASE_RANGE[phase];
  if (!range) return 0;
  const [from, to] = range;
  const ratio = total > 0 ? Math.min(done / total, 1) : 0;
  return Math.round(from + (to - from) * ratio);
};

const runningText = ({ phase, done, total }) => {
  switch (phase) {
    case 'CLONING': return '레포지토리를 내려받는 중';
    case 'CHUNKING': return `코드 분석 중 · ${done}/${total} 파일`;
    case 'EMBEDDING': return `코드 저장 중 · ${done}/${total} 조각`;
    default: return '인덱싱을 준비하는 중';
  }
};

/**
 * 저장소 인덱싱 상태 표시줄.
 * RUNNING이면 단계별 진행률을, 끝난 뒤에는 결과를 보여준다. 취소·다시 시작 버튼은 방장에게만 보인다.
 */
const IndexingBar = ({ indexing, isOwner, cancelling, onCancel, onRestart }) => {
  const [confirming, setConfirming] = useState(false);
  if (!indexing) return null;

  const { status } = indexing;

  if (status === 'RUNNING') {
    const percent = overallPercent(indexing);
    return (
      <div className={s.indexingBar}>
        <div className={s.indexingRow}>
          <div className={s.indexingText}>
            <span className={s.miniSpinner} />
            <span>{cancelling ? '인덱싱을 취소하는 중' : runningText(indexing)}</span>
            {!cancelling && <span className={s.indexingPercent}>{percent}%</span>}
          </div>
          {isOwner && !cancelling && (
            confirming ? (
              <div className={s.indexingActions}>
                <span className={s.indexingHint}>저장한 코드도 모두 지워져요. 취소할까요?</span>
                <button type="button" className={`dc-btn ${s.indexingBtn}`} onClick={() => setConfirming(false)}>계속 진행</button>
                <button type="button" className={`dc-btn dc-btn-danger ${s.indexingBtn}`}
                  onClick={() => { setConfirming(false); onCancel(); }}>취소</button>
              </div>
            ) : (
              <button type="button" className={`dc-btn ${s.indexingBtn}`} onClick={() => setConfirming(true)}>
                <X size={13} /> 취소
              </button>
            )
          )}
        </div>
        <div className={s.indexingTrack}>
          <div className={s.indexingFill} style={{ width: `${percent}%` }} />
        </div>
      </div>
    );
  }

  if (status === 'COMPLETED') {
    return (
      <div className={s.indexingBar}>
        <div className={`${s.indexingText} ${s.indexingTextOnly}`}>
          <Check size={14} /> 코드 인덱싱을 마쳤어요. 이제 AI 리뷰가 관련 코드를 함께 참고해요.
        </div>
      </div>
    );
  }

  // CANCELLED, FAILED, NONE: 다시 시작할 수 있는 상태. 방장이 아니면 할 수 있는 게 없어 숨긴다.
  if (!isOwner) return null;
  const message = {
    CANCELLED: '인덱싱을 취소했어요. 저장했던 코드는 모두 지웠어요.',
    FAILED: '인덱싱에 실패했어요. 다시 시도해 주세요.',
    NONE: '코드 인덱싱이 되어 있지 않아요. AI 리뷰가 관련 코드를 참고하려면 인덱싱이 필요해요.',
  }[status];
  if (!message) return null;

  return (
    <div className={s.indexingBar}>
      <div className={s.indexingRow}>
        <div className={`${s.indexingText} ${s.indexingTextOnly}`}>
          {status === 'FAILED' && <AlertTriangle size={14} />}
          <span>{message}</span>
        </div>
        <button type="button" className={`dc-btn dc-btn-primary ${s.indexingBtn}`} onClick={onRestart}>
          <RotateCcw size={13} /> {status === 'NONE' ? '인덱싱 시작' : '다시 시작'}
        </button>
      </div>
    </div>
  );
};

export default IndexingBar;
