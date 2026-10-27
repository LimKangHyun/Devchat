import React, { useEffect, useState } from 'react';
import {
  X,
  Github,
  User,
  Crown,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  Loader2
} from 'lucide-react';
import axiosInstance from "../api/axiosInstance"
import s from './Modals.module.css';

const GitHubRepoDisplay = ({ repositoryUrl }) => {
  const [expanded, setExpanded] = useState(false);

  const parseRepo = (url) => {
    try {
      const parts = new URL(url).pathname.split('/').filter(Boolean);
      return { owner: parts[0], repo: parts[1] };
    } catch {
      return null;
    }
  };

  const parsed = parseRepo(repositoryUrl);

  return (
    <div className={s.repoCard}>
      <a
        href={repositoryUrl}
        target="_blank"
        rel="noopener noreferrer"
        className={s.repoLink}
      >
        <Github size={20} className={s.repoIcon} />
        <div className={s.repoText}>
          <div className={s.repoName}>
            <span className={s.repoOwner}>{parsed?.owner}/</span>
            <strong>{parsed?.repo}</strong>
          </div>
          <div className={s.repoHost}>github.com</div>
        </div>
        <ExternalLink size={14} className={s.repoExt} />
      </a>

      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className={s.repoToggle}
      >
        <span>전체 URL 보기</span>
        {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
      </button>

      {expanded && (
        <div className={s.repoFull}>
          {repositoryUrl}
        </div>
      )}
    </div>
  );
};

const IndexingStatusBadge = ({ status }) => {
  if (!status || status === 'NONE') return null;

  const config = {
    RUNNING: { label: '인덱싱 중', dot: '#4bb4ef', animate: true },
    COMPLETED: { label: '인덱싱 완료', dot: 'var(--online)', animate: false },
    FAILED: { label: '인덱싱 실패', dot: 'var(--danger)', animate: false },
  };

  const c = config[status];
  if (!c) return null;

  return (
    <div className={s.statusRow}>
      <span
        className={`${s.statusDot} ${c.animate ? s.pulse : ''}`}
        style={{ backgroundColor: c.dot }}
      />
      {c.label}
    </div>
  );
};

const RoomInfoModal = ({ room, sidebarRef, onClose, showToast, indexingStatus }) => {
  const [participants, setParticipants] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetchParticipants = async () => {
    setLoading(true);
    try {
      const response = await axiosInstance.get(`/chat-rooms/${room.roomId}/participants`);
      setParticipants(response.data);
    } catch (err) {
      console.error('참가자 목록 가져오기 실패:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (room?.roomId) {
      fetchParticipants();
    }
  }, [room?.roomId]);

  const ProfileImage = ({ participant, isOwner }) => {
    const [imageError, setImageError] = useState(false);

    return (
      <div className={s.avatar}>
        {participant.profileImageUrl && !imageError ? (
          <img
            src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${participant.profileImageUrl}`}
            alt={participant.nickname}
            onError={() => setImageError(true)}
          />
        ) : (
          <User size={18} />
        )}
      </div>
    );
  };

  const owners = participants ? participants.filter(p => p.owner) : [];
  const members = participants ? participants.filter(p => !p.owner) : [];

  return (
    <div className="dc-modal-backdrop" onClick={onClose}>
      <div className="dc-modal" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
        <div className="dc-modal-header" style={{ alignItems: 'flex-start' }}>
          <div style={{ minWidth: 0 }}>
            <h3 className="dc-modal-title" style={{ wordBreak: 'break-all' }}>
              # {room.name || room.roomName || `Room ${room.uniqueId}`}
            </h3>
            <div className={s.subtitle}>채팅방 정보</div>
          </div>
          <button type="button" className="dc-icon-btn" onClick={onClose} aria-label="닫기">
            <X size={20} />
          </button>
        </div>

        <div className="dc-modal-body dc-scroll">
          {room.repositoryUrl && (
            <div className={s.section}>
              <div className={s.sectionTitle}>연결된 레포지토리</div>
              <GitHubRepoDisplay repositoryUrl={room.repositoryUrl} />
              <IndexingStatusBadge status={indexingStatus} />
            </div>
          )}

          <div className={s.section}>
            <div className={s.sectionTitle}>
              <span>멤버</span>
              {participants && (
                <span className={s.pill}>{participants.length}명</span>
              )}
            </div>

            {loading ? (
              <div className={s.loading}>
                <Loader2 size={20} className={s.spin} />
                <span>로딩 중...</span>
              </div>
            ) : participants && participants.length > 0 ? (
              <ul className={s.memberList}>
                {owners.map((p, idx) => (
                  <li key={`owner-${idx}`} className={s.memberRow}>
                    <ProfileImage participant={p} isOwner={true} />
                    <span className={s.memberName}>{p.nickname || '알 수 없음'}</span>
                    <span className={s.pill}>
                      <Crown size={11} />
                      방장
                    </span>
                  </li>
                ))}

                {members.map((p, idx) => (
                  <li key={`member-${idx}`} className={s.memberRow}>
                    <ProfileImage participant={p} isOwner={false} />
                    <span className={s.memberName}>{p.nickname || '알 수 없음'}</span>
                    <span className={s.pillMuted}>멤버</span>
                  </li>
                ))}
              </ul>
            ) : (
              <div className={s.empty}>
                <User size={22} />
                <div>멤버 정보를 불러올 수 없습니다</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default RoomInfoModal;
