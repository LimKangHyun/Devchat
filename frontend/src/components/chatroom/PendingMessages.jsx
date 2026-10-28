import React from 'react';
import { RotateCw, Trash2 } from 'lucide-react';
import s from './Chat.module.css';

const preview = (body) => {
  if (body.type === 'IMAGE') return '[이미지]';
  return body.content || '';
};

/**
 * 서버가 아직 받았다고 확인하지 않은 내 메시지. 대화 목록 맨 아래에 보여준다.
 * 확인되면 사라지고 진짜 메시지가 목록에 나타난다.
 */
const PendingMessages = ({ items, onRetry, onDiscard }) => {
  if (!items.length) return null;

  return (
    <div className={s.pendingList} aria-live="polite">
      {items.map((it) => {
        const failed = it.status === 'failed';
        return (
          <div key={it.clientMessageId} className={`${s.msgRow} ${s.pendingRow} ${failed ? s.pendingFailed : ''}`}>
            <div className={s.gutter} />
            <div className={s.msgMain}>
              <div className={`${s.pendingText} ${it.body.type === 'CODE' ? s.pendingCode : ''}`}>{preview(it.body)}</div>
              <div className={s.pendingStatus}>
                {failed ? (
                  <>
                    <span>전송 실패{it.error ? ` · ${it.error}` : ''}</span>
                    <button type="button" className={s.pendingAction} onClick={() => onRetry(it.clientMessageId)}>
                      <RotateCw size={12} /> 다시 보내기
                    </button>
                    <button type="button" className={s.pendingAction} onClick={() => onDiscard(it.clientMessageId)}>
                      <Trash2 size={12} /> 삭제
                    </button>
                  </>
                ) : (
                  <span>{it.waiting ? '연결되면 보낼게요…' : '보내는 중…'}</span>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default PendingMessages;
