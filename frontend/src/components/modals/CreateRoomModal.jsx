import React, { useState } from 'react';
import { X, Info, Github, Loader2 } from 'lucide-react';
import s from './Modals.module.css';
import useBackClose from '../../hooks/useBackClose';

const CreateRoomModal = ({ onClose, onSubmit }) => {
  useBackClose(true, onClose);
  const [roomName, setRoomName] = useState('');
  const [repoUrl, setRepoUrl] = useState('');
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [loading, setLoading] = useState(false);

  // 방 정보 입력 후 확인 단계로 이동
  const handleNext = (e) => {
    e.preventDefault();

    if (!roomName.trim()) {
      alert('방 이름을 입력해주세요.');
      return;
    }

    setShowConfirmation(true);
  };

  const confirmCreate = async () => {
    if (roomName.trim()) {
      setLoading(true);
      try {
        await onSubmit(roomName.trim(), repoUrl.trim()); // 실패 시 catch로 넘어감
      } catch (error) {
        console.error("방 생성 실패:", error);
        onClose(); // 실패했을 때 모달 닫기
      } finally {
        setLoading(false); // 항상 로딩 상태 해제
      }
    }
  };

  // 이전 단계로 돌아가기
  const goBack = () => {
    setShowConfirmation(false);
  };

  return (
    <div className="dc-modal-backdrop" onClick={onClose}>
      <div className="dc-modal" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="dc-modal-header">
          <h3 className="dc-modal-title">
            {showConfirmation ? '채팅방 생성 확인' : '채팅방 만들기'}
          </h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기">
            <X size={20} />
          </button>
        </div>

        {/* 방 정보 확인 화면 */}
        {showConfirmation ? (
          <>
            <div className="dc-modal-body">
              <p className={s.lead}>아래 정보로 채팅방을 만들까요?</p>
              <dl className={s.summary}>
                <div className={s.summaryRow}>
                  <dt>방 이름</dt>
                  <dd>{roomName}</dd>
                </div>
                {repoUrl && (
                  <div className={s.summaryRow}>
                    <dt>GitHub 레포지토리</dt>
                    <dd className={s.mono}>{repoUrl}</dd>
                  </div>
                )}
              </dl>
            </div>
            <div className="dc-modal-footer">
              <button type="button" className="dc-btn" onClick={goBack} disabled={loading}>
                이전으로
              </button>
              <button
                type="button"
                className="dc-btn dc-btn-primary"
                onClick={confirmCreate}
                disabled={loading}
              >
                {loading ? (
                  <>
                    <Loader2 size={15} className={s.spin} />
                    생성 중...
                  </>
                ) : (
                  '생성하기'
                )}
              </button>
            </div>
          </>
        ) : (
          /* 방 정보 입력 화면 */
          <form onSubmit={handleNext} className={s.form}>
            <div className="dc-modal-body">
              <div className={s.field}>
                <label htmlFor="roomName" className="dc-label">
                  채팅방 이름 <span className={s.required}>*</span>
                </label>
                <input
                  id="roomName"
                  type="text"
                  className="dc-input"
                  value={roomName}
                  onChange={(e) => setRoomName(e.target.value)}
                  placeholder="예: backend-team"
                  required
                />
              </div>

              <div className={s.field}>
                <label htmlFor="repoUrl" className="dc-label">
                  GitHub 레포지토리 URL <span className={s.optional}>(선택)</span>
                </label>
                <div className={s.inputGroup}>
                  <span className={s.inputAddon}>
                    <Github size={16} />
                  </span>
                  <input
                    id="repoUrl"
                    type="text"
                    value={repoUrl}
                    onChange={(e) => setRepoUrl(e.target.value)}
                    placeholder="https://github.com/username/repo"
                  />
                </div>
                <div className={`dc-help ${s.helpRow}`}>
                  <Info size={13} />
                  <span>코드 질문/답변에 사용할 GitHub 저장소 URL을 입력하세요.</span>
                </div>
              </div>
            </div>

            <div className="dc-modal-footer">
              <button type="button" className="dc-btn" onClick={onClose}>
                취소
              </button>
              <button type="submit" className="dc-btn dc-btn-primary">
                다음
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default CreateRoomModal;
