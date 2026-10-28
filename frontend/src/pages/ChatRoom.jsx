import React, { useEffect, useState, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Virtuoso } from 'react-virtuoso';
import SearchSidebar from '../components/SearchSideBar';
import axiosInstance from '../components/api/axiosInstance';
import MessageInput from '../components/chatroom/MessageInput';
import { MessageItem, DateDivider } from '../components/chatroom/MessageList';
import useWebSocket from '../components/common/useWebSocket';
import RoomHeader from '../components/chatroom/RoomHeader';
import RoomDeletedModal from '../components/modals/RoomDeletedModal';
import CodeReviewModal from '../components/modals/CodeReviewModal.jsx';
import { useAlarm } from '../context/AlarmContext';
import { useUser } from '../context/UserContext';
import Toast from '../components/common/Toast';
import AiReviewModal from '../components/modals/AiReviewModal';
import ConnectRepoModal from '../components/modals/ConnectRepoModal';
import IndexingBar from '../components/chatroom/IndexingBar';
import s from '../components/chatroom/Chat.module.css';
import { MessageSquareDashed, Code2, GitPullRequest, UserPlus, Copy } from 'lucide-react';
import { findMentionedIds } from '../utils/mention';
import { uploadErrorMessage } from '../utils/imageCompress';
import { isTooLong, TOO_LONG_MESSAGE } from '../utils/messageLimits';
import { useWebSocketContext } from '../components/common/WebSocketContext';
import useOutbox from '../components/chatroom/useOutbox';
import PendingMessages from '../components/chatroom/PendingMessages';
import { CATCH_UP_PAGE, fetchMissedMessages, maxMessageId, mergeMessages } from '../components/chatroom/catchUp';

// 레포 연동 방에 자동으로 들어오는 봇 계정 (서버 github.username, github.bot.username)
const BOT_NICKNAMES = new Set(['GitHubBot', 'Devchat-Code-Assist']);

const PAGE_SIZE = 30;
const INDEX_OFFSET = 100000;

const preloadedCache = new Set();

const formatDate = (dateString) => {
  const date = new Date(dateString);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === today.toDateString()) return '오늘';
  if (date.toDateString() === yesterday.toDateString()) return '어제';
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
};

const preloadImages = (urls) => {
  urls.forEach(url => {
    if (!url) return;
    if (preloadedCache.has(url)) return;
    preloadedCache.add(url);
    const img = new Image();
    img.src = `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${url}`;
  });
};

// prNumber가 있는 GIT 메시지들을 묶어서
// 첫 번째 메시지만 남기고 나머지는 subMessages로 붙임
const groupGitMessages = (messages) => {
  const prFirstMessageId = new Map(); // prNumber -> 첫 번째 messageId
  const subMessagesMap = new Map();   // messageId -> subMessages[]
  const hiddenIds = new Set();        // 숨길 messageId

  messages.forEach((msg) => {
    if (msg.type !== 'GIT' || msg.prNumber == null) return;

    const existingId = prFirstMessageId.get(msg.prNumber);
    if (existingId == null) {
      prFirstMessageId.set(msg.prNumber, msg.messageId);
      subMessagesMap.set(msg.messageId, []);
    } else {
      subMessagesMap.get(existingId).push(msg);
      hiddenIds.add(msg.messageId);
    }
  });

  return { subMessagesMap, hiddenIds };
};

const VirtuosoMessageItem = React.memo(({
  msg, prevMsg, nextMsg,
  currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent,
  handleDeleteMessage, editMessageId, editContent,
  handleEditMessage, onCodeClick, onRetryClick,
  subMessages,
}) => {
  const msgDate = formatDate(msg.createdAt || msg.joinAt);
  const prevDate = prevMsg ? formatDate(prevMsg.createdAt || prevMsg.joinAt) : null;

  return (
    <div>
      {msgDate !== prevDate && <DateDivider label={msgDate} />}
      <MessageItem
        msg={msg}
        prevMsg={prevMsg}
        nextMsg={nextMsg}
        currentUser={currentUser}
        contextMenuId={contextMenuId}
        setContextMenuId={setContextMenuId}
        setEditMessageId={setEditMessageId}
        setEditContent={setEditContent}
        handleDeleteMessage={handleDeleteMessage}
        editMessageId={editMessageId}
        editContent={editContent}
        handleEditMessage={handleEditMessage}
        onCodeClick={onCodeClick}
        onRetryClick={onRetryClick}
        subMessages={subMessages}
      />
    </div>
  );
});

const ChatRoom = () => {
  const { inviteCode } = useParams();
  const { currentUser } = useUser();
  const { getAlarmStatus, updateAlarm, enterRoom, clearUnread } = useAlarm();

  const [messages, setMessages] = useState([]);
  const [content, setContent] = useState('');
  const [inputMode, setInputMode] = useState('TEXT');
  const [language, setLanguage] = useState('java');
  const [contextMenuId, setContextMenuId] = useState(null);
  const [editMessageId, setEditMessageId] = useState(null);
  const [editContent, setEditContent] = useState('');
  const [cursor, setCursor] = useState(null);
  const [hasMoreMessages, setHasMoreMessages] = useState(true);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [isInitialLoad, setIsInitialLoad] = useState(true);
  const [firstItemIndex, setFirstItemIndex] = useState(INDEX_OFFSET);
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [newMessageCount, setNewMessageCount] = useState(0);
  const [inputHeight, setInputHeight] = useState(0);
  const [toast, setToast] = useState({ open: false, message: '' });
  const [aiReviewEnabled, setAiReviewEnabled] = useState(true);
  const [aiSummaryEnabled, setAiSummaryEnabled] = useState(true);
  const [showConnectRepo, setShowConnectRepo] = useState(false);

  const virtuosoRef = useRef(null);
  const inputAreaRef = useRef(null);
  const navigate = useNavigate();
  const roomIdRef = useRef(null);
  const prevRoomIdRef = useRef(null);
  const readDoneRef = useRef(false);
  const isLoadingRef = useRef(false);
  const isAtBottomRef = useRef(true);
  const currentUserRef = useRef(null);

  // 서버가 받았다고 확인하지 않은 내 메시지. 확인되면 지우고, 안 되면 "전송 실패"로 보여준다
  const { stompClientRef: sharedClientRef } = useWebSocketContext();
  const outbox = useOutbox({
    publish: ({ roomId: targetRoomId, payload }, clientMessageId) => {
      const client = sharedClientRef.current;
      if (!client?.connected) throw new Error('disconnected');
      client.publish({
        destination: `/chat/send-message/${targetRoomId}`,
        body: JSON.stringify({ ...payload, clientMessageId }),
        headers: { 'client-message-id': clientMessageId },
      });
    },
    isConnected: () => !!sharedClientRef.current?.connected,
  });
  const outboxRef = useRef(outbox);
  outboxRef.current = outbox;

  const [roomName, setRoomName] = useState('로딩 중...');
  const [roomId, setRoomId] = useState(null);
  const [isOwner, setIsOwner] = useState(false);
  const [roomData, setRoomData] = useState(null);
  const [deleteNotification, setDeleteNotification] = useState(null);
  const [showCodeModal, setShowCodeModal] = useState(false);
  const [selectedCodeMessage, setSelectedCodeMessage] = useState(null);
  const [imageFile, setImageFile] = useState(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState(null);
  const [showSearchSidebar, setShowSearchSidebar] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [searchHasNext, setSearchHasNext] = useState(false);
  const [searchTotalCount, setSearchTotalCount] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [indexingStatus, setIndexingStatus] = useState(null);
  const [indexingCancelling, setIndexingCancelling] = useState(false);

  const [initState, setInitState] = useState({
    isRoomValidated: false,
    isMessagesLoaded: false,
    hasError: false,
    errorMessage: '',
  });

  const isFullyLoaded = initState.isRoomValidated && initState.isMessagesLoaded && !!currentUser;

  // GIT 메시지 그룹핑 — messages가 바뀔 때마다 재계산
  // hiddenIds에 속한 메시지는 Virtuoso에 넘기지 않고,
  // 첫 번째 메시지에 subMessages로 붙여서 렌더링
  const { displayMessages, subMessagesMap } = useMemo(() => {
    const { subMessagesMap, hiddenIds } = groupGitMessages(messages);
    const displayMessages = messages.filter((m) => !hiddenIds.has(m.messageId));
    return { displayMessages, subMessagesMap };
  }, [messages]);

  useEffect(() => { currentUserRef.current = currentUser; }, [currentUser]);
  useEffect(() => { roomIdRef.current = roomId; }, [roomId]);

  useEffect(() => {
    if (currentUser?.profileImageUrl) preloadImages([currentUser.profileImageUrl]);
  }, [currentUser]);

  useEffect(() => {
    const el = inputAreaRef.current;
    if (!el) return;
    const observer = new ResizeObserver(() => setInputHeight(el.offsetHeight));
    observer.observe(el);
    setInputHeight(el.offsetHeight);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    return () => {
      if (roomIdRef.current && !readDoneRef.current) {
        axiosInstance.post(`/chat-rooms/${roomIdRef.current}/read`)
          .then(() => window.dispatchEvent(new CustomEvent('room:read')))
          .catch(() => {});
      }
    };
  }, []);

  useEffect(() => {
    if (roomId) {
      enterRoom(roomId)
      clearUnread(roomId)
    }
  }, [roomId, enterRoom, clearUnread])

  const handleCodeClick = (message) => {
    setSelectedCodeMessage(message);
    setShowCodeModal(true);
  };

  const handleRetryAiReview = async (prNumber) => {
    try {
      await axiosInstance.post(`/github/${roomId}/ai-review/retry`, { prNumber });
    } catch (err) {
      console.error('AI 리뷰 재시도 실패:', err);
    }
  };

  // 방에 들어올 때 인덱싱 상태를 맞춘다. 진행 중이면 현재 단계·진행률을 받아 오고, 이후로는 WebSocket 알림으로 갱신한다.
  const loadIndexingStatus = useCallback(async (room) => {
    if (!room?.repositoryUrl) {
      setIndexingStatus(null);
      return;
    }
    if (room.indexingStatus === 'RUNNING') {
      try {
        const res = await axiosInstance.get(`/chat-rooms/${room.roomId}/indexing`);
        setIndexingStatus(res.data);
      } catch {
        setIndexingStatus({ status: 'RUNNING' });
      }
      return;
    }
    // 완료된 방은 표시줄을 띄우지 않는다. NONE·FAILED는 방장에게 다시 시작 버튼을 보여준다.
    setIndexingStatus(room.indexingStatus === 'COMPLETED' ? null : { status: room.indexingStatus });
  }, []);

  const handleCancelIndexing = async () => {
    setIndexingCancelling(true);
    try {
      await axiosInstance.delete(`/chat-rooms/${roomId}/indexing`);
      // 결과는 AI 서버가 멈추고 청크를 지운 뒤 WebSocket(CANCELLED)으로 온다.
    } catch (err) {
      setIndexingCancelling(false);
      setToast({ open: true, message: err.response?.data?.message || '인덱싱을 취소하지 못했어요.' });
      setTimeout(() => setToast({ open: false, message: '' }), 3000);
    }
  };

  const handleRestartIndexing = async () => {
    try {
      const res = await axiosInstance.post(`/chat-rooms/${roomId}/indexing`);
      setIndexingStatus(res.data);
    } catch (err) {
      setToast({ open: true, message: err.response?.data?.message || '인덱싱을 시작하지 못했어요.' });
      setTimeout(() => setToast({ open: false, message: '' }), 3000);
    }
  };

  const fetchRoomInfo = useCallback(async () => {
    try {
      const res = await axiosInstance.get(`/chat-rooms/${inviteCode}`);
      const data = res.data;
      setRoomId(data.roomId);
      setRoomName(data.roomName);
      setRoomData(data);
      setAiReviewEnabled(data.aiReviewEnabled ?? true);
      setAiSummaryEnabled(data.aiSummaryEnabled ?? true);
      updateAlarm(data.roomId, data.alarmEnabled);
      setInitState((prev) => ({ ...prev, isRoomValidated: true }));
      loadIndexingStatus(data);
      return data.roomId;
    } catch {
      setInitState((prev) => ({ ...prev, hasError: true, errorMessage: '방 정보를 불러올 수 없습니다.' }));
      return null;
    }
  }, [inviteCode, updateAlarm, loadIndexingStatus]);

  const fetchMessages = useCallback(async (cursorValue = null, isLoadMore = false) => {
    if (!roomId) return;
    if (isLoadingRef.current) return;
    isLoadingRef.current = true;
    setIsLoadingMessages(true);
    try {
      const params = { size: PAGE_SIZE };
      if (cursorValue) params.cursor = cursorValue;
      const res = await axiosInstance.get(`/${roomId}/messages`, { params });
      const data = res.data;
      const messageList = data.messages || [];
      messageList.forEach((m) => m.clientMessageId && outboxRef.current.resolve(m.clientMessageId));
      const sorted = messageList
        .map((msg) => ({ ...msg, createdAt: msg.createdAt && !isNaN(new Date(msg.createdAt).getTime()) ? msg.createdAt : new Date().toISOString() }))
        .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

      const uniqueProfileUrls = [...new Set(sorted.map(m => m.profileImageUrl).filter(Boolean))];
      preloadImages(uniqueProfileUrls);

      if (isLoadMore) {
        setFirstItemIndex((prev) => prev - sorted.length);
        setMessages((prev) => {
          const ids = new Set(prev.map((m) => m.messageId));
          return [...sorted.filter((m) => !ids.has(m.messageId)), ...prev];
        });
      } else {
        setFirstItemIndex(INDEX_OFFSET);
        setMessages(sorted);
        setInitState((prev) => ({ ...prev, isMessagesLoaded: true }));
      }
      setCursor(data.nextCursor);
      setHasMoreMessages(!!data.nextCursor && messageList.length > 0);
    } catch (e) {
      console.error(e);
    } finally {
      isLoadingRef.current = false;
      setIsLoadingMessages(false);
    }
  }, [roomId]);

  // 다시 연결됐을 때 끊긴 사이 메시지를 채운다 (catchUp.js 참고)
  const messagesRef = useRef(messages);
  useEffect(() => { messagesRef.current = messages; }, [messages]);

  const fetchMissingMessages = useCallback(async () => {
    if (!roomId) return;
    const resolveSent = (list) => list.forEach((m) => m.clientMessageId && outboxRef.current.resolve(m.clientMessageId));
    const lastId = maxMessageId(messagesRef.current);
    try {
      if (lastId == null) { await fetchMessages(null, false); return; }

      const { messages: missed, complete } = await fetchMissedMessages(
        (after) => axiosInstance.get(`/${roomId}/messages/after`, { params: { after, size: CATCH_UP_PAGE } }).then((r) => r.data),
        lastId
      );
      if (!complete) {
        // 너무 많이 놓쳤다: 다 받지 않고 방에 다시 들어온 것처럼 최신 메시지로 새로 그린다
        resolveSent(missed);
        await fetchMessages(null, false);
        return;
      }
      const latest = (await axiosInstance.get(`/${roomId}/messages`)).data.messages || [];
      resolveSent(missed);
      resolveSent(latest);
      setMessages((prev) => mergeMessages(prev, missed, latest));
    } catch (e) {
      console.error('누락 메시지 조회 실패', e);
    }
  }, [roomId, fetchMessages]);

  const handleToggleAiReview = async () => {
    try {
      const res = await axiosInstance.patch(`/chat-rooms/${roomId}/ai-review/toggle`);
      setAiReviewEnabled(res.data.aiReviewEnabled);
    } catch (err) {
      alert('설정 변경에 실패했습니다.');
    }
  };

  const handleToggleAiSummary = async () => {
    try {
      const res = await axiosInstance.patch(`/chat-rooms/${roomId}/ai-summary/toggle`);
      setAiSummaryEnabled(res.data.aiSummaryEnabled);
    } catch (err) {
      alert('설정 변경에 실패했습니다.');
    }
  };

  // 실패하면 에러를 던져서 모달이 메시지를 보여주게 한다
  const handleConnectRepo = async (repositoryUrl) => {
    const res = await axiosInstance.patch(`/chat-rooms/${roomId}/repository`, { repositoryUrl });
    setRoomData(prev => ({ ...prev, repositoryUrl: res.data.repositoryUrl }));
    setAiReviewEnabled(true);
    setAiSummaryEnabled(true);
    setIndexingStatus({ status: 'RUNNING', phase: 'CLONING', done: 0, total: 0 });
    setShowConnectRepo(false);
    setToast({ open: true, message: '레포지토리를 연결했어요. 코드 인덱싱을 시작합니다.' });
    setTimeout(() => setToast({ open: false, message: '' }), 3000);
  };

  const toggleAlarm = async () => {
    try {
      const res = await axiosInstance.post(`/chat-rooms/alarm/toggle/${roomId}`);
      updateAlarm(roomId, res.data);
    } catch {}
  };

  const handleProfileUpdate = useCallback((data) => {
    if (data?.profileImageUrl) preloadImages([data.profileImageUrl]);
    setMessages((prev) =>
      prev.map((msg) =>
        msg.senderId === data.userId
          ? { ...msg, senderName: data.nickname || msg.senderName, profileImageUrl: data.profileImageUrl || msg.profileImageUrl }
          : msg
      )
    );
  }, []);

  const { stompClientRef, connected: socketConnected } = useWebSocket({
    roomId: initState.isRoomValidated ? roomId : null,
    onMessageReceived: (received) => {
      if (received.type === 'INDEXING_PROGRESS') {
        setIndexingStatus(received);
        if (received.status !== 'RUNNING') setIndexingCancelling(false);
        // 완료 안내는 잠깐 보여 주고 닫는다.
        if (received.status === 'COMPLETED') {
          setTimeout(() => setIndexingStatus((cur) => (cur?.status === 'COMPLETED' ? null : cur)), 4000);
        }
        return;
      }
      if (received.clientMessageId) outboxRef.current.resolve(received.clientMessageId);
      if (received?.profileImageUrl) preloadImages([received.profileImageUrl]);

      setMessages((prev) => {
        const updated = prev.some((m) => m.messageId === received.messageId)
          ? prev.map((m) => (m.messageId === received.messageId ? received : m))
          : [...prev, received];
        return [...updated].sort((a, b) => a.messageId - b.messageId);
      });

      const isMine = received.senderId === currentUserRef.current?.id;
      if (isMine) {
        setShowScrollButton(false);
        setNewMessageCount(0);
        isAtBottomRef.current = true;
        setTimeout(() => virtuosoRef.current?.scrollToIndex({ index: 999999, behavior: 'auto' }), 30);
      } else if (isAtBottomRef.current) {
        setTimeout(() => virtuosoRef.current?.scrollToIndex({ index: 999999, behavior: 'auto' }), 30);
      } else {
        setNewMessageCount((prev) => prev + 1);
        setShowScrollButton(true);
      }
    },
    onProfileUpdate: handleProfileUpdate,
    onRoomDeleted: (e) => {
      if (isDeleting.current) return; // 본인이 삭제한 거면 모달 안 띄움
      setDeleteNotification(e.content);
    },
    onError: (message, error) => {
      // 어느 메시지가 거절됐는지 알 수 있으면 그 메시지를 "전송 실패"로 바꾼다
      if (error?.clientMessageId) outboxRef.current.fail(error.clientMessageId, message);
      setToast({ open: true, message });
      setTimeout(() => setToast({ open: false, message: '' }), 3000);
    },
  });

  // 끊겼다 다시 연결되면, 끊긴 사이에 온 메시지를 채운다 (첫 연결은 초기 로딩이 맡는다).
  // 보내던 메시지는 끊기면 기다리고, 다시 연결되면 같은 임시 id로 다시 보낸다
  const socketStateRef = useRef('never'); // never → up → dropped → up ...
  useEffect(() => {
    if (socketConnected) {
      if (socketStateRef.current === 'dropped') fetchMissingMessages();
      socketStateRef.current = 'up';
      outboxRef.current.flush();
    } else if (socketStateRef.current === 'up') {
      socketStateRef.current = 'dropped';
      outboxRef.current.pause();
    }
  }, [socketConnected, fetchMissingMessages]);

  // 다시 보낸 메시지가 이미 저장돼 있을 때 서버가 나에게만 돌려주는 확인
  useEffect(() => {
    const client = stompClientRef.current;
    if (!socketConnected || !client?.connected) return;
    const sub = client.subscribe('/user/queue/message-ack', (frame) => {
      try {
        const saved = JSON.parse(frame.body);
        const pending = outboxRef.current.items.find((it) => it.clientMessageId === saved.clientMessageId);
        outboxRef.current.resolve(saved.clientMessageId);
        // 원래 방송을 놓쳤을 수 있으니 지금 방의 메시지면 목록에 넣는다
        if (pending && String(pending.body.roomId) === String(roomIdRef.current)) {
          setMessages((prev) => (prev.some((m) => m.messageId === saved.messageId)
            ? prev
            : [...prev, saved].sort((a, b) => a.messageId - b.messageId)));
        }
      } catch (e) {
        console.error('전송 확인 처리 실패', e);
      }
    });
    return () => { try { if (client.connected) sub.unsubscribe(); } catch (e) {} };
  }, [socketConnected, stompClientRef]);

  const pendingHere = outbox.items.filter((it) => String(it.body.roomId) === String(roomId));

  useEffect(() => {
    if (!inviteCode) { navigate('/error'); return; }
    const prevRoomId = prevRoomIdRef.current;
    const init = async () => {
      if (prevRoomId) {
        readDoneRef.current = true;
        await axiosInstance.post(`/chat-rooms/${prevRoomId}/read`).catch(() => {});
        window.dispatchEvent(new CustomEvent('room:read', { detail: { roomId: prevRoomId } }));
        readDoneRef.current = false;
      }
      setInitState({ isRoomValidated: false, isMessagesLoaded: false, hasError: false, errorMessage: '' });
      setMessages([]);
      setCursor(null);
      setHasMoreMessages(true);
      setIsInitialLoad(true);
      setIsLoadingMessages(false);
      setFirstItemIndex(INDEX_OFFSET);
      setShowScrollButton(false);
      setNewMessageCount(0);
      isAtBottomRef.current = true;
      isLoadingRef.current = false;
      const id = await fetchRoomInfo();
      if (!id) { navigate('/error'); return; }
      prevRoomIdRef.current = id;
    };
    init();
  }, [inviteCode, navigate, fetchRoomInfo]);

  useEffect(() => {
    if (roomId && initState.isRoomValidated && isInitialLoad) {
      fetchMessages();
      setIsInitialLoad(false);
    }
  }, [roomId, initState.isRoomValidated, isInitialLoad, fetchMessages]);

  useEffect(() => {
    if (currentUser && roomData?.ownerId) setIsOwner(currentUser.id === roomData.ownerId);
  }, [currentUser, roomData]);

  // @멘션 후보: 방 참여자 중 나와 봇을 뺀 사람 (서버도 다시 걸러낸다)
  const [participants, setParticipants] = useState([]);
  useEffect(() => {
    if (!roomId || !initState.isRoomValidated) return;
    let cancelled = false;
    axiosInstance.get(`/chat-rooms/${roomId}/participants`)
      .then((res) => { if (!cancelled) setParticipants(res.data || []); })
      .catch(() => { if (!cancelled) setParticipants([]); });
    return () => { cancelled = true; };
  }, [roomId, initState.isRoomValidated]);
  const mentionCandidates = useMemo(
    () => participants.filter((p) => p.memberId !== currentUser?.id && !BOT_NICKNAMES.has(p.nickname)),
    [participants, currentUser?.id]
  );

  const handleStartReached = useCallback(() => {
    if (!isLoadingRef.current && hasMoreMessages && cursor) fetchMessages(cursor, true);
  }, [fetchMessages, hasMoreMessages, cursor]);

  const scrollToMessage = useCallback((messageId) => {
    // displayMessages 기준으로 인덱스 찾아야 Virtuoso 위치가 맞음
    const idx = displayMessages.findIndex((m) => m.messageId === messageId);
    if (idx === -1) return;
    virtuosoRef.current?.scrollToIndex({ index: idx, behavior: 'smooth', align: 'center' });
    setTimeout(() => {
      const el = document.getElementById(`message-${messageId}`);
      if (!el) return;
      el.style.backgroundColor = 'var(--mention-bg)';
      setTimeout(() => { el.style.backgroundColor = ''; }, 2000);
    }, 300);
  }, [displayMessages]);

  const scrollToBottom = useCallback(() => {
    virtuosoRef.current?.scrollToIndex({ index: 999999, behavior: 'smooth' });
    setShowScrollButton(false);
    setNewMessageCount(0);
  }, []);

  const handleLeaveRoom = async () => {
    try {
      await axiosInstance.delete(`/chat-rooms/${roomId}/leave`);
      navigate('/');
      return { success: true };
    } catch (err) {
      return { success: false, error: err.response?.data?.message || '나가기 실패' };
    }
  };

  const isDeleting = useRef(false);
  const handleDeleteRoom = async () => {
    try {
      isDeleting.current = true;
      await axiosInstance.delete(`/chat-rooms/${roomId}`);
      window.dispatchEvent(new CustomEvent('room:deleted', { detail: { roomId } }));
      return { success: true };
    } catch (err) {
      isDeleting.current = false;
      return { success: false, error: err.response?.data?.message || '삭제 실패' };
    }
  };

  const handleSearch = async (keyword, lastMessageId = null) => {
    if (!roomId || !initState.isRoomValidated) { setErrorMessage('채팅방이 아직 로딩 중입니다.'); return; }
    setIsSearching(true);
    setShowSearchSidebar(true);
    if (keyword !== searchKeyword) { setSearchKeyword(keyword); setSearchTotalCount(null); }
    setErrorMessage(null);
    try {
      const params = { keyword, pageSize: 10 };
      if (lastMessageId) params.lastMessageId = lastMessageId;
      const res = await axiosInstance.get(`/chat/search/${roomId}`, { params });
      const data = res.data;
      setSearchResults((data.content || []).map((msg) => ({
        ...msg,
        createdAt: msg.createdAt && !isNaN(new Date(msg.createdAt)) ? msg.createdAt : new Date().toISOString(),
      })));
      setSearchHasNext(!data.last);
      if (!lastMessageId && data.totalCount !== undefined) setSearchTotalCount(data.totalCount);
    } catch (err) {
      setErrorMessage(err.response?.data?.message || '검색 오류');
      setSearchResults([]);
    } finally {
      setIsSearching(false);
    }
  };

  const handleUnifiedSend = async () => {
    if (inputMode === 'IMAGE') {
      if (!imageFile) { alert('이미지를 선택하세요.'); return; }
      try {
        const formData = new FormData();
        formData.append('image', imageFile);
        const res = await axiosInstance.post('/send-image', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
        sendMessage({ type: 'IMAGE', content: '', imageFileId: res.data });
        setImageFile(null); setImagePreviewUrl(null);
      } catch (err) { alert(uploadErrorMessage(err, '이미지 전송에 실패했습니다.')); }
    } else {
      sendMessage();
    }
  };

  const sendMessage = (override = null) => {
    if (!roomId || !initState.isRoomValidated) { alert('채팅방 로딩 중입니다.'); return; }
    if (!currentUser) { alert('사용자 정보 로딩 중입니다.'); return; }
    // 키보드 전송은 버튼 비활성과 상관없이 들어오므로 여기서도 막는다
    if (!override && isTooLong(content)) return;
    const base = {
      content,
      type: inputMode,
      createdAt: new Date().toISOString(),
      senderName: currentUser.nickname,
      profileImageUrl: currentUser.profileImageUrl,
      ...(inputMode === 'CODE' && { language }),
    };
    if (inputMode === 'TEXT') {
      const mentionedMemberIds = findMentionedIds(content, mentionCandidates);
      if (mentionedMemberIds.length) base.mentionedMemberIds = mentionedMemberIds;
    }
    const msg = override ? { ...base, ...override } : base;
    if (msg.type !== 'IMAGE' && !String(msg.content).trim()) return;
    // 끊겨 있어도 버리지 않는다: "연결되면 보낼게요"로 두었다가 다시 연결되면 보낸다
    outbox.enqueue({ roomId, payload: msg });
    setContent(''); setInputMode('TEXT');
    isAtBottomRef.current = true;
    setTimeout(() => virtuosoRef.current?.scrollTo({ top: Number.MAX_SAFE_INTEGER }), 30);
  };

  const handleEditMessage = (messageId) => {
    const client = stompClientRef.current;
    if (!client?.connected) { alert('서버에 연결되어 있지 않습니다.'); return; }
    if (isTooLong(editContent)) { alert(TOO_LONG_MESSAGE); return; }
    client.publish({ destination: `/chat/edit-message/${roomId}`, body: JSON.stringify({ messageId, content: editContent }) });
    setEditMessageId(null); setEditContent('');
  };

  const handleDeleteMessage = (messageId) => {
    const client = stompClientRef.current;
    if (!client?.connected) { alert('서버에 연결되어 있지 않습니다.'); return; }
    client.publish({ destination: `/chat/delete-message/${roomId}`, body: messageId });
    setContextMenuId(null);
  };

  if (initState.hasError) {
    return (
      <div className={s.centerState}>
        <p className={s.centerError}>{initState.errorMessage}</p>
        <button type="button" className="dc-btn dc-btn-primary" onClick={() => navigate('/')}>홈으로</button>
      </div>
    );
  }

  return (
    <>
      <div className={s.roomLayout}>
        <div className={`${s.roomColumn} ${showSearchSidebar ? s.roomColumnWithPanel : ''}`}>

          {getAlarmStatus(roomId) !== undefined && (
            <RoomHeader
              aiReviewEnabled={aiReviewEnabled}
              onToggleAiReview={handleToggleAiReview}
              aiSummaryEnabled={aiSummaryEnabled}
              onToggleAiSummary={handleToggleAiSummary}
              repositoryUrl={roomData?.repositoryUrl}
              onConnectRepo={() => setShowConnectRepo(true)}
              roomId={roomId} roomName={roomName} inviteCode={inviteCode}
              onSearch={handleSearch} onLeaveRoom={handleLeaveRoom}
              onDeleteRoom={handleDeleteRoom} isOwner={isOwner}
              toggleAlarm={toggleAlarm} alarmEnabled={getAlarmStatus(roomId)}
            />
          )}

          <IndexingBar
            indexing={indexingStatus}
            isOwner={isOwner}
            cancelling={indexingCancelling}
            onCancel={handleCancelIndexing}
            onRestart={handleRestartIndexing}
          />

          <div className={s.messagesArea}>
            {!isFullyLoaded && (
              <div className={s.loadingOverlay}>
                <div className={s.spinner} />
                <div>불러오는 중...</div>
              </div>
            )}

            {isFullyLoaded && displayMessages.length === 0 && (
              <div className={`${s.emptyRoom} dc-scroll`}>
                <div className={s.emptyRoomInner}>
                  <div className={s.emptyRoomIcon}><MessageSquareDashed size={26} /></div>
                  <div className={s.channelIntroTitle}><span>{roomName}</span></div>
                  <p className={s.emptyRoomDesc}>아직 대화가 없어요. 아래 입력창에 첫 메시지를 남겨 보세요.</p>
                  <PendingMessages items={pendingHere} onRetry={outbox.retry} onDiscard={outbox.discard} />

                  <ul className={s.emptyRoomTips}>
                    <li>
                      <span className={s.emptyRoomTipIcon}><Code2 size={16} /></span>
                      <span>
                        <b>코드 블록</b>
                        <em>입력창 아래 <Code2 size={12} /> 버튼으로 코드를 하이라이트해서 공유할 수 있어요.</em>
                      </span>
                    </li>
                    <li>
                      <span className={s.emptyRoomTipIcon}><GitPullRequest size={16} /></span>
                      <span>
                        <b>PR 소식 · AI 리뷰</b>
                        <em>
                          {roomData?.repositoryUrl
                            ? '연결된 레포에 PR이 올라오면 여기에 알림과 AI 코드 리뷰가 함께 올라와요.'
                            : '레포를 연결하면 PR 알림과 AI 코드 리뷰가 이 방에 올라와요.'}
                        </em>
                      </span>
                      {!roomData?.repositoryUrl && isOwner && (
                        <button type="button" className={s.emptyRoomCopy} onClick={() => setShowConnectRepo(true)}>
                          <GitPullRequest size={13} /> 레포 연결
                        </button>
                      )}
                    </li>
                    <li>
                      <span className={s.emptyRoomTipIcon}><UserPlus size={16} /></span>
                      <span>
                        <b>팀원 초대</b>
                        <em>초대 코드를 공유하면 팀원이 바로 들어올 수 있어요.</em>
                      </span>
                      <button
                        type="button"
                        className={s.emptyRoomCopy}
                        onClick={async () => {
                          try {
                            await navigator.clipboard.writeText(inviteCode);
                            setToast({ open: true, message: '초대 코드를 복사했어요' });
                          } catch {
                            setToast({ open: true, message: `초대 코드: ${inviteCode}` });
                          }
                          setTimeout(() => setToast({ open: false, message: '' }), 2500);
                        }}
                      >
                        <Copy size={13} /> 코드 복사
                      </button>
                    </li>
                  </ul>
                </div>
              </div>
            )}

            {displayMessages.length > 0 && (
              <Virtuoso
                key={roomId}
                ref={virtuosoRef}
                className="dc-scroll"
                style={{ height: '100%' }}
                firstItemIndex={firstItemIndex}
                initialTopMostItemIndex={999999}
                data={displayMessages}
                followOutput={false}
                atBottomStateChange={(isAtBottom) => {
                  isAtBottomRef.current = isAtBottom;
                  if (isAtBottom) {
                    setShowScrollButton(false);
                    setNewMessageCount(0);
                  } else {
                    setShowScrollButton(true);
                  }
                }}
                startReached={handleStartReached}
                overscan={300}
                components={{
                  Header: () =>
                    isLoadingMessages && hasMoreMessages ? (
                      <div className={s.listHeaderLoading}>메시지 불러오는 중...</div>
                    ) : !hasMoreMessages && displayMessages.length > 0 ? (
                      <div className={s.channelIntro}>
                        <div className={s.channelIntroTitle}><span>{roomName}</span></div>
                        <div className={s.channelIntroDesc}>
                          이 방의 첫 메시지예요.
                          {roomData?.repositoryUrl ? ' 연결된 레포의 PR 소식과 AI 리뷰도 여기에 올라와요.' : ''}
                        </div>
                      </div>
                    ) : null,
                  Footer: () => (
                    <>
                      <PendingMessages items={pendingHere} onRetry={outbox.retry} onDiscard={outbox.discard} />
                      <div style={{ height: 12 }} />
                    </>
                  ),
                }}
                itemContent={(index, msg) => {
                  const arrayIndex = index - firstItemIndex;
                  const prevMsg = arrayIndex > 0 ? displayMessages[arrayIndex - 1] : null;
                  const nextMsg = displayMessages[arrayIndex + 1] || null;
                  const subMessages = subMessagesMap.get(msg.messageId) || [];
                  return (
                    <VirtuosoMessageItem
                      msg={msg} prevMsg={prevMsg} nextMsg={nextMsg} currentUser={currentUser}
                      contextMenuId={contextMenuId} setContextMenuId={setContextMenuId}
                      setEditMessageId={setEditMessageId} setEditContent={setEditContent}
                      handleDeleteMessage={handleDeleteMessage} editMessageId={editMessageId}
                      editContent={editContent} handleEditMessage={handleEditMessage}
                      onCodeClick={handleCodeClick}
                      onRetryClick={handleRetryAiReview}
                      subMessages={subMessages}
                    />
                  );
                }}
              />
            )}
          </div>

          {showScrollButton && (
            <button
              type="button"
              onClick={scrollToBottom}
              className={s.jumpButton}
              style={{ bottom: `${inputHeight + 12}px` }}
            >
              {newMessageCount > 0
                ? <>새 메시지 {newMessageCount > 99 ? '99+' : newMessageCount}개 ↓</>
                : <>최근 메시지로 ↓</>}
            </button>
          )}

          <div ref={inputAreaRef} className={s.composerArea} style={{ pointerEvents: isFullyLoaded ? 'auto' : 'none' }}>
            <MessageInput
              inputMode={inputMode} setInputMode={setInputMode}
              content={content} setContent={setContent}
              language={language} setLanguage={setLanguage}
              handleUnifiedSend={handleUnifiedSend}
              setImageFile={setImageFile}
              imagePreviewUrl={imagePreviewUrl} setImagePreviewUrl={setImagePreviewUrl}
              roomName={roomName}
              mentionCandidates={mentionCandidates}
            />
          </div>
        </div>

        {showSearchSidebar && (
          <SearchSidebar
            totalCount={searchTotalCount} onSearch={handleSearch}
            searchKeyword={searchKeyword} searchResults={searchResults}
            isSearching={isSearching} errorMessage={errorMessage}
            hasNext={searchHasNext} onClose={() => setShowSearchSidebar(false)}
            onMessageClick={(id) => {
              // 모바일에선 검색 패널이 전체 화면이라 닫고 이동한다
              if (window.matchMedia && window.matchMedia('(max-width: 768px)').matches) setShowSearchSidebar(false);
              scrollToMessage(id);
            }}
          />
        )}

        {showConnectRepo && (
          <ConnectRepoModal
            roomName={roomName}
            onClose={() => setShowConnectRepo(false)}
            onSubmit={handleConnectRepo}
          />
        )}

        {showCodeModal && selectedCodeMessage && (
          selectedCodeMessage.type === 'AI_REVIEW' ? (
            <AiReviewModal
              message={selectedCodeMessage}
              roomId={roomId}
              onClose={() => { setShowCodeModal(false); setSelectedCodeMessage(null); }}
              repositoryUrl={roomData.repositoryUrl}
            />
          ) : (
            <CodeReviewModal
              message={selectedCodeMessage} roomId={roomId} currentUser={currentUser}
              onClose={() => { setShowCodeModal(false); setSelectedCodeMessage(null); }}
            />
          )
        )}

        <RoomDeletedModal
          isOpen={!!deleteNotification} message={deleteNotification}
          onClose={() => setDeleteNotification(null)}
        />

        <Toast open={toast.open} message={toast.message} />
      </div>
    </>
  );
};

export default ChatRoom;
