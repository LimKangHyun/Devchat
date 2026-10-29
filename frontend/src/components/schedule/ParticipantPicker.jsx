import React, { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Search, X } from 'lucide-react';
import axiosInstance from '../api/axiosInstance';
import { useUser } from '../../context/UserContext';
import { useAlarm } from '../../context/AlarmContext';
import Avatar from './Avatar';
import s from './Schedule.module.css';
import { MAX_PARTICIPANTS, PARTICIPANT_LIMIT_MESSAGE, addParticipant } from '../../utils/scheduleForm';

const FRIEND_PAGE_SIZE = 100;

// 친구 + 지금 열려 있는 채팅방 참여자 중에서 고른다. value: [{ id, nickname, profileImg }]
const ParticipantPicker = ({ value, onChange, disabled }) => {
  const { currentUser } = useUser();
  const { currentRoomId } = useAlarm();
  const { pathname } = useLocation();
  // 채팅방 화면일 때만 그 방을 "열려 있는 방"으로 본다
  const roomId = pathname.startsWith('/chat/') ? currentRoomId : null;

  const [friends, setFriends] = useState([]);
  const [roomMembers, setRoomMembers] = useState([]);
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [friendsFailed, setFriendsFailed] = useState(false);
  const [roomFailed, setRoomFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    axiosInstance.get(`/friend?page=0&size=${FRIEND_PAGE_SIZE}`)
      .then((res) => {
        if (!alive) return;
        const list = (res.data?.content || [])
          .map((f) => ({ id: f.id ?? f.memberId, nickname: f.nickname || f.username, username: f.username, profileImg: f.profileImage }))
          // 친구 목록에 회원 번호가 없으면 고를 수 없다
          .filter((f) => f.id != null);
        setFriends(list);
      })
      .catch(() => { if (alive) setFriendsFailed(true); });
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    setRoomFailed(false);
    if (!roomId) { setRoomMembers([]); return undefined; }
    let alive = true;
    axiosInstance.get(`/chat-rooms/${roomId}/participants`)
      .then((res) => {
        if (!alive) return;
        setRoomMembers((res.data || []).map((p) => ({ id: p.memberId, nickname: p.nickname, profileImg: p.profileImageUrl })));
      })
      .catch(() => { if (alive) setRoomFailed(true); });
    return () => { alive = false; };
  }, [roomId]);

  // 중복 없이 합치고 나는 뺀다
  const candidates = useMemo(() => {
    const seen = new Set();
    const result = [];
    [...roomMembers, ...friends].forEach((p) => {
      if (p.id == null || seen.has(p.id) || p.id === currentUser?.id) return;
      seen.add(p.id);
      result.push(p);
    });
    return result;
  }, [friends, roomMembers, currentUser?.id]);

  const selectedIds = new Set(value.map((p) => p.id));
  const q = query.trim().toLowerCase();
  const options = candidates
    .filter((p) => !selectedIds.has(p.id))
    .filter((p) => !q || (p.nickname || '').toLowerCase().includes(q) || (p.username || '').toLowerCase().includes(q))
    .slice(0, 30);

  // 50명이 차면 더 못 넣는다
  const full = value.length >= MAX_PARTICIPANTS;
  const add = (p) => {
    if (full) return;
    onChange(addParticipant(value, { id: p.id, nickname: p.nickname, profileImg: p.profileImg }));
    setQuery('');
  };
  const remove = (id) => onChange(value.filter((p) => p.id !== id));

  return (
    <div className={s.picker}>
      {value.length > 0 && (
        <div className={s.chipRow}>
          {value.map((p) => (
            <span key={p.id} className={s.personChip}>
              <Avatar name={p.nickname} img={p.profileImg} size={20} />
              <span>{p.nickname}</span>
              <button type="button" onClick={() => remove(p.id)} aria-label={`${p.nickname} 빼기`} disabled={disabled}>
                <X size={12} />
              </button>
            </span>
          ))}
        </div>
      )}
      <div className={s.pickerSearch}>
        <Search size={14} />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          placeholder="친구나 이 방 사람 찾기"
          disabled={disabled}
          aria-label="참여자 찾기"
        />
      </div>
      {(friendsFailed || roomFailed) && candidates.length > 0 && (
        <p className={s.pickerError} role="alert">목록을 불러오지 못했어요. 일부 사람만 보일 수 있어요.</p>
      )}
      {full && <p className={s.pickerError} role="status">{PARTICIPANT_LIMIT_MESSAGE}</p>}
      {open && !full && (
        <ul className={s.pickerList} role="listbox">
          {options.length === 0 ? (
            <li className={s.pickerEmpty}>
              {candidates.length > 0 ? '찾는 사람이 없어요'
                : friendsFailed || roomFailed ? '목록을 불러오지 못했어요' : '고를 수 있는 사람이 없어요'}
            </li>
          ) : options.map((p) => (
            <li key={p.id}>
              <button type="button" className={s.pickerOption} onMouseDown={(e) => e.preventDefault()} onClick={() => add(p)}>
                <Avatar name={p.nickname} img={p.profileImg} size={24} />
                <span>{p.nickname}</span>
                {p.username && <span className={s.pickerSub}>@{p.username}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default ParticipantPicker;
