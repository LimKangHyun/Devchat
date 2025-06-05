import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
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

  const messagesEndRef = useRef(null);
  const navigate = useNavigate();

  const [roomName, setRoomName] = useState("로딩 중...");
  const [roomId, setRoomId] = useState(null);
  
  // 초기화 상태 관리
  const [isRoomValidated, setIsRoomValidated] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);

  // 1. 방 정보 불러오기
  const fetchRoomInfo = async () => {
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
  };

  // 2. 메시지 목록 불러오기
  const fetchMessages = async (roomId) => {
    try {
      const res = await axiosInstance.get(`/${roomId}/messages`); 
      const data = res.data;

      // 날짜 유효성 검사
      const validatedData = data.map(msg => {
        const sendAt = new Date(msg.sendAt);
        const isInvalidDate = isNaN(sendAt.getTime());

        if (!msg.sendAt || isInvalidDate) {
          return { ...msg, sendAt: new Date().toISOString() };
        }

        return msg;
      });

      // sendAt 기준으로 메시지 정렬
      const sortedData = validatedData.sort(
          (a, b) => new Date(a.sendAt).getTime() - new Date(b.sendAt).getTime()
      );

      setMessages(sortedData);
    } catch (error) {
      console.error('Error fetching messages:', error);
    }
  };

  // 3. 로그인 유저 정보 가져오기
  const fetchCurrentUser = async () => {
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
  };

  // 웹소켓 연결 (roomId가 검증된 후에만 연결)
  const stompClientRef = useWebSocket({
    roomId: isRoomValidated ? roomId : null, // 방이 검증된 후에만 roomId 전달
    onMessageReceived: (received) => {
      setMessages(prev => {
        const updated = prev.some(m => m.messageId === received.messageId)
        ? prev.map(m => m.messageId === received.messageId ? received : m)
        : [...prev, received];

        // sendAt 기준으로 정렬
        return [...updated].sort((a, b) => new Date(a.sendAt) - new Date(b.sendAt));
      });
    }
  });

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
      setMessages([]); // 이전 채팅방 메시지 제거
      
      try {
        // 1단계: 방 정보 검증 및 로딩
        const fetchedRoomId = await fetchRoomInfo();
        
        if (!fetchedRoomId) {
          // 방 정보 로딩 실패 시 여기서 종료 (navigate는 fetchRoomInfo에서 처리)
          return;
        }

        // 2단계: 방 검증 완료 표시 (이제 웹소켓 연결 가능)
        setIsRoomValidated(true);
        
        // 3단계: 병렬로 사용자 정보와 메시지 로딩
        await Promise.all([
          fetchCurrentUser(),
          fetchMessages(fetchedRoomId)
        ]);

      } catch (error) {
        console.error('방 초기화 중 오류:', error);
        navigate("/error");
      } finally {
        setIsInitializing(false);
      }
    };

    initializeRoom();
  }, [inviteCode]);

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

  // 메시지가 업데이트될 때마다 아래로 스크롤
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "instant" });
  }, [messages]);

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
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '20px 24px',
            backgroundColor: '#fff',
            minHeight: 0
          }}>
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