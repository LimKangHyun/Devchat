import React, { useState } from 'react';
import axiosInstance from "../api/axiosInstance"
import { X, Link, AlertTriangle, Loader2 } from 'lucide-react';
import s from './Modals.module.css';
import useBackClose from '../../hooks/useBackClose';

const JoinRoomModal = ({ onClose, onSubmit }) => {
  useBackClose(true, onClose);
  const [inviteCode, setInviteCode] = useState('');
  const [roomInfo, setRoomInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // 초대 코드로 방 정보 가져오기
  const checkRoomInfo = async (e) => {
    e.preventDefault();
    
    if (!inviteCode.trim()) {
      setError('초대 코드를 입력해주세요.');
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      const res = await axiosInstance.get(`/chat-rooms/info/${inviteCode}`);
      setRoomInfo(res.data);
    } catch (err) {
      console.error('초대 코드 확인 오류:', err);
      const message =
        err.response?.data?.message || // 백엔드에서 내려준 에러 메시지
        err.message ||                // 일반 JS 에러 메시지
        '방 정보를 가져올 수 없습니다. 초대 코드를 확인해주세요.'; // 기본 메시지
      setError(message);
    } finally {
      setLoading(false);
    }
  }

  // 최종 참여 확인
  const confirmJoin = async() => {
    if (inviteCode.trim()) {
      setLoading(true);
      try {
        await onSubmit(inviteCode.trim());
      } catch (error) {
        console.error("참여 실패:", error);
        onClose(); // 실패 시 모달 닫기
      } finally {
        setLoading(false);
      }
    }
  };

  // 이전 단계로 돌아가기
  const goBack = () => {
    setRoomInfo(null);
  };

  return (
    <div className="dc-modal-backdrop" onClick={onClose}>
      <div className="dc-modal" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="dc-modal-header">
          <h3 className="dc-modal-title">
            {roomInfo ? '채팅방 입장 확인' : '채팅방 참여하기'}
          </h3>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기">
            <X size={20} />
          </button>
        </div>

        {/* 방 정보 있을 때는 확인 화면 표시 */}
        {roomInfo ? (
          <>
            <div className="dc-modal-body">
              <p className={s.lead}>이 채팅방에 입장할까요?</p>
              <dl className={s.summary}>
                <div className={s.summaryRow}>
                  <dt>방 이름</dt>
                  <dd>{roomInfo.roomName || '이름 없는 채팅방'}</dd>
                </div>
                <div className={s.summaryRow}>
                  <dt>초대 코드</dt>
                  <dd className={s.mono}>{inviteCode}</dd>
                </div>
                {roomInfo.participantCount !== undefined && (
                  <div className={s.summaryRow}>
                    <dt>참여자 수</dt>
                    <dd>{roomInfo.participantCount}명</dd>
                  </div>
                )}
              </dl>
            </div>
            <div className="dc-modal-footer">
              <button type="button" className="dc-btn" onClick={goBack}>
                이전으로
              </button>
              <button type="button" className="dc-btn dc-btn-primary" onClick={confirmJoin}>
                입장하기
              </button>
            </div>
          </>
        ) : (
          /* 초대 코드 입력 화면 */
          <form onSubmit={checkRoomInfo} className={s.form}>
            <div className="dc-modal-body">
              <div className={s.field}>
                <label htmlFor="inviteCode" className="dc-label">
                  초대 코드 <span className={s.required}>*</span>
                </label>
                <div className={`${s.inputGroup} ${error ? s.inputGroupError : ''}`}>
                  <span className={s.inputAddon}>
                    <Link size={16} />
                  </span>
                  <input
                    id="inviteCode"
                    type="text"
                    value={inviteCode}
                    onChange={(e) => setInviteCode(e.target.value)}
                    placeholder="초대 코드를 입력하세요"
                    required
                  />
                </div>
                {error && (
                  <div className={s.error}>
                    <AlertTriangle size={13} />
                    <span>{error}</span>
                  </div>
                )}
              </div>
            </div>

            <div className="dc-modal-footer">
              <button type="button" className="dc-btn" onClick={onClose}>
                취소
              </button>
              <button type="submit" className="dc-btn dc-btn-primary" disabled={loading}>
                {loading ? (
                  <>
                    <Loader2 size={15} className={s.spin} />
                    확인 중...
                  </>
                ) : (
                  '다음'
                )}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default JoinRoomModal;
