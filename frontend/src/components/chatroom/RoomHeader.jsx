import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, BellOff, Search, MoreVertical, Copy, LogOut, Trash2, GitBranch, X, Loader2, ChevronLeft, Bot, FileText, Check, Mail, Link2, ScrollText } from 'lucide-react';
import useIsMobile from '../../hooks/useIsMobile';
import s from './Chat.module.css';
import InviteEmailModal from '../modals/InviteEmailModal';
import ReviewConventionsModal from '../modals/ReviewConventionsModal';

const getRepoLabel = (url) => {
  if (!url) return null;
  const match = url.match(/github\.com\/([^/]+\/[^/?#]+)/);
  return match ? match[1].replace(/\.git$/, '') : url;
};

const ConfirmDialog = ({ title, desc, confirmLabel, onCancel, onConfirm, loading }) => (
  <div className="dc-modal-backdrop" onClick={loading ? undefined : onCancel}>
    <div className="dc-modal" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
      <div className="dc-modal-header">
        <h2 className="dc-modal-title">{title}</h2>
        <button type="button" className="dc-icon-btn" onClick={onCancel} disabled={loading} aria-label="닫기">
          <X size={20} />
        </button>
      </div>
      <div className="dc-modal-body" style={{ color: 'var(--text-2)' }}>{desc}</div>
      <div className="dc-modal-footer">
        <button type="button" className="dc-btn" onClick={onCancel} disabled={loading}>취소</button>
        <button type="button" className="dc-btn dc-btn-danger" onClick={onConfirm} disabled={loading}>
          {loading && <Loader2 size={14} style={{ animation: 'dc-spin 1s linear infinite' }} />}
          {loading ? '처리 중...' : confirmLabel}
        </button>
      </div>
    </div>
  </div>
);

const Toggle = ({ on }) => (
  <span className={`${s.miniToggle} ${on ? s.miniToggleOn : ''}`}><span /></span>
);

const RoomHeader = ({
  roomId, roomName, inviteCode, onSearch, onLeaveRoom, onDeleteRoom,
  isOwner, toggleAlarm, alarmEnabled,
  aiReviewEnabled, onToggleAiReview,
  aiSummaryEnabled, onToggleAiSummary,
  repositoryUrl, onConnectRepo
}) => {
  const navigate = useNavigate();
  const isMobile = useIsMobile();
  const [showNotification, setShowNotification] = useState(false);
  const [notificationText, setNotificationText] = useState('');
  const [showInviteEmail, setShowInviteEmail] = useState(false);
  const [showConventions, setShowConventions] = useState(false);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [showLeaveSuccess, setShowLeaveSuccess] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const menuRef = useRef(null);
  const searchInputRef = useRef(null);

  const repoLabel = getRepoLabel(repositoryUrl);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false);
    };
    if (menuOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuOpen]);

  useEffect(() => {
    if (searchOpen) searchInputRef.current?.focus();
  }, [searchOpen]);

  const handleLeave = async () => {
    const result = await onLeaveRoom();
    if (result.success) {
      setShowLeaveConfirm(false);
      setShowLeaveSuccess(true);
      setTimeout(() => { setShowLeaveSuccess(false); navigate('/'); }, 500);
    } else {
      alert(result.error);
    }
  };

  const handleDelete = async () => {
    setDeleteLoading(true);
    const result = await onDeleteRoom();
    if (!result.success) alert(result.error);
    setShowDeleteConfirm(false);
    setDeleteLoading(false);
  };

  const copyText = async (text, done) => {
    try {
      await navigator.clipboard.writeText(text);
      setNotificationText(done);
      setShowNotification(true);
      setTimeout(() => setShowNotification(false), 2000);
    } catch {
      alert('복사 중 오류가 발생했습니다.');
    }
  };

  const handleCopy = () => copyText(inviteCode, '초대 코드가 복사되었습니다.');

  // 메일로 보내는 것과 같은 입장 링크. 메신저 등으로 직접 공유할 때 쓴다.
  const handleCopyLink = () =>
    copyText(`${window.location.origin}/invite/${encodeURIComponent(inviteCode)}`, '입장 링크가 복사되었습니다.');

  const closeSearch = () => { setSearchOpen(false); setSearchValue(''); };

  const AiToggle = ({ icon, label, enabled, onToggle }) => (
    <button
      type="button"
      className={`${s.aiToggle} ${enabled ? s.aiToggleOn : ''}`}
      onClick={isOwner ? onToggle : undefined}
      title={isOwner ? `${label} ${enabled ? '끄기' : '켜기'}` : '방장만 변경할 수 있어요'}
      style={{ cursor: isOwner ? 'pointer' : 'default' }}
    >
      {icon}
      <span>{label}</span>
      <Toggle on={enabled} />
    </button>
  );

  const menuItemAi = (label, enabled, onToggle) => (
    <button
      type="button"
      className={s.menuItem}
      onClick={isOwner ? onToggle : undefined}
      disabled={!isOwner}
      title={isOwner ? undefined : '방장만 변경할 수 있어요'}
    >
      <Bot size={15} />
      <span style={{ flex: 1 }}>{label}</span>
      <Toggle on={enabled} />
    </button>
  );

  return (
    <div className={s.roomHeader}>
      {searchOpen && isMobile ? (
        <div className={s.mobileSearchBar}>
          <Search size={16} className={s.searchFieldIcon} />
          <input
            ref={searchInputRef}
            type="search"
            value={searchValue}
            placeholder={`#${roomName}에서 검색`}
            onChange={(e) => setSearchValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && onSearch && searchValue.trim()) onSearch(searchValue.trim());
              if (e.key === 'Escape') closeSearch();
            }}
            className={s.searchField}
          />
          <button type="button" className="dc-btn dc-btn-ghost" onClick={closeSearch}>취소</button>
        </div>
      ) : (
        <>
          <div className={s.roomHeaderLeft}>
            {isMobile && (
              <button type="button" className={`dc-icon-btn ${s.backBtn}`} onClick={() => navigate('/')} aria-label="채팅방 목록으로">
                <ChevronLeft size={24} />
              </button>
            )}
            <div className={s.roomTitleWrap}>
              <button type="button" className={s.roomTitle} onClick={handleCopy} title="초대 코드 복사">
                <span className={s.roomTitleText}>{roomName}</span>
              </button>
              {repoLabel && (
                <a href={repositoryUrl} target="_blank" rel="noopener noreferrer" className={s.repoLink} title={repositoryUrl}>
                  <GitBranch size={12} />
                  <span>{repoLabel}</span>
                </a>
              )}
            </div>
          </div>

          <div className={s.roomHeaderRight}>
            {repositoryUrl && !isMobile && (
              <div className={s.aiToggles}>
                <AiToggle icon={<Bot size={14} />} label="AI 리뷰" enabled={aiReviewEnabled} onToggle={onToggleAiReview} />
                <AiToggle icon={<FileText size={14} />} label="AI 요약" enabled={aiSummaryEnabled} onToggle={onToggleAiSummary} />
              </div>
            )}

            {!repositoryUrl && isOwner && !isMobile && onConnectRepo && (
              <button type="button" className={s.connectRepoBtn} onClick={onConnectRepo} title="GitHub 레포지토리 연결">
                <GitBranch size={14} />
                <span>레포 연결</span>
              </button>
            )}

            {searchOpen ? (
              <div className={s.searchFieldWrap}>
                <Search size={14} className={s.searchFieldIcon} />
                <input
                  ref={searchInputRef}
                  type="text"
                  value={searchValue}
                  placeholder={`#${roomName}에서 검색`}
                  onChange={(e) => setSearchValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && onSearch && searchValue.trim()) onSearch(searchValue.trim());
                    if (e.key === 'Escape') closeSearch();
                  }}
                  className={s.searchField}
                />
                <button type="button" onClick={closeSearch} className={s.searchFieldClear} aria-label="검색 닫기">
                  <X size={13} />
                </button>
              </div>
            ) : (
              <button type="button" title="메시지 검색" className="dc-icon-btn" onClick={() => setSearchOpen(true)} aria-label="메시지 검색">
                <Search size={18} />
              </button>
            )}

            {!isMobile && (
              <button
                type="button"
                onClick={toggleAlarm}
                title={alarmEnabled ? '알림 끄기' : '알림 켜기'}
                aria-label={alarmEnabled ? '알림 끄기' : '알림 켜기'}
                className="dc-icon-btn"
              >
                {alarmEnabled ? <Bell size={18} /> : <BellOff size={18} />}
              </button>
            )}

            <div ref={menuRef} style={{ position: 'relative' }}>
              <button
                type="button"
                onClick={() => setMenuOpen(prev => !prev)}
                className={`dc-icon-btn ${menuOpen ? 'is-active' : ''}`}
                aria-label="채널 메뉴"
              >
                <MoreVertical size={18} />
              </button>

              {menuOpen && (
                <>
                  {isMobile && <div className={s.sheetBackdrop} onClick={() => setMenuOpen(false)} />}
                  <div className={s.menu}>
                    {isMobile && <div className={s.menuTitle}>{roomName}</div>}
                    {roomId && (
                      <button type="button" className={s.menuItem} onClick={() => { setShowInviteEmail(true); setMenuOpen(false); }}>
                        <Mail size={15} />이메일로 초대
                      </button>
                    )}
                    <button type="button" className={s.menuItem} onClick={() => { handleCopyLink(); setMenuOpen(false); }}>
                      <Link2 size={15} />입장 링크 복사
                    </button>
                    <button type="button" className={s.menuItem} onClick={() => { handleCopy(); setMenuOpen(false); }}>
                      <Copy size={15} />초대 코드 복사
                    </button>
                    {isMobile && (
                      <button type="button" className={s.menuItem} onClick={toggleAlarm}>
                        {alarmEnabled ? <Bell size={15} /> : <BellOff size={15} />}
                        <span style={{ flex: 1 }}>알림</span>
                        <Toggle on={alarmEnabled} />
                      </button>
                    )}
                    {isMobile && !repositoryUrl && isOwner && onConnectRepo && (
                      <button type="button" className={s.menuItem} onClick={() => { onConnectRepo(); setMenuOpen(false); }}>
                        <GitBranch size={15} />레포지토리 연결
                      </button>
                    )}
                    {isMobile && repositoryUrl && (
                      <>
                        {menuItemAi('AI 리뷰', aiReviewEnabled, onToggleAiReview)}
                        {menuItemAi('AI 요약', aiSummaryEnabled, onToggleAiSummary)}
                      </>
                    )}
                    {repositoryUrl && roomId && (
                      <button type="button" className={s.menuItem} onClick={() => { setShowConventions(true); setMenuOpen(false); }}>
                        <ScrollText size={15} />팀 리뷰 규칙
                      </button>
                    )}
                    <div className={s.menuDivider} />
                    <button type="button" className={`${s.menuItem} ${s.menuItemDanger}`} onClick={() => { setShowLeaveConfirm(true); setMenuOpen(false); }}>
                      <LogOut size={15} />채팅방 나가기
                    </button>
                    {isOwner && (
                      <button type="button" className={`${s.menuItem} ${s.menuItemDanger}`} onClick={() => { setShowDeleteConfirm(true); setMenuOpen(false); }}>
                        <Trash2 size={15} />채팅방 삭제
                      </button>
                    )}
                  </div>
                </>
              )}
            </div>
          </div>
        </>
      )}

      {showNotification && (
        <div className={s.snackbar}><Check size={14} /> {notificationText}</div>
      )}

      {showInviteEmail && (
        <InviteEmailModal roomId={roomId} roomName={roomName} onClose={() => setShowInviteEmail(false)} />
      )}

      {showConventions && (
        <ReviewConventionsModal roomId={roomId} isOwner={isOwner} onClose={() => setShowConventions(false)} />
      )}

      {showLeaveConfirm && (
        <ConfirmDialog
          title="채팅방을 나갈까요?"
          desc="나가더라도 초대 코드로 다시 입장할 수 있어요."
          confirmLabel="나가기"
          onCancel={() => setShowLeaveConfirm(false)}
          onConfirm={handleLeave}
        />
      )}

      {showDeleteConfirm && (
        <ConfirmDialog
          title="채팅방을 삭제할까요?"
          desc="삭제된 채팅방과 모든 메시지는 복구할 수 없어요."
          confirmLabel="삭제"
          loading={deleteLoading}
          onCancel={() => setShowDeleteConfirm(false)}
          onConfirm={handleDelete}
        />
      )}

      {showLeaveSuccess && (
        <div className={s.snackbar}>채팅방에서 나갔습니다.</div>
      )}
    </div>
  );
};

export default RoomHeader;
