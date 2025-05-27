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

  const { roomId, inviteCode } = useParams();
  const [messages, setMessages] = useState([]);
  const [content, setContent] = useState("");
  const [inputMode, setInputMode] = useState("TEXT");
  const [language, setLanguage] = useState("java");

  const [currentUser, setCurrentUser] = useState(null);
  const [contextMenuId, setContextMenuId] = useState(null);

  const [editMessageId, setEditMessageId] = useState(null); // 현재 수정 중인 메시지 ID
  const [editContent, setEditContent] = useState(""); // 수정 중인 내용

  const messagesEndRef = useRef(null);
  const navigate = useNavigate();           // ← 네비게이트 훅
  const location = useLocation();           // ← 현재 URL 가져오기

  const [roomName, setRoomName] = useState("로딩 중...");

  // 1. 방 이름 불러오기
  const fetchRoomInfo = async () => {
    try {
      const res = await axiosInstance.get(`/chat-rooms/check?inviteCode=${inviteCode}`, {
      });

      const roomData = res.data;
      setRoomName(roomData.roomName);
    } catch (error) {
      console.error('방 정보 요청 실패:', error);
      setRoomName(`채팅방 #${roomId}`); // 오류 시 기본값으로 표시
    }
  };

  // 2. 메시지 목록 불러오기
  const fetchMessages = async () => {
    try {
      const res = await axiosInstance.get(`/${roomId}/messages`);
      const data = res.data;

      // 날짜 유효성 검사
      const validatedData = data.map(msg => {
        const sendAt = new Date(msg.sendAt);
        const isInvalidDate = isNaN(sendAt.getTime()); // getTime이 NaN이면 잘못된 날짜

        if (!msg.sendAt || isInvalidDate) {
          return { ...msg, sendAt: new Date().toISOString() };
        }

        return msg;
      });

      //sendAt 기준으로 메시지 정렬
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

      const user = await res.json(); // { id, email, nickname, profileImg }
      setCurrentUser(user);
    } catch (error) {
      console.error('사용자 정보 요청 실패:', error);
    }
  };

  //웹소켓 연결 (현재 채팅방 구독)
  const stompClientRef = useWebSocket({
    roomId,
    onMessageReceived: (received)=> {
      //메세지 상태 업데이트
      setMessages(prev => {
        const updated=prev.some(m => m.messageId === received.messageId)
        ? prev.map(m => m.messageId === received.messageId ? received : m)
        : [...prev, received];

        // sendAt 기준으로 정렬
        return [...updated].sort((a, b) => new Date(a.sendAt) - new Date(b.sendAt));
      });
    }
  });

  //roomId가 변할 때 초기 데이터 불러오기
  useEffect(() => {
    if (!roomId) {
      console.error("No roomId available");
      navigate("/");
      return;
    }

    setMessages([]); // 이전 채팅방 메세지 제거

    fetchCurrentUser();
    fetchRoomInfo(); // 방 정보 가져오기 함수 호출 추가
    fetchMessages();

  },[roomId]);

  const handleLeaveRoom = async () => {
    try {
      await axiosInstance.delete(`/chat-rooms/${roomId}/leave`);

      // ChatRoom 컴포넌트의 상태 업데이트는 RoomHeader가 처리하도록 처리
      return { success: true };
    } catch (err) {
      const errorMsg =
        err.response?.data?.message ||
        err.message ||
        '나가기 실패';

      return { success: false, error: errorMsg };
    }
  };

  //메세지 검색
  const [showSearchSidebar, setShowSearchSidebar] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [errorMessage, setErrorMessage] = useState(null);

  const handleSearch = async (keyword, page = 0) => {
    // Check if roomId is defined before proceeding
    if (!roomId) {
      setErrorMessage('채팅방 ID가 유효하지 않습니다.');
      return;
    }
    setIsSearching(true);
    setShowSearchSidebar(true);
    setSearchKeyword(keyword);
    setErrorMessage(null); // 이전 에러 메시지 초기화

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
      const isInvalid = !msg.sendAt || isNaN(sendAt.getTime()); // NaN = Invalid Date

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
    // 연결이 끊긴 경우 재연결 시도
    if (!client.connected) {
      client.activate();
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

  //메세지 수정 요청
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

  //메세지 삭제 요청
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
