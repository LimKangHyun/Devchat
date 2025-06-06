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
  const [isInitialLoad, setIsInitialLoad] = useState(true);

  const messagesEndRef = useRef(null);
  const messagesStartRef = useRef(null);
  const messageContainerRef = useRef(null);
  const prevScrollHeightRef = useRef(0);
  const navigate = useNavigate();

  const [roomName, setRoomName] = useState("로딩 중...");
  const [roomId, setRoomId] = useState(null);
  
  // 초기화 상태 관리
  const [isRoomValidated, setIsRoomValidated] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);

  // 맨 아래로 스크롤하는 함수 (새 메시지 수신용)
  const scrollToBottom = useCallback(() => {
    if (messageContainerRef.current) {
      const container = messageContainerRef.current;
      container.scrollTop = container.scrollHeight;
    }
  }, []);

  // 1. 방 정보 불러오기
  const fetchRoomInfo = useCallback(async () => {
    try {
      const res = await axiosInstance.get(`/chat-rooms/${inviteCode}`);
      const roomData = res.data;

      setRoomId(roomData.roomId);
      setRoomName(roomData.roomName);
      return roomData.roomId;
    } catch (error) {
      console.error('방 정보 조회 실패:', error);
      navigate(`/error`);
      return null;
    }
  }, [inviteCode, navigate]);

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
      console.log('📨 API 응답:', {
        messagesCount: data.messages?.length,
        nextCursor: data.nextCursor,
        hasNext: data.nextCursor !== null,
        fullResponse: data
      });

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
        // 이전 메시지들을 현재 메시지 앞에 추가
        setMessages(prev => {
          // 중복 제거를 위한 Set 사용
          const existingIds = new Set(prev.map(msg => msg.messageId));
          const newMessages = sortedMessages.filter(msg => !existingIds.has(msg.messageId));
          console.log('➕ 새로 추가할 메시지:', newMessages.length, '개');
          return [...newMessages, ...prev];
        });
      } else {
        // 초기 로드
        setMessages(sortedMessages);
        console.log('🆕 초기 메시지 설정:', sortedMessages.length, '개');
      }

      // 커서와 hasMore 상태 업데이트
      const nextCursor = data.nextCursor;
      setCursor(nextCursor);
      
      let hasMore = true;
      
      if (nextCursor === null) {
        hasMore = false;
        console.log('🔄 hasMore = false (nextCursor가 null)');
      } else if (messageList.length === 0) {
        hasMore = false;
        console.log('🔄 hasMore = false (받은 메시지가 0개)');
      } else {
        hasMore = true;
        console.log('🔄 hasMore = true (nextCursor 존재하고 메시지 있음)');
      }
      
      setHasMoreMessages(hasMore);
      
      console.log('🔄 상태 업데이트:', {
        nextCursor,
        hasMore,
        receivedCount: messageList.length,
        requestedSize: params.size
      });
      
    } catch (error) {
      console.error('❌ Error fetching messages:', error);
      console.log('❌ 에러 발생했지만 hasMoreMessages 상태 유지');
    } finally {
      setIsLoadingMessages(false);
      setIsInitialLoad(false);
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
      setCurrentUser(user);
    } catch (error) {
      console.error('사용자 정보 요청 실패:', error);
    }
  }, []);

  // 프로필 업데이트 처리 함수
  const handleProfileUpdate = useCallback((profileUpdateData) => {
    console.log('👤 프로필 업데이트 수신:', profileUpdateData);
    
    setMessages(prevMessages => {
      return prevMessages.map(msg => {
        // 해당 사용자의 메시지인 경우 프로필 정보 업데이트
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

  // 웹소켓 연결 (방이 검증된 후에만 연결 + 프로필 업데이트 구독)
  const stompClientRef = useWebSocket({
    roomId: isRoomValidated ? roomId : null, // 방이 검증된 후에만 roomId 전달
    onMessageReceived: (received) => {
      setMessages(prev => {
        const updated = prev.some(m => m.messageId === received.messageId)
          ? prev.map(m => m.messageId === received.messageId ? received : m)
          : [...prev, received];

        const sorted = [...updated].sort((a, b) => new Date(a.sendAt) - new Date(b.sendAt));
        
        // 새 메시지가 추가되면 스크롤을 맨 아래로 (즉시)
        requestAnimationFrame(() => {
          scrollToBottom();
        });
        
        return sorted;
      });
    },
    onProfileUpdate: handleProfileUpdate // 프로필 업데이트 콜백 추가
  });

  // 개선된 스크롤 위치 복원 함수
  const restoreScrollPosition = useCallback(() => {
    if (!messageContainerRef.current || isInitialLoad) return;
    
    const container = messageContainerRef.current;
    const newScrollHeight = container.scrollHeight;
    const oldScrollHeight = prevScrollHeightRef.current;
    
    console.log('📏 스크롤 높이 비교:', {
      old: oldScrollHeight,
      new: newScrollHeight,
      diff: newScrollHeight - oldScrollHeight
    });
    
    // 새로운 메시지가 추가되어 스크롤 높이가 증가한 경우
    if (newScrollHeight > oldScrollHeight) {
      const scrollDiff = newScrollHeight - oldScrollHeight;
      const currentScrollTop = container.scrollTop;
      const newScrollTop = currentScrollTop + scrollDiff;
      
      console.log('📍 스크롤 위치 조정:', {
        currentScrollTop,
        scrollDiff,
        newScrollTop
      });
      
      // 즉시 스크롤 위치 조정
      container.scrollTop = newScrollTop;
    }
    
    // 현재 스크롤 높이를 다음을 위해 저장
    prevScrollHeightRef.current = newScrollHeight;
  }, [isInitialLoad]);

  // 개선된 스크롤 이벤트 핸들러
  const handleScroll = useCallback(() => {
    if (!messageContainerRef.current || !roomId || !isRoomValidated) return;
    
    const container = messageContainerRef.current;
    const { scrollTop, scrollHeight } = container;
    
    const isAtTop = scrollTop <= 150;
    const currentHasMore = hasMoreMessages;
    const currentCursor = cursor;
    const currentIsLoading = isLoadingMessages;
    
    const canLoadMore = !currentIsLoading && currentHasMore && currentCursor;
    
    if (isAtTop && canLoadMore) {
      console.log('🚀 무한 스크롤 트리거! 이전 메시지 로드 시작');
      
      // ✅ 현재 스크롤 높이를 저장 (메시지 로드 전)
      prevScrollHeightRef.current = scrollHeight;
      
      fetchMessages(currentCursor, true);
    }
  }, [fetchMessages, hasMoreMessages, cursor, isLoadingMessages, roomId, isRoomValidated]);

  // 스크롤 이벤트 리스너 등록 (디바운스 적용)
  useEffect(() => {
    const container = messageContainerRef.current;
    if (!container) return;

    let timeoutId = null;
    const debouncedHandleScroll = () => {
      if (timeoutId) clearTimeout(timeoutId);
      timeoutId = setTimeout(handleScroll, 150); // 150ms 디바운스
    };

    container.addEventListener('scroll', debouncedHandleScroll, { passive: true });
    return () => {
      container.removeEventListener('scroll', debouncedHandleScroll);
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, [handleScroll]);

  // 초기화 로직 
  useEffect(() => {
    if (!inviteCode) {
      console.error("No inviteCode available");
      navigate("/error");
      return;
    }

    const initializeRoom = async () => {
      setIsInitializing(true);
      setIsRoomValidated(false);
      setMessages([]);
      
      // 무한 스크롤 상태 초기화
      setCursor(null);
      setHasMoreMessages(true);
      setIsInitialLoad(true);
      setIsLoadingMessages(false);
      
      try {
        // 1단계: 방 정보 검증 및 로딩
        const fetchedRoomId = await fetchRoomInfo();
        
        if (!fetchedRoomId) {
          return;
        }

        // 2단계: 방 검증 완료 표시
        setIsRoomValidated(true);
        
        // 3단계: 사용자 정보 로딩과 메시지 로딩을 순차적으로 처리
        await fetchCurrentUser();
        
        // ✅ roomId가 설정된 후에 메시지를 로드하도록 수정
        // fetchMessages는 roomId 의존성이 있으므로 별도로 처리
        
      } catch (error) {
        console.error('방 초기화 중 오류:', error);
        navigate("/error");
      } finally {
        setIsInitializing(false);
      }
    };

    initializeRoom();
  }, [inviteCode, navigate, fetchRoomInfo, fetchCurrentUser]);

  useEffect(() => {
    if (roomId && isRoomValidated && isInitialLoad) {
      console.log('🚀 roomId 설정 완료, 초기 메시지 로드 시작:', roomId);
      fetchMessages();
    }
  }, [roomId, isRoomValidated, isInitialLoad, fetchMessages]);

  // 초기 메시지 로드 완료 후 최신 메시지로 스크롤
  useEffect(() => {
    if (!isInitialLoad && messages.length > 0) {
      // DOM 업데이트가 완전히 완료된 후 스크롤 위치 복원
      // requestAnimationFrame을 두 번 사용해서 더 확실하게 DOM 업데이트를 기다림
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          restoreScrollPosition();
        });
      });
    }
  }, [messages, isInitialLoad, restoreScrollPosition]);

  // 메시지 로드 후 스크롤 위치 조정 (무한 스크롤용)
  useEffect(() => {
    if (!isInitialLoad && messages.length > 0) {
      // 이전 메시지 로드 시 스크롤 위치 복원
      requestAnimationFrame(() => {
        restoreScrollPosition();
      });
    }
  }, [messages, isInitialLoad, restoreScrollPosition]);

  const handleLeaveRoom = async () => {
    try {
      await axiosInstance.delete(`/chat-rooms/${roomId}/leave`);
      return { success: true };
    } catch (err) {
      const errorMsg =
        err.response?.data?.message ||
        err.message ||
        '나가기 실패';

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
    // roomId 검증 - 더 명확한 에러 처리
    if (!roomId || !isRoomValidated) {
      setErrorMessage('채팅방이 아직 로딩 중입니다. 잠시 후 다시 시도해주세요.');
      return;
    }
    
    setIsSearching(true);
    setShowSearchSidebar(true);
    setSearchKeyword(keyword);
    setErrorMessage(null);

    try {
      const response = await axiosInstance.get(`/chat/search/${roomId}`, {
        params: {
          keyword,
          page,
          size: 10
        }
      });

      const data = response.data;
      const validatedResults = (data.content || []).map(msg => {
        const sendAt = new Date(msg.sendAt);
        const isInvalid = !msg.sendAt || isNaN(sendAt.getTime());

        return isInvalid
          ? { ...msg, sendAt: new Date().toISOString() }
          : msg;
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
      
      // 깔끔한 하이라이트
      messageElement.style.backgroundColor = '#e8f4fd';
      messageElement.style.borderRadius = '6px';
      messageElement.style.transition = 'all 0.3s ease';
      
      // 2초 후 제거
      setTimeout(() => {
        messageElement.style.backgroundColor = '';
        messageElement.style.borderRadius = '';
      }, 2000);
    }
  };

  const [imageFile, setImageFile] = useState(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState(null);

  // 전송 버튼 클릭 시 호출되는 공통 핸들러 함수 (이미지 업로드 고려)
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
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        });

        const imageId = response.data;
        sendMessage({
          type: 'IMAGE',
          content: '',
          imageFileId: imageId
        });

        setImageFile(null);
        setImagePreviewUrl(null);
      } catch (err) {
        console.error("이미지 전송 실패: ", err);
      }
    } else {
      sendMessage();
    }
  };

  // 메시지 전송 (텍스트/코드/이미지 모두 처리)
  const sendMessage = (overrideMessage = null) => {
    const client = stompClientRef.current;
    
    // 방 검증 상태 확인
    if (!roomId || !isRoomValidated) {
      alert('채팅방 정보를 로딩 중입니다. 잠시 후 다시 시도해주세요.');
      return;
    }
    
    if (!client || !client.connected) {
      alert('⚠️ 서버와 연결이 끊어졌습니다. 재연결을 시도합니다.');
      return;
    }

    // 기본 메시지 구조
    let baseMessage = {
      content: content,
      type: inputMode,
      sendAt: new Date().toISOString(),
      ...(inputMode === 'CODE' && { language })
    };

    // overrideMessage가 있으면 병합 (예: 이미지 메시지 함께 전송)
    const message = overrideMessage ? { ...baseMessage, ...overrideMessage } : baseMessage;

    // 메시지 비어있는 경우 전송 방지
    const trimmed = String(message.content).trim();
    if (message.type !== 'IMAGE' && trimmed === '') {
      return;
    }

    client.publish({
      destination: `/chat/send-message/${roomId}`,
      body: JSON.stringify(message)
    });

    setContent('');
    setInputMode('TEXT');
  };

  // 메시지 수정 요청
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

    // 수정 모드 종료
    setEditMessageId(null);
    setEditContent('');
  };

  // 메시지 삭제 요청
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

    setContextMenuId(null); // 메뉴 닫기
  };

  // 로딩 상태 표시
  if (isInitializing) {
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
          <div>채팅방을 불러오는 중...</div>
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

      {/* Top Bar */}
      <Header></Header>

      {/* 본문 전체 영역 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        <Sidebar />

        {/* Chat area */}
        <div style={{
          flex: 1,
          width: '700px',
          backgroundColor: '#ffffff',
          borderRadius: '12px',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.08)',
          overflow: 'hidden'
        }}>

          {/* 채팅방 헤더 - 채팅방 이름, 초대 코드 복사 버튼, 나가기 버튼, 메세지 검색 창 */}
          <RoomHeader
            roomName={roomName}
            inviteCode={inviteCode}
            onSearch={handleSearch} // 메시지 검색 api 요청 함수
            onLeaveRoom={handleLeaveRoom} // 방 나가기 api 요청 함수
          />

          {/* 메시지 목록 */}
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
            
            {/* 개선된 로딩 인디케이터 */}
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
            
            {/* 더 이상 메시지가 없을 때 표시 */}
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

          {/* 메시지 입력 폼 */}
          <div style={{
            backgroundColor: '#fbfbfd',
            borderTop: '1px solid #eaedf0',
            padding: '16px 24px',
            display: 'flex',
            flexDirection: 'column'
          }}>
            <MessageInput
              inputMode={inputMode}
              setInputMode={setInputMode}
              content={content}
              setContent={setContent}
              language={language}
              setLanguage={setLanguage}
              sendMessage={sendMessage}
              handleUnifiedSend={handleUnifiedSend}
              setImageFile={setImageFile}
              imagePreviewUrl={imagePreviewUrl}
              setImagePreviewUrl={setImagePreviewUrl}
            />
          </div>
        </div>

        {/* 메세지 검색 바 */}
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
      </div>
    </div>
  );
};

export default ChatRoom;