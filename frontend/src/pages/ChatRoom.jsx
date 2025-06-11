import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Sidebar from '../components/SideBar';
import Header from '../components/header';
import SearchSidebar from '../components/SearchSideBar';
import axiosInstance from '../components/api/axiosInstance';
import MessageInput from '../components/chatroom/MessageInput';
import MessageList from '../components/chatroom/MessageList';
import useWebSocket from '../components/common/useWebSocket';
import RoomHeader from '../components/chatroom/RoomHeader';
import RoomDeletedModal from '../components/modals/RoomDeletedModal';

const ChatRoom = () => {
  const { inviteCode } = useParams();
  const [messages, setMessages] = useState([]);
  const [content, setContent] = useState("");
  const [inputMode, setInputMode] = useState("TEXT");
  const [language, setLanguage] = useState("java");

  const [currentUser, setCurrentUser] = useState(null);
  const [contextMenuId, setContextMenuId] = useState(null);

  const [editMessageId, setEditMessageId] = useState(null);
  const [editContent, setEditContent] = useState("");

  // 무한 스크롤 관련 상태
  const [cursor, setCursor] = useState(null);
  const [hasMoreMessages, setHasMoreMessages] = useState(true);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [isInitialLoad, setIsInitialLoad] = useState(true); // 초기 로딩 상태

  const messagesEndRef = useRef(null);
  const messagesStartRef = useRef(null);
  const messageContainerRef = useRef(null);
  const prevScrollHeightRef = useRef(0); // 이전 스크롤 높이 저장
  const navigate = useNavigate();

  const [roomName, setRoomName] = useState("로딩 중...");
  const [roomId, setRoomId] = useState(null);
  const [isOwner, setIsOwner] = useState(false);
  const [roomData, setRoomData] = useState(null);
  const [deleteNotification, setDeleteNotification] = useState(null);

  // 초기화 상태를 하나로 통합하고 단계별로 관리
  const [initState, setInitState] = useState({
    isRoomValidated: false,
    isUserLoaded: false,
    isMessagesLoaded: false,
    hasError: false,
    errorMessage: ''
  });

  // 전체 로딩 상태를 계산으로 처리
  const isFullyLoaded = initState.isRoomValidated &&
    initState.isUserLoaded &&
    initState.isMessagesLoaded;

  // 맨 아래로 스크롤하는 함수 (새 메시지 수신용 및 초기 로드 완료 시 사용)
  const scrollToBottom = useCallback(() => {
    if (messageContainerRef.current) {
      const container = messageContainerRef.current;
      // scrollHeight가 변경될 때까지 기다렸다가 스크롤
      requestAnimationFrame(() => {
        container.scrollTop = container.scrollHeight;
      });
    }
  }, []);


  // 1. 방 정보 불러오기
  const fetchRoomInfo = useCallback(async () => {
    try {
      const res = await axiosInstance.get(`/chat-rooms/${inviteCode}`);
      const roomData = res.data;

      setRoomId(roomData.roomId);
      setRoomName(roomData.roomName);
      setRoomData(roomData);
      
      // 한 번에 상태 업데이트
      setInitState(prev => ({
        ...prev,
        isRoomValidated: true
      }));

      return roomData.roomId;
    } catch (error) {
      console.error('방 정보 조회 실패:', error);
      setInitState(prev => ({
        ...prev,
        hasError: true,
        errorMessage: '방 정보를 불러올 수 없습니다.'
      }));
      return null;
    }
  }, [inviteCode]);

  // 2. 메시지 목록 불러오기 (커서 기반)
  const fetchMessages = useCallback(async (cursorValue = null, isLoadMore = false) => {
    console.log('🔍 fetchMessages 호출:', { cursorValue, isLoadMore });

    setIsLoadingMessages(prev => {
      if (prev) {
        console.log('❌ 이미 로딩 중이므로 중단');
        return prev;
      }
      return true;
    });

    try {
      const params = {
        size: 30
      };

      if (cursorValue) {
        params.cursor = cursorValue;
      }

      console.log('📡 API 요청:', `/${roomId}/messages`, params);
      const res = await axiosInstance.get(`/${roomId}/messages`, { params });

      const data = res.data;

      const messageList = data.messages || [];

      if (!Array.isArray(messageList)) {
        console.error('❌ Message list is not an array:', messageList);
        return;
      }

      // 날짜 유효성 검사
      const validatedMessages = messageList.map(msg => {
        const sendAt = new Date(msg.sendAt);
        const isInvalidDate = isNaN(sendAt.getTime());

        if (!msg.sendAt || isInvalidDate) {
          return { ...msg, sendAt: new Date().toISOString() };
        }
        return msg;
      });

      // sendAt 기준으로 메시지 정렬 (오래된 순)
      const sortedMessages = validatedMessages.sort(

        (a, b) => new Date(a.sendAt).getTime() - new Date(b.sendAt).getTime()
      );

      if (isLoadMore) {
        // 이전 스크롤 높이를 저장
        prevScrollHeightRef.current = messageContainerRef.current ? messageContainerRef.current.scrollHeight : 0;

        // 이전 메시지들을 현재 메시지 앞에 추가
        setMessages(prev => {
          const existingIds = new Set(prev.map(msg => msg.messageId));
          const newMessages = sortedMessages.filter(msg => !existingIds.has(msg.messageId));
          return [...newMessages, ...prev];
        });
      } else {
        // 초기 로드
        setMessages(sortedMessages);

        // 초기 메시지 로드 완료 상태 업데이트
        setInitState(prev => ({
          ...prev,
          isMessagesLoaded: true
        }));
      }

      // 커서와 hasMore 상태 업데이트
      const nextCursor = data.nextCursor;
      setCursor(nextCursor);

      let hasMore = nextCursor !== null && messageList.length > 0;
      setHasMoreMessages(hasMore);

    } catch (error) {
      console.error('❌ Error fetching messages:', error);
    } finally {
      setIsLoadingMessages(false);
    }
  }, [roomId]);

  // 3. 로그인 유저 정보 가져오기
  const fetchCurrentUser = useCallback(async () => {
    try {
      const res = await fetch('http://localhost:8080/user/details', {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      });

      if (!res.ok) {
        throw new Error('로그인 정보를 가져오지 못했습니다.');
      }

      const user = await res.json();
      console.log("현재 사용자 정보:", user);
      setCurrentUser(user);

      // 사용자 정보 로드 완료 상태 업데이트
      setInitState(prev => ({
        ...prev,
        isUserLoaded: true
      }));

    } catch (error) {
      console.error('사용자 정보 요청 실패:', error);
      setInitState(prev => ({
        ...prev,
        isUserLoaded: true // 실패해도 진행
      }));
    }
  }, []);

  // 프로필 업데이트 처리 함수
  const handleProfileUpdate = useCallback((profileUpdateData) => {
    console.log('👤 프로필 업데이트 수신:', profileUpdateData);

    setMessages(prevMessages => {
      return prevMessages.map(msg => {
        if (msg.senderId === profileUpdateData.userId) {
          return {
            ...msg,
            senderName: profileUpdateData.nickname || msg.senderName,
            profileImageUrl: profileUpdateData.profileImageUrl || msg.profileImageUrl
          };
        }
        return msg;
      });
    });
  }, []);

  // 웹소켓 연결
  const stompClientRef = useWebSocket({
    roomId: initState.isRoomValidated ? roomId : null,
    onMessageReceived: (received) => {
      // 서버에서 받은 메시지만을 로컬 상태에 추가/업데이트
      setMessages(prev => {
        // 동일한 messageId를 가진 메시지가 이미 존재하면 업데이트하고, 없으면 추가
        const updated = prev.some(m => m.messageId === received.messageId)
          ? prev.map(m => m.messageId === received.messageId ? received : m)
          : [...prev, received];

        const sorted = [...updated].sort((a, b) => new Date(a.sendAt).getTime() - new Date(b.sendAt).getTime());

        // 새 메시지가 추가되었을 때만 맨 아래로 스크롤
        // 현재 스크롤 위치가 거의 맨 아래에 있다면 스크롤, 아니면 유지
        if (messageContainerRef.current) {
          const { scrollTop, clientHeight, scrollHeight } = messageContainerRef.current;
          const isNearBottom = (scrollTop + clientHeight) >= (scrollHeight - 100); // 100px 여유
          // 새 메시지가 나(currentUser)의 메시지이거나, 스크롤이 거의 맨 아래에 있을 때만 스크롤
          if (isNearBottom || received.senderId === currentUser?.userId) {
            scrollToBottom();
          }
        }
        return sorted;
      });
    },
    onProfileUpdate: handleProfileUpdate,

    onRoomDeleted: (eventMessage) => {  // 새로 추가
      setDeleteNotification(eventMessage.content);
    }

  });

  // 스크롤 위치 복원 함수 (무한 스크롤 시 사용)
  const restoreScrollPosition = useCallback(() => {
    if (!messageContainerRef.current) return;

    const container = messageContainerRef.current;
    const newScrollHeight = container.scrollHeight;
    const oldScrollHeight = prevScrollHeightRef.current;

    if (newScrollHeight > oldScrollHeight) {
      const scrollDiff = newScrollHeight - oldScrollHeight;
      // 이전 스크롤 위치에 새로 추가된 메시지 높이만큼 더해서 조정
      container.scrollTop = container.scrollTop + scrollDiff;
    }
  }, []);

  // 스크롤 이벤트 핸들러
  const handleScroll = useCallback(() => {
    if (!messageContainerRef.current || !roomId || !initState.isRoomValidated) return;

    const container = messageContainerRef.current;
    const { scrollTop, scrollHeight } = container;

    const isAtTop = scrollTop <= 150; // 상단에서 150px 이내
    const canLoadMore = !isLoadingMessages && hasMoreMessages && cursor;

    if (isAtTop && canLoadMore) {
      // 메시지를 불러오기 전에 현재 스크롤 높이를 저장
      prevScrollHeightRef.current = scrollHeight;
      fetchMessages(cursor, true);
    }
  }, [fetchMessages, hasMoreMessages, cursor, isLoadingMessages, roomId, initState.isRoomValidated]);

  // 스크롤 이벤트 리스너 등록
  useEffect(() => {
    const container = messageContainerRef.current;
    if (!container) return;

    let timeoutId = null;
    const debouncedHandleScroll = () => {
      if (timeoutId) clearTimeout(timeoutId);
      timeoutId = setTimeout(handleScroll, 150); // 디바운싱
    };

    container.addEventListener('scroll', debouncedHandleScroll, { passive: true });
    return () => {
      container.removeEventListener('scroll', debouncedHandleScroll);
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, [handleScroll]);

  // 초기화 로직 - 한 번에 모든 상태 리셋
  useEffect(() => {
    if (!inviteCode) {
      navigate("/error");
      return;
    }

    const initializeRoom = async () => {
      // 한 번에 모든 초기 상태 설정
      setInitState({
        isRoomValidated: false,
        isUserLoaded: false,
        isMessagesLoaded: false,
        hasError: false,
        errorMessage: ''
      });

      setMessages([]);
      setCursor(null);
      setHasMoreMessages(true);
      setIsInitialLoad(true); // 초기 로딩 상태를 true로 재설정
      setIsLoadingMessages(false);
      prevScrollHeightRef.current = 0; // 스크롤 높이 참조 초기화

      try {
        // 방 정보와 사용자 정보를 병렬로 로드
        const [fetchedRoomId] = await Promise.all([
          fetchRoomInfo(),
          fetchCurrentUser()
        ]);

        if (!fetchedRoomId) {
          navigate("/error");
          return;
        }

      } catch (error) {
        console.error('방 초기화 중 오류:', error);
        navigate("/error");
      }
    };

    initializeRoom();
  }, [inviteCode, navigate, fetchRoomInfo, fetchCurrentUser]);

  // roomId가 설정되고 방 정보가 유효하며, 초기 로드가 아직 안된 경우 메시지 로드
  useEffect(() => {
    // isInitialLoad가 true일 때만 fetchMessages를 호출하여 초기 로드를 제어
    if (roomId && initState.isRoomValidated && isInitialLoad) {
      fetchMessages();
      setIsInitialLoad(false); // 초기 로드 후 바로 false로 설정
    }
  }, [roomId, initState.isRoomValidated, isInitialLoad, fetchMessages]);

  // 메시지 로드 후 스크롤 위치 조정
  useEffect(() => {
    // 초기 로딩이 완료되고 메시지가 있을 때만 스크롤 조정
    if (!isInitialLoad && messages.length > 0) {
      if (isLoadingMessages) {
        // 메시지 추가 로드 중일 때 스크롤 위치 복원
        // DOM 업데이트가 완전히 반영되도록 다음 프레임에서 실행
        requestAnimationFrame(() => {
          requestAnimationFrame(() => { // 한 번 더 requestAnimationFrame을 사용하여 확실히 DOM 반영 후
            restoreScrollPosition();
          });
        });
      } else {
        // 초기 로드 완료 시 (isInitialLoad가 false로 변하는 시점) 맨 아래로 스크롤
        // 이 조건은 컴포넌트 마운트 후 최초 메시지 로드 시에만 적용됩니다.
        if (prevScrollHeightRef.current === 0 && messageContainerRef.current) {
          // prevScrollHeightRef가 0이라는 것은 초기 로드이거나, 메시지가 없었다가 처음 생겼다는 의미
          // (prevScrollHeightRef는 isLoadMore일 때만 업데이트되므로, 이 조건은 첫 로드에만 해당)
          scrollToBottom();
        }
        // 새 메시지 수신으로 인한 스크롤은 useWebSocket 내부 onMessageReceived에서 처리
      }
    }
  }, [messages, isInitialLoad, restoreScrollPosition, isLoadingMessages, scrollToBottom]);


  useEffect(() => {
    if (currentUser && roomData && roomData.ownerId) {
      setIsOwner(currentUser.id === roomData.ownerId);
    }
  }, [currentUser, roomData]);

  const handleLeaveRoom = async () => {
    try {
      await axiosInstance.delete(`/chat-rooms/${roomId}/leave`);

      navigate(`/`);

      return { success: true };
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message || '나가기 실패';
      return { success: false, error: errorMsg };
    }
  };

  // 채팅방 삭제 함수
  const handleDeleteRoom = async () => {
    try {
      await axiosInstance.delete(`/chat-rooms/${roomId}`);
      return { success: true };
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message || '채팅방 삭제에 실패했습니다';
      return { success: false, error: errorMsg };
    }
  };

  // 메시지 검색 관련 상태
  const [showSearchSidebar, setShowSearchSidebar] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [errorMessage, setErrorMessage] = useState(null);

  const handleSearch = async (keyword, page = 0) => {
    if (!roomId || !initState.isRoomValidated) {
      setErrorMessage('채팅방이 아직 로딩 중입니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    setIsSearching(true);
    setShowSearchSidebar(true);
    setSearchKeyword(keyword);
    setErrorMessage(null);

    try {
      const response = await axiosInstance.get(`/chat/search/${roomId}`, {
        params: { keyword, page, size: 10 }
      });

      const data = response.data;
      const validatedResults = (data.content || []).map(msg => {
        const sendAt = new Date(msg.sendAt);
        const isInvalid = !msg.sendAt || isNaN(sendAt.getTime());

        return isInvalid ? { ...msg, sendAt: new Date().toISOString() } : msg;

      });

      setSearchResults(validatedResults);
      setCurrentPage(data.pageable?.pageNumber || 0);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      console.error('Search error:', err);
      setErrorMessage(err.response?.data?.message || '검색 중 오류가 발생했습니다.');
    } finally {
      setIsSearching(false);
    }
  };

  // 검색 결과로 스크롤 이동
  const scrollToMessage = (messageId) => {
    const messageElement = document.getElementById(`message-${messageId}`);
    if (messageElement) {
      messageElement.scrollIntoView({
        behavior: 'smooth',
        block: 'center'
      });

      messageElement.style.backgroundColor = '#e8f4fd';
      messageElement.style.borderRadius = '6px';
      messageElement.style.transition = 'all 0.3s ease';
      setTimeout(() => {
        messageElement.style.backgroundColor = '';
        messageElement.style.borderRadius = '';
      }, 2000);
    }
  };

  const [imageFile, setImageFile] = useState(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState(null);

  // 통합 전송 핸들러
  const handleUnifiedSend = async () => {
    if (inputMode === 'IMAGE') {
      if (!imageFile) {
        alert("이미지를 선택하세요.");
        return;
      }

      try {
        const formData = new FormData();
        formData.append('image', imageFile);

        const response = await axiosInstance.post('/send-image', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });

        const imageId = response.data;

        // 이미지 메시지는 서버에서 완전한 메시지를 받아올 때까지 화면에 표시하지 않음
        sendMessage({
          type: 'IMAGE',
          content: '', // 이미지 메시지는 content가 비어있을 수 있습니다.
          imageFileId: imageId
        });

        setImageFile(null);
        setImagePreviewUrl(null);
      } catch (err) {
        console.error("이미지 전송 실패: ", err);
        alert("이미지 전송에 실패했습니다. 다시 시도해주세요.");
      }
    } else {
      // 텍스트, 코드 메시지 전송 (임시 메시지 로직 제거)
      sendMessage();
    }
  };

  // 메시지 전송 (임시 메시지 로직 제거)
  const sendMessage = (overrideMessage = null) => {
    const client = stompClientRef.current;

    if (!roomId || !initState.isRoomValidated) {

      alert('채팅방 정보를 로딩 중입니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    if (!client || !client.connected) {
      alert('⚠️ 서버와 연결이 끊어졌습니다. 재연결을 시도합니다.');
      return;
    }

    if (!currentUser) {
      console.warn('Current user not loaded yet. Cannot send message.');
      alert('사용자 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    let baseMessage = {
      content: content,
      type: inputMode,
      sendAt: new Date().toISOString(),
      ...(inputMode === 'CODE' && { language })
    };

    const messageToSend = overrideMessage ? { ...baseMessage, ...overrideMessage } : baseMessage;

    const trimmed = String(messageToSend.content).trim();
    if (messageToSend.type !== 'IMAGE' && trimmed === '') {
      return;
    }

    // 서버에 보낼 메시지를 즉시 로컬 상태에 추가하지 않고,
    // 서버에서 다시 브로드캐스트된 메시지만을 onMessageReceived에서 처리
    client.publish({
      destination: `/chat/send-message/${roomId}`,
      body: JSON.stringify(messageToSend)
    });

    // 입력 필드 초기화
    setContent('');
    setInputMode('TEXT');
  };

  // 메시지 수정
  const handleEditMessage = (messageId) => {
    const client = stompClientRef.current;
    if (!client || !client.connected) {
      alert('서버에 연결되어 있지 않습니다.');
      return;
    }

    const editPayload = {
      messageId: messageId,
      content: editContent
    };

    client.publish({
      destination: `/chat/edit-message/${roomId}`,
      body: JSON.stringify(editPayload)
    });

    setEditMessageId(null);
    setEditContent('');
  };

  // 메시지 삭제
  const handleDeleteMessage = (messageId) => {
    const client = stompClientRef.current;
    if (!client || !client.connected) {
      alert('서버에 연결되어 있지 않습니다.');
      return;
    }

    client.publish({
      destination: `/chat/delete-message/${roomId}`,
      body: messageId
    });

    setContextMenuId(null);
  };

  // 에러 상태 처리
  if (initState.hasError) {
    return (
      <div style={{
        backgroundColor: '#f5f7fa',
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif'
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ color: '#dc3545', marginBottom: '16px' }}>
            {initState.errorMessage}
          </div>
          <button
            onClick={() => navigate('/')}
            style={{
              padding: '8px 16px',
              backgroundColor: '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            홈으로 돌아가기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        backgroundColor: '#f5f7fa',
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
        boxSizing: 'border-box',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif'
      }}>

      <Header />

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        <Sidebar />

        <div style={{
          flex: 1,
          width: '700px',
          backgroundColor: '#ffffff',
          borderRadius: '12px',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.08)',
          overflow: 'hidden',
          // 로딩 중에도 레이아웃 유지하되 투명도로 처리
          opacity: isFullyLoaded ? 1 : 0.7,
          transition: 'opacity 0.3s ease'
        }}>

          <RoomHeader
            roomName={roomName}
            inviteCode={inviteCode}
            onSearch={handleSearch} 
            onLeaveRoom={handleLeaveRoom}
            onDeleteRoom={handleDeleteRoom}
            isOwner={isOwner}

          />

          <div
            ref={messageContainerRef}
            style={{
              flex: 1,
              overflowY: 'auto',
              padding: '20px 24px',
              backgroundColor: '#fff',
              minHeight: 0,
              position: 'relative'
            }}>

            {/* 초기 로딩 상태 표시 (깜빡임 없이) */}
            {!isFullyLoaded && (
              <div style={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                textAlign: 'center',
                color: '#666',
                zIndex: 10
              }}>
                <div style={{ fontSize: '14px' }}>채팅방을 불러오는 중...</div>
                <div style={{
                  width: '40px',
                  height: '40px',
                  border: '3px solid #f0f0f0',
                  borderTop: '3px solid #007bff',
                  borderRadius: '50%',
                  animation: 'spin 1s linear infinite',
                  margin: '12px auto'
                }} />
              </div>
            )}

            {/* 무한 스크롤 로딩 */}
            {isLoadingMessages && hasMoreMessages && (
              <div style={{
                position: 'sticky',
                top: 0,
                textAlign: 'center',
                padding: '8px 16px',
                color: '#666',
                fontSize: '13px',
                backgroundColor: '#f8f9fa',
                borderRadius: '16px',
                margin: '0 auto 16px auto',
                width: 'fit-content',
                border: '1px solid #e9ecef',
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
              }}>
                ⏳ 메시지 로딩 중...
              </div>
            )}

            {/* 메시지 끝 표시 */}
            {!hasMoreMessages && messages.length > 0 && !isInitialLoad && (
              <div style={{
                textAlign: 'center',
                padding: '8px 16px',
                color: '#999',
                fontSize: '12px',
                backgroundColor: '#f8f9fa',
                borderRadius: '16px',
                margin: '0 auto 16px auto',
                width: 'fit-content'
              }}>
                💬 모든 메시지를 불러왔습니다
              </div>
            )}

            <div ref={messagesStartRef} />

            {/* 메시지 목록은 항상 렌더링 (비어있어도) */}
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
            />
            <div ref={messagesEndRef} />
          </div>

          {/* 메시지 입력 */}
          <div style={{
            backgroundColor: '#fbfbfd',
            borderTop: '1px solid #eaedf0',
            padding: '16px 24px',
            display: 'flex',
            flexDirection: 'column',
            // 로딩 중에는 입력 비활성화
            pointerEvents: isFullyLoaded ? 'auto' : 'none'
          }}>
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

        {/* 검색 사이드바 */}
        {showSearchSidebar && (
          <SearchSidebar
            searchKeyword={searchKeyword}
            searchResults={searchResults}
            isSearching={isSearching}
            errorMessage={errorMessage}
            currentPage={currentPage}
            totalPages={totalPages}
            totalElements={totalElements}
            onClose={() => setShowSearchSidebar(false)}
            onPageChange={(page) => handleSearch(searchKeyword, page)}
            onMessageClick={scrollToMessage}
          />
        )}

        <RoomDeletedModal
          isOpen={!!deleteNotification}
          message={deleteNotification}
          onClose={() => setDeleteNotification(null)}
        />

      </div>
    </div>
  );
};

export default ChatRoom;