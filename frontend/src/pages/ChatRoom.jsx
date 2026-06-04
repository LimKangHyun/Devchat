import React, { useEffect, useState, useRef, useCallback } from 'react';
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
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
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

const VirtuosoMessageItem = React.memo(({
  msg, prevMsg,
  currentUser, contextMenuId, setContextMenuId,
  setEditMessageId, setEditContent,
  handleDeleteMessage, editMessageId, editContent,
  handleEditMessage, onCodeClick,
}) => {
  const msgDate = formatDate(msg.createdAt || msg.joinAt);
  const prevDate = prevMsg ? formatDate(prevMsg.createdAt || prevMsg.joinAt) : null;

  return (
    <div>
      {msgDate !== prevDate && <DateDivider label={msgDate} />}
      <MessageItem
        msg={msg}
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

  const virtuosoRef = useRef(null);
  const inputAreaRef = useRef(null);
  const navigate = useNavigate();
  const roomIdRef = useRef(null);
  const prevRoomIdRef = useRef(null);
  const readDoneRef = useRef(false);
  const isLoadingRef = useRef(false);
  const isAtBottomRef = useRef(true);
  const currentUserRef = useRef(null);
  const lastSequenceRef = useRef(null);

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

  const [initState, setInitState] = useState({
    isRoomValidated: false,
    isMessagesLoaded: false,
    hasError: false,
    errorMessage: '',
  });

  const isFullyLoaded = initState.isRoomValidated && initState.isMessagesLoaded && !!currentUser;

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

  const fetchRoomInfo = useCallback(async () => {
    try {
      const res = await axiosInstance.get(`/chat-rooms/${inviteCode}`);
      const data = res.data;
      setRoomId(data.roomId);
      setRoomName(data.roomName);
      setRoomData(data);
      updateAlarm(data.roomId, data.alarmEnabled);
      setInitState((prev) => ({ ...prev, isRoomValidated: true }));
      return data.roomId;
    } catch {
      setInitState((prev) => ({ ...prev, hasError: true, errorMessage: '방 정보를 불러올 수 없습니다.' }));
      return null;
    }
  }, [inviteCode, updateAlarm]);

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
        if (sorted.length > 0) {
          const lastMsg = sorted[sorted.length - 1];
          if (lastMsg.sequence != null) lastSequenceRef.current = lastMsg.sequence;
        }
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

  const fetchMissingMessages = useCallback(async () => {
    if (!roomId) return;
    try {
      const res = await axiosInstance.get(`/${roomId}/messages`);
      const data = res.data;
      const messageList = data.messages || [];
      const sorted = messageList
        .map((msg) => ({ ...msg, createdAt: msg.createdAt && !isNaN(new Date(msg.createdAt).getTime()) ? msg.createdAt : new Date().toISOString() }))
        .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

      setMessages((prev) => {
        const ids = new Set(prev.map((m) => m.messageId));
        const missing = sorted.filter((m) => !ids.has(m.messageId));
        if (missing.length === 0) return prev;
        return [...prev, ...missing].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
      });
    } catch (e) {
      console.error('누락 메시지 조회 실패', e);
    }
  }, [roomId]);

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

  const { stompClientRef } = useWebSocket({
    roomId: initState.isRoomValidated ? roomId : null,
    onMessageReceived: (received) => {
      if (received?.profileImageUrl) preloadImages([received.profileImageUrl]);
      if (received.sequence != null && lastSequenceRef.current != null) {
        if (received.sequence - lastSequenceRef.current > 1) fetchMissingMessages();
      }
      if (received.sequence != null) lastSequenceRef.current = received.sequence;

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
    onRoomDeleted: (e) => setDeleteNotification(e.content),
    onError: (message) => {
      setToast({ open: true, message });
      setTimeout(() => setToast({ open: false, message: '' }), 3000);
    },
  });

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
      lastSequenceRef.current = null;
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

  const handleStartReached = useCallback(() => {
    if (!isLoadingRef.current && hasMoreMessages && cursor) fetchMessages(cursor, true);
  }, [fetchMessages, hasMoreMessages, cursor]);

  const scrollToMessage = useCallback((messageId) => {
    const idx = messages.findIndex((m) => m.messageId === messageId);
    if (idx === -1) return;
    virtuosoRef.current?.scrollToIndex({ index: idx, behavior: 'smooth', align: 'center' });
    setTimeout(() => {
      const el = document.getElementById(`message-${messageId}`);
      if (!el) return;
      el.style.backgroundColor = '#fff8e1';
      el.style.borderRadius = '6px';
      el.style.transition = 'background-color 0.3s ease';
      setTimeout(() => { el.style.backgroundColor = ''; el.style.borderRadius = ''; }, 2000);
    }, 300);
  }, [messages]);

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

  const handleDeleteRoom = async () => {
    try {
      await axiosInstance.delete(`/chat-rooms/${roomId}`);
      return { success: true };
    } catch (err) {
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
      } catch { alert('이미지 전송 실패'); }
    } else {
      sendMessage();
    }
  };

  const sendMessage = (override = null) => {
    const client = stompClientRef.current;
    if (!roomId || !initState.isRoomValidated) { alert('채팅방 로딩 중입니다.'); return; }
    if (!client?.connected) { alert('서버와 연결이 끊어졌습니다.'); return; }
    if (!currentUser) { alert('사용자 정보 로딩 중입니다.'); return; }
    const base = {
      content,
      type: inputMode,
      createdAt: new Date().toISOString(),
      senderName: currentUser.nickname,
      profileImageUrl: currentUser.profileImageUrl,
      ...(inputMode === 'CODE' && { language }),
    };
    const msg = override ? { ...base, ...override } : base;
    if (msg.type !== 'IMAGE' && !String(msg.content).trim()) return;
    client.publish({ destination: `/chat/send-message/${roomId}`, body: JSON.stringify(msg) });
    setContent(''); setInputMode('TEXT');
  };

  const handleEditMessage = (messageId) => {
    const client = stompClientRef.current;
    if (!client?.connected) { alert('서버에 연결되어 있지 않습니다.'); return; }
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
      <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#f8f8f8' }}>
        <div style={{ textAlign: 'center' }}>
          <p style={{ color: '#e01e5a', marginBottom: '16px', fontSize: '15px' }}>{initState.errorMessage}</p>
          <button onClick={() => navigate('/')} style={{ padding: '8px 20px', backgroundColor: '#1264a3', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '14px' }}>홈으로</button>
        </div>
      </div>
    );
  }

  return (
    <>
      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        @keyframes fadeIn { from { opacity: 0; transform: translateX(-50%) translateY(8px); } to { opacity: 1; transform: translateX(-50%) translateY(0); } }
      `}</style>

      <div style={{ height: '100%', display: 'flex', backgroundColor: '#ffffff', fontFamily: '-apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif' }}>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, borderRight: showSearchSidebar ? '1px solid #f0f0f0' : 'none', position: 'relative' }}>

          {getAlarmStatus(roomId) !== undefined && (
            <div style={{ borderBottom: '1px solid #f0f0f0', backgroundColor: '#ffffff', flexShrink: 0 }}>
              <RoomHeader
                roomName={roomName} inviteCode={inviteCode}
                onSearch={handleSearch} onLeaveRoom={handleLeaveRoom}
                onDeleteRoom={handleDeleteRoom} isOwner={isOwner}
                toggleAlarm={toggleAlarm} alarmEnabled={getAlarmStatus(roomId)}
              />
            </div>
          )}

          <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
            {!isFullyLoaded && (
              <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff', zIndex: 10 }}>
                <div style={{ textAlign: 'center' }}>
                  <div style={{ width: '28px', height: '28px', border: '2.5px solid #e8e8e8', borderTop: '2.5px solid #1264a3', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 10px' }} />
                  <div style={{ fontSize: '13px', color: '#aaa' }}>불러오는 중...</div>
                </div>
              </div>
            )}

            {messages.length > 0 && (
              <Virtuoso
                key={roomId}
                ref={virtuosoRef}
                style={{ height: '100%' }}
                firstItemIndex={firstItemIndex}
                initialTopMostItemIndex={999999}
                data={messages}
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
                      <div style={{ textAlign: 'center', padding: '6px 14px', color: '#888', fontSize: '12px', backgroundColor: '#f8f8f8', borderRadius: '12px', margin: '8px auto', width: 'fit-content', border: '1px solid #efefef' }}>
                        메시지 불러오는 중...
                      </div>
                    ) : !hasMoreMessages && messages.length > 0 ? (
                      <div style={{ textAlign: 'center', padding: '6px 14px', color: '#bbb', fontSize: '11px', margin: '8px auto 16px', width: 'fit-content' }}>
                        채널의 시작입니다
                      </div>
                    ) : null,
                }}
                itemContent={(index, msg) => {
                  const arrayIndex = index - firstItemIndex;
                  const prevMsg = arrayIndex > 0 ? messages[arrayIndex - 1] : null;
                  return (
                    <VirtuosoMessageItem
                      msg={msg} prevMsg={prevMsg} currentUser={currentUser}
                      contextMenuId={contextMenuId} setContextMenuId={setContextMenuId}
                      setEditMessageId={setEditMessageId} setEditContent={setEditContent}
                      handleDeleteMessage={handleDeleteMessage} editMessageId={editMessageId}
                      editContent={editContent} handleEditMessage={handleEditMessage}
                      onCodeClick={handleCodeClick}
                      onRetryClick={handleRetryAiReview}
                    />
                  );
                }}
              />
            )}
          </div>

          {showScrollButton && (
            <div
              onClick={scrollToBottom}
              style={{
                position: 'absolute',
                bottom: `${inputHeight + 16}px`,
                left: '50%',
                transform: 'translateX(-50%)',
                backgroundColor: '#1264a3',
                color: 'white',
                padding: '8px 18px',
                borderRadius: '20px',
                fontSize: '13px',
                fontWeight: '500',
                cursor: 'pointer',
                boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
                zIndex: 10,
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                animation: 'fadeIn 0.2s ease',
                userSelect: 'none',
                whiteSpace: 'nowrap',
              }}
            >
              {newMessageCount > 0 && (
                <span style={{ backgroundColor: '#e01e5a', borderRadius: '10px', padding: '1px 7px', fontSize: '11px', fontWeight: '700' }}>
                  {newMessageCount > 99 ? '99+' : newMessageCount}
                </span>
              )}
              맨 아래로 가기 ↓
            </div>
          )}

          <div ref={inputAreaRef} style={{ padding: '0 20px 16px', backgroundColor: '#ffffff', flexShrink: 0, pointerEvents: isFullyLoaded ? 'auto' : 'none' }}>
            <MessageInput
              inputMode={inputMode} setInputMode={setInputMode}
              content={content} setContent={setContent}
              language={language} setLanguage={setLanguage}
              handleUnifiedSend={handleUnifiedSend}
              setImageFile={setImageFile}
              imagePreviewUrl={imagePreviewUrl} setImagePreviewUrl={setImagePreviewUrl}
            />
          </div>
        </div>

        {showSearchSidebar && (
          <SearchSidebar
            totalCount={searchTotalCount} onSearch={handleSearch}
            searchKeyword={searchKeyword} searchResults={searchResults}
            isSearching={isSearching} errorMessage={errorMessage}
            hasNext={searchHasNext} onClose={() => setShowSearchSidebar(false)}
            onMessageClick={scrollToMessage}
          />
        )}

        {showCodeModal && selectedCodeMessage && (
          <CodeReviewModal
            message={selectedCodeMessage} roomId={roomId} currentUser={currentUser}
            onClose={() => { setShowCodeModal(false); setSelectedCodeMessage(null); }}
          />
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