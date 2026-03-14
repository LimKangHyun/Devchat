import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import SearchSidebar from '../components/SearchSideBar';
import axiosInstance from '../components/api/axiosInstance';
import MessageInput from '../components/chatroom/MessageInput';
import MessageList from '../components/chatroom/MessageList';
import useWebSocket from '../components/common/useWebSocket';
import RoomHeader from '../components/chatroom/RoomHeader';
import RoomDeletedModal from '../components/modals/RoomDeletedModal';
import CodeReviewModal from '../components/modals/CodeReviewModal.jsx';
import { useAlarm } from '../context/AlarmContext';

const ChatRoom = () => {
  const { inviteCode } = useParams();
  const [messages, setMessages] = useState([]);
  const [content, setContent] = useState('');
  const [inputMode, setInputMode] = useState('TEXT');
  const [language, setLanguage] = useState('java');
  const [currentUser, setCurrentUser] = useState(null);
  const [contextMenuId, setContextMenuId] = useState(null);
  const [editMessageId, setEditMessageId] = useState(null);
  const [editContent, setEditContent] = useState('');
  const [cursor, setCursor] = useState(null);
  const [hasMoreMessages, setHasMoreMessages] = useState(true);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [isInitialLoad, setIsInitialLoad] = useState(true);
  const messagesEndRef = useRef(null);
  const messagesStartRef = useRef(null);
  const messageContainerRef = useRef(null);
  const prevScrollHeightRef = useRef(0);
  const navigate = useNavigate();
  const roomIdRef = useRef(null);
  const prevRoomIdRef = useRef(null);
  const readDoneRef = useRef(false);
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
    isUserLoaded: false,
    isMessagesLoaded: false,
    hasError: false,
    errorMessage: '',
  });

  const isFullyLoaded =
    initState.isRoomValidated && initState.isUserLoaded && initState.isMessagesLoaded;

  const { getAlarmStatus, updateAlarm } = useAlarm();

  // roomId가 세팅될 때 ref도 업데이트
  useEffect(() => {
    roomIdRef.current = roomId;
  }, [roomId]);

  // 컴포넌트 완전 언마운트 시 read 처리 (방 이동이 아닌 경우에만)
  useEffect(() => {
    return () => {
      if (roomIdRef.current && !readDoneRef.current) {
        axiosInstance.post(`/chat-rooms/${roomIdRef.current}/read`)
          .then(() => window.dispatchEvent(new CustomEvent('room:read')))
          .catch(() => {});
      }
    };
  }, []);

  const handleCodeClick = (message) => {
    setSelectedCodeMessage(message);
    setShowCodeModal(true);
  };

  const scrollToBottom = useCallback(() => {
    if (messageContainerRef.current) {
      requestAnimationFrame(() => {
        messageContainerRef.current.scrollTop = messageContainerRef.current.scrollHeight;
      });
    }
  }, []);

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
      setInitState((prev) => ({
        ...prev,
        hasError: true,
        errorMessage: '방 정보를 불러올 수 없습니다.',
      }));
      return null;
    }
  }, [inviteCode]);

  const fetchMessages = useCallback(
    async (cursorValue = null, isLoadMore = false) => {
      setIsLoadingMessages((prev) => {
        if (prev) return prev;
        return true;
      });
      try {
        const params = { size: 30 };
        if (cursorValue) params.cursor = cursorValue;
        const res = await axiosInstance.get(`/${roomId}/messages`, { params });
        const data = res.data;
        const messageList = data.messages || [];
        const validated = messageList.map((msg) => ({
          ...msg,
          sendAt:
            msg.sendAt && !isNaN(new Date(msg.sendAt).getTime())
              ? msg.sendAt
              : new Date().toISOString(),
        }));
        const sorted = [...validated].sort(
          (a, b) => new Date(a.sendAt) - new Date(b.sendAt)
        );
        if (isLoadMore) {
          prevScrollHeightRef.current = messageContainerRef.current?.scrollHeight || 0;
          setMessages((prev) => {
            const ids = new Set(prev.map((m) => m.messageId));
            return [...sorted.filter((m) => !ids.has(m.messageId)), ...prev];
          });
        } else {
          setMessages(sorted);
          setInitState((prev) => ({ ...prev, isMessagesLoaded: true }));
        }
        setCursor(data.nextCursor);
        setHasMoreMessages(!!data.nextCursor && messageList.length > 0);
      } catch (e) {
        console.error(e);
      } finally {
        setIsLoadingMessages(false);
      }
    },
    [roomId]
  );

  const fetchCurrentUser = useCallback(async () => {
    try {
      const res = await axiosInstance.get('/user/details');
      setCurrentUser(res.data);
    } catch {}
    setInitState((prev) => ({ ...prev, isUserLoaded: true }));
  }, []);

  const toggleAlarm = async () => {
    try {
      const res = await axiosInstance.post(`/chat-rooms/alarm/toggle/${roomId}`);
      updateAlarm(roomId, res.data);
    } catch {}
  };

  const handleProfileUpdate = useCallback((data) => {
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
      setMessages((prev) => {
        const updated = prev.some((m) => m.messageId === received.messageId)
          ? prev.map((m) => (m.messageId === received.messageId ? received : m))
          : [...prev, received];
        const sorted = [...updated].sort((a, b) => new Date(a.sendAt) - new Date(b.sendAt));
        if (messageContainerRef.current) {
          const { scrollTop, clientHeight, scrollHeight } = messageContainerRef.current;
          if (scrollTop + clientHeight >= scrollHeight - 100 || received.senderId === currentUser?.userId) {
            scrollToBottom();
          }
        }
        return sorted;
      });
    },
    onProfileUpdate: handleProfileUpdate,
    onRoomDeleted: (e) => setDeleteNotification(e.content),
  });

  const restoreScrollPosition = useCallback(() => {
    if (!messageContainerRef.current) return;
    const diff = messageContainerRef.current.scrollHeight - prevScrollHeightRef.current;
    if (diff > 0) messageContainerRef.current.scrollTop += diff;
  }, []);

  const handleScroll = useCallback(() => {
    if (!messageContainerRef.current || !roomId || !initState.isRoomValidated) return;
    const { scrollTop, scrollHeight } = messageContainerRef.current;
    if (scrollTop <= 150 && !isLoadingMessages && hasMoreMessages && cursor) {
      prevScrollHeightRef.current = scrollHeight;
      fetchMessages(cursor, true);
    }
  }, [fetchMessages, hasMoreMessages, cursor, isLoadingMessages, roomId, initState.isRoomValidated]);

  useEffect(() => {
    const el = messageContainerRef.current;
    if (!el) return;
    let tid = null;
    const debounced = () => {
      clearTimeout(tid);
      tid = setTimeout(handleScroll, 150);
    };
    el.addEventListener('scroll', debounced, { passive: true });
    return () => { el.removeEventListener('scroll', debounced); clearTimeout(tid); };
  }, [handleScroll]);

  useEffect(() => {
    if (!inviteCode) { navigate('/error'); return; }

    const prevRoomId = prevRoomIdRef.current;

    const init = async () => {
      // 이전 방 read 처리 (방 이동 시)
      if (prevRoomId) {
        readDoneRef.current = true;
        await axiosInstance.post(`/chat-rooms/${prevRoomId}/read`).catch(() => {});
        window.dispatchEvent(new CustomEvent('room:read', { detail: { roomId: prevRoomId } }));
        readDoneRef.current = false;
      }

      setInitState({ isRoomValidated: false, isUserLoaded: false, isMessagesLoaded: false, hasError: false, errorMessage: '' });
      setMessages([]); setCursor(null); setHasMoreMessages(true);
      setIsInitialLoad(true); setIsLoadingMessages(false); prevScrollHeightRef.current = 0;

      const [id] = await Promise.all([fetchRoomInfo(), fetchCurrentUser()]);
      if (!id) { navigate('/error'); return; }

      prevRoomIdRef.current = id;
    };
    init();
  }, [inviteCode, navigate, fetchRoomInfo, fetchCurrentUser]);

  useEffect(() => {
    if (roomId && initState.isRoomValidated && isInitialLoad) {
      fetchMessages();
      setIsInitialLoad(false);
    }
  }, [roomId, initState.isRoomValidated, isInitialLoad, fetchMessages]);

  useEffect(() => {
    if (!isInitialLoad && messages.length > 0) {
      if (isLoadingMessages) {
        requestAnimationFrame(() => requestAnimationFrame(restoreScrollPosition));
      } else if (prevScrollHeightRef.current === 0 && messageContainerRef.current) {
        scrollToBottom();
      }
    }
  }, [messages, isInitialLoad, restoreScrollPosition, isLoadingMessages, scrollToBottom]);

  useEffect(() => {
    if (currentUser && roomData?.ownerId) {
      setIsOwner(currentUser.id === roomData.ownerId);
    }
  }, [currentUser, roomData]);

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
    if (!roomId || !initState.isRoomValidated) {
      setErrorMessage('채팅방이 아직 로딩 중입니다.');
      return;
    }
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
        sendAt: msg.sendAt && !isNaN(new Date(msg.sendAt)) ? msg.sendAt : new Date().toISOString(),
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

  const scrollToMessage = (messageId) => {
    const el = document.getElementById(`message-${messageId}`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.style.backgroundColor = '#fff8e1';
    el.style.borderRadius = '6px';
    el.style.transition = 'background-color 0.3s ease';
    setTimeout(() => { el.style.backgroundColor = ''; el.style.borderRadius = ''; }, 2000);
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
    const base = { content, type: inputMode, sendAt: new Date().toISOString(), ...(inputMode === 'CODE' && { language }) };
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
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#f8f8f8' }}>
        <div style={{ textAlign: 'center' }}>
          <p style={{ color: '#e01e5a', marginBottom: '16px', fontSize: '15px' }}>{initState.errorMessage}</p>
          <button onClick={() => navigate('/')} style={{ padding: '8px 20px', backgroundColor: '#1264a3', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '14px' }}>
            홈으로
          </button>
        </div>
      </div>
    );
  }

  return (
    <>
      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        .msg-container::-webkit-scrollbar { width: 6px; }
        .msg-container::-webkit-scrollbar-track { background: transparent; }
        .msg-container::-webkit-scrollbar-thumb { background: #e0e0e0; border-radius: 3px; }
        .msg-container::-webkit-scrollbar-thumb:hover { background: #c8c8c8; }
      `}</style>

      <div style={{
        height: '100%',
        display: 'flex',
        backgroundColor: '#ffffff',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif',
      }}>
        <div style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          minWidth: 0,
          borderRight: showSearchSidebar ? '1px solid #f0f0f0' : 'none',
        }}>
          {getAlarmStatus(roomId) !== undefined && (
            <div style={{ borderBottom: '1px solid #f0f0f0', backgroundColor: '#ffffff', flexShrink: 0 }}>
              <RoomHeader
                roomName={roomName}
                inviteCode={inviteCode}
                onSearch={handleSearch}
                onLeaveRoom={handleLeaveRoom}
                onDeleteRoom={handleDeleteRoom}
                isOwner={isOwner}
                toggleAlarm={toggleAlarm}
                alarmEnabled={getAlarmStatus(roomId)}
              />
            </div>
          )}

          <div
            ref={messageContainerRef}
            className="msg-container"
            style={{
              flex: 1, overflowY: 'auto', padding: '16px 20px',
              backgroundColor: '#ffffff', minHeight: 0, position: 'relative',
              opacity: isFullyLoaded ? 1 : 0.6, transition: 'opacity 0.2s',
            }}
          >
            {!isFullyLoaded && (
              <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', textAlign: 'center', zIndex: 10 }}>
                <div style={{ width: '28px', height: '28px', border: '2.5px solid #e8e8e8', borderTop: '2.5px solid #1264a3', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 10px' }} />
                <div style={{ fontSize: '13px', color: '#aaa' }}>불러오는 중...</div>
              </div>
            )}

            {isLoadingMessages && hasMoreMessages && (
              <div style={{ textAlign: 'center', padding: '6px 14px', color: '#888', fontSize: '12px', backgroundColor: '#f8f8f8', borderRadius: '12px', margin: '0 auto 12px', width: 'fit-content', border: '1px solid #efefef' }}>
                메시지 불러오는 중...
              </div>
            )}

            {!hasMoreMessages && messages.length > 0 && !isInitialLoad && (
              <div style={{ textAlign: 'center', padding: '6px 14px', color: '#bbb', fontSize: '11px', margin: '0 auto 16px', width: 'fit-content' }}>
                채널의 시작입니다
              </div>
            )}

            <div ref={messagesStartRef} />
            <MessageList
              messages={messages}
              currentUser={currentUser}
              contextMenuId={contextMenuId}
              setContextMenuId={setContextMenuId}
              setEditMessageId={setEditMessageId}
              setEditContent={setEditContent}
              handleDeleteMessage={handleDeleteMessage}
              editMessageId={editMessageId}
              editContent={editContent}
              handleEditMessage={handleEditMessage}
              onCodeClick={handleCodeClick}
            />
            <div ref={messagesEndRef} />
          </div>

          <div style={{ padding: '0 20px 16px', backgroundColor: '#ffffff', flexShrink: 0, pointerEvents: isFullyLoaded ? 'auto' : 'none' }}>
            <MessageInput
              inputMode={inputMode}
              setInputMode={setInputMode}
              content={content}
              setContent={setContent}
              language={language}
              setLanguage={setLanguage}
              handleUnifiedSend={handleUnifiedSend}
              setImageFile={setImageFile}
              imagePreviewUrl={imagePreviewUrl}
              setImagePreviewUrl={setImagePreviewUrl}
            />
          </div>
        </div>

        {showSearchSidebar && (
          <SearchSidebar
            totalCount={searchTotalCount}
            onSearch={handleSearch}
            searchKeyword={searchKeyword}
            searchResults={searchResults}
            isSearching={isSearching}
            errorMessage={errorMessage}
            hasNext={searchHasNext}
            onClose={() => setShowSearchSidebar(false)}
            onMessageClick={scrollToMessage}
          />
        )}

        {showCodeModal && selectedCodeMessage && (
          <CodeReviewModal
            message={selectedCodeMessage}
            roomId={roomId}
            currentUser={currentUser}
            onClose={() => { setShowCodeModal(false); setSelectedCodeMessage(null); }}
          />
        )}

        <RoomDeletedModal
          isOpen={!!deleteNotification}
          message={deleteNotification}
          onClose={() => setDeleteNotification(null)}
        />
      </div>
    </>
  );
};

export default ChatRoom;