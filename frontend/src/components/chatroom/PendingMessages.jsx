import React from 'react';
import { RotateCw, Trash2 } from 'lucide-react';
import s from './Chat.module.css';

const preview = (payload = {}) => {
  if (payload.type === 'IMAGE') return '[이미지]';
  return payload.content || '';
};

/**
 * 보내지 못한 내 메시지. 대화 목록 맨 아래에 남겨 다시 보내거나 지울 수 있게 한다.
 * 보내는 중인 메시지는 보여주지 않는다 (서버가 받으면 대화에 바로 나타난다).
 */
const PendingMessages = ({ items, onRetry, onDiscard }) => {
  const failed = items.filter((it) => it.status === 'failed');
  if (!failed.length) return null;

  return (
    <div className={s.pendingList} aria-live="polite">
      {failed.map((it) => (
        <div key={it.clientMessageId} className={`${s.msgRow} ${s.pendingRow} ${s.pendingFailed}`}>
          <div className={s.gutter} />
          <div className={s.msgMain}>
            <div className={`${s.pendingText} ${it.body.payload?.type === 'CODE' ? s.pendingCode : ''}`}>
              {preview(it.body.payload)}
            </div>
            <div className={s.pendingStatus}>
              <span>전송 실패{it.error ? ` · ${it.error}` : ''}</span>
              <button type="button" className={s.pendingAction} onClick={() => onRetry(it.clientMessageId)}>
                <RotateCw size={12} /> 다시 보내기
              </button>
              <button type="button" className={s.pendingAction} onClick={() => onDiscard(it.clientMessageId)}>
                <Trash2 size={12} /> 삭제
              </button>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
};

export default PendingMessages;
