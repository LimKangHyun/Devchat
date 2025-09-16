"use client"

import { useState, useEffect, useRef } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { FaRegCommentDots, FaInfoCircle, FaComments, FaPlus, FaUsers } from "react-icons/fa"

import CreateRoomModal from "./modals/CreateRoomModal"
import JoinRoomModal from "./modals/JoinRoomModal"
import RoomInfoModal from "./modals/RoomInfoModal"
import Toast from "./common/Toast"
import FriendsSidebar from "./FriendsSidebar" // Import the new component
import axiosInstance from "./api/axiosInstance"
import { useAlarm } from '../context/AlarmContext';
import useWebSocket from './common/useWebSocket';

const Sidebar = ({ onStartChat }) => {
  const navigate = useNavigate()
  const { inviteCode } = useParams()
  const sidebarRef = useRef(null)

  // Tab state - ADD THIS
  const [activeTab, setActiveTab] = useState("chat")
  const [loading, setLoading] = useState(true)

  // 모달 상태
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showJoinModal, setShowJoinModal] = useState(false)
  const [showMembersModal, setShowMembersModal] = useState(false)
  const [selectedRoom, setSelectedRoom] = useState(null)

  // 토스트 알림 상태
  const [showToast, setShowToast] = useState(false)
  const [toastMessage, setToastMessage] = useState("")

  // 현재 사용자 정보
  const [currentUser, setCurrentUser] = useState(null)

  // 읽지 않은 메시지 상태 (roomId: boolean)
  const [unreadMessages, setUnreadMessages] = useState({})
  const [roomId, setRoomId] = useState(null)

  const { getAlarmStatus, alarmStatusMap={} } = useAlarm(); // 전역 알림 상태 접근
  const [alarmRooms, setAlarmRooms] = useState([])

  // 사이드바 메시지 처리 콜백
  const handleSidebarMessage = (roomUniqueId, message) => {

    // 현재 있는 채팅방이 아닌 경우에만 알림 표시
    if (Number(roomId) !== Number(roomUniqueId)) {
      setUnreadMessages((prev) => ({
        ...prev,
        [roomUniqueId]: true
      }));

      const room=alarmRooms.find(r=> r.uniqueId===roomUniqueId);

      // ✅ 알림 채팅방 최상단으로 올리기
      if (room) {
        setAlarmRooms(prev => {
          const updated = prev.filter(r => r.uniqueId !== roomUniqueId);
          return [room, ...updated];
        });
      }

      // 알림 허용 상태 체크
      const isAlarmEnabled = getAlarmStatus(roomUniqueId);
      if (isAlarmEnabled === false) {
        console.log(`🔕 알림 비활성화된 방(${roomUniqueId}) → 브라우저 알림 생략`);
        return;
      }

      // header.jsx에게 "브라우저 알림 요청" 전역 이벤트 발행
      console.log(`chat_message 전역 이벤트 발행`);

      window.dispatchEvent(
        new CustomEvent("chat:notify", {
          detail: {
            senderNickname: message.senderName,
            body: message.content,
            senderImg: message.profileImageUrl,
            url: room?.inviteCode ? `/chat/${room.inviteCode}` : undefined, // 클릭 시 해당 채팅방으로 이동
            tag: `room-${Date.now()}`, // 고유 태그 부여(사용 안함)
            silent: false, // 소리 재생 여부 (true면 소리 재생 x)
            roomName: room?.roomName || `Room ${roomUniqueId}`
          },
        })
      );

    }
  };

  // useWebSocket 훅 사용 - 사이드바용 구독만 활성화
  const stompClientRef = useWebSocket({
    chatRooms: alarmRooms, // 채팅방 목록 전달
    currentRoomId: roomId, // 현재 활성화된 채팅방 ID
    onSidebarMessage: handleSidebarMessage, // 사이드바 메시지 처리 콜백
    onMessageReceived: () => {}, // 메인 메시지 처리는 비활성화
    roomId: null,
  });

  useEffect(() => {
    // Only fetch chat rooms when chat tab is active
    if (activeTab === "chat") {
      fetchAllRooms();
      fetchCurrentUser()
    }
  }, [inviteCode, activeTab, alarmStatusMap]) // Add activeTab to dependencies

  // 현재 채팅방이 변경될 때 해당 방의 읽지 않은 메시지 상태 제거
  useEffect(() => {
    if (roomId) {
      setUnreadMessages((prev) => {
        const updated = { ...prev }
        delete updated[roomId]
        return updated
      })
    }
  }, [roomId, alarmStatusMap]);

  useEffect(() => {
    if (!inviteCode) {
      setRoomId(null);
      return;
    }

    // chatRooms가 비어 있으면 기다렸다가 다시 실행 (최초 로딩 대응)
    const foundRoom = alarmRooms.find(room => room.inviteCode === inviteCode);

    if (foundRoom) {
      setRoomId(foundRoom.uniqueId);
    } else {
      setRoomId(null); // 또는 유지
    }
  }, [inviteCode, alarmRooms, alarmStatusMap]);

  const fetchCurrentUser = async () => {
    try {
      const res = await axiosInstance.get("/user/details")
      setCurrentUser(res.data)
    } catch (err) {
      console.error("사용자 정보 로딩 오류:", err)
    }
  }

  const fetchAllRooms = async () => {
    try {
      setLoading(true);
      const res = await axiosInstance.get('/chat-rooms/all');
      const rooms = res.data.map(room => ({
        ...room,
        uniqueId: room.roomId || room.id,
        alarmEnabled: room.alarmEnabled ?? true,
        roomName: room.roomName || `Room ${room.roomId || room.id}`,
        inviteCode: room.inviteCode,
      })).filter(room => room.uniqueId);

      setAlarmRooms(rooms);
    } catch (e) {
      console.error('알림용 채팅방 목록 로딩 실패', e);
    } finally {
      setLoading(false)
    }
  }

  const navigateToRoom = (id, inviteCode) => {
    if (id) {
      // 해당 방의 읽지 않은 메시지 상태 제거
      setUnreadMessages((prev) => {
        const updated = { ...prev }
        delete updated[id]
        return updated
      })
      navigate(`/chat/${inviteCode}`)
    }
  };

  // 방 상세 정보 가져오기
  const fetchRoomDetails = async (roomId) => {
    try {
      // 방 상세 정보를 가져오는 API가 있다면 사용
      // 현재 예시에서는 이런 API가 명시되어 있지 않으므로 기존 목록에서 찾아서 사용
      const existingRoom = alarmRooms.find(room => Number(room.uniqueId) === Number(roomId));

      if (!existingRoom) return null

      // 방장 정보가 없는 경우에만 추가
      if (!existingRoom.participants || !existingRoom.participants.some((p) => p.owner)) {
        return {
          ...existingRoom,
          participants: currentUser
            ? [
                {
                  ...currentUser,
                  owner: existingRoom.ownerId === currentUser.id,
                  nickname: currentUser.nickname || currentUser.username || "나",
                },
                ...(existingRoom.participants || []).filter((p) => p.id !== currentUser.id),
              ]
            : existingRoom.participants || [],
        }
      }

      return existingRoom
    } catch (err) {
      console.error("방 상세 정보 가져오기 오류:", err)
      return null
    }
  }

  // 멤버 정보 모달 표시 핸들러
  const showMembersInfo = async (room) => {
    // 방 상세 정보 가져오기
    const detailedRoom = await fetchRoomDetails(room.uniqueId)

    // 방 정보에 방장 추가
    const enhancedRoom = detailedRoom || room

    // 방장 정보 처리
    if (
      currentUser &&
      enhancedRoom.ownerId === currentUser.id &&
      (!enhancedRoom.participants || !enhancedRoom.participants.some((p) => p.owner))
    ) {
      enhancedRoom.participants = enhancedRoom.participants || []

      // 이미 해당 사용자가 목록에 있는지 확인
      const existingUserIndex = enhancedRoom.participants.findIndex((p) => p.id === currentUser.id)

      if (existingUserIndex >= 0) {
        // 기존 사용자 정보를 방장으로 업데이트
        enhancedRoom.participants[existingUserIndex] = {
          ...enhancedRoom.participants[existingUserIndex],
          owner: true,
        }
      } else {
        // 방장 정보 추가
        enhancedRoom.participants.push({
          ...currentUser,
          owner: true,
          nickname: currentUser.nickname || currentUser.username || "나",
        })
      }
    }

    setSelectedRoom(enhancedRoom)
    setShowMembersModal(true)

    // 상태 업데이트 - 방 목록에 반영
    setAlarmRooms(prev => prev.map(r =>
      Number(r.uniqueId) === Number(enhancedRoom.uniqueId) ? enhancedRoom : r
    ));
  };

  // 토스트 메시지 표시 함수
  const showToastMessage = (message) => {
    setToastMessage(message)
    setShowToast(true)
    setTimeout(() => setShowToast(false), 3000)
  }

  // 채팅방 생성 핸들러
  const handleCreateRoom = async (roomName, repoUrl) => {
    try {
      const res = await axiosInstance.post("/chat-rooms", {
        name: roomName,
        repositoryUrl: repoUrl,
      })

      const created = res.data;
      setShowCreateModal(false);
      fetchAllRooms();

      // 응답에서 얻은 데이터로 채팅방 목록 직접 업데이트
      if (created) {
        // 생성자를 방장으로 추가
        const newRoom = {
          ...created,
          uniqueId: created.id || created.roomId,
          ownerId: created.ownerId || (currentUser ? currentUser.id : null),
          participants: [
            {
              ...(currentUser || {}),
              owner: true, 
              nickname: currentUser?.nickname || currentUser?.username || "나",
            },
          ],
        }

          setAlarmRooms(prev => [newRoom, ...prev.slice(0, 9)]); // 최대 10개 유지
          fetchAllRooms();
      }

      if (created?.id) {
        navigate(`/chat/${created.inviteCode}`)
      }
    } catch (err) {
      alert(
        err.response?.data?.message || // 백엔드에서 내려준 에러 메시지
          err.message || // 일반 JS 에러 메시지
          "방 생성에 실패했습니다.. ㅋㅋ루삥뽕뽕",
      ) // 기본 메시지);

      throw err
    }
  }

  // 채팅방 참여 핸들러
  const handleJoinRoom = async (inviteCode) => {
    try {
      const res = await axiosInstance.post("/chat-rooms/join", {
        inviteCode,
      })

      const joined = await res.data;
      setShowJoinModal(false);
      fetchAllRooms();

      if (joined) {
        const newRoom = {
          ...joined,
          uniqueId: joined.id || joined.roomId,
          participants: [
            ...(joined.participants || []),
            ...(joined.ownerId === currentUser?.id && currentUser
              ? [
                 {
                  ...currentUser,
                  owner: true,
                  nickname: currentUser.nickname || currentUser.username || '나'
                  },
              ]
              : [])
          ]
        };
      }

      if (joined?.id) {
        navigate(`/chat/${joined.inviteCode}`)
      }
    } catch (err) {
      alert(err.response?.data?.message || err.message || "채팅방 참여에 실패했습니다.")
      throw err
    }
  }

  // ADD THIS FUNCTION - Render tab content
  const renderTabContent = () => {
    if (activeTab === "friends") {
      return <FriendsSidebar onStartChat={onStartChat} /> // 👈 중요!!
  }

    // Original chat room content
    return (
      <>
        {/* 채팅방 목록 */}
        <div
          className="rooms-container"
          style={{
            overflowY: "auto",
            flex: 1,
            padding: "5px 0",
            scrollbarWidth: "none", // Firefox
            msOverflowStyle: "none", // IE/Edge
          }}
        >
          {loading ? (
            <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>
              채팅방 불러오는 중...
            </div>
          ) : alarmRooms.length === 0 ? (
            <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>
              참여중인 채팅방이 없습니다
            </div>
          ) : (
            alarmRooms.map((room) => {
              const roomUniqueId = room.uniqueId;
              const isCurrentRoom = roomId && Number(roomId) === Number(roomUniqueId);
              const isSelectedForModal = selectedRoom && Number(selectedRoom.uniqueId) === Number(roomUniqueId) && showMembersModal;
              const roomInviteCode = room.inviteCode;
              const hasUnreadMessage = unreadMessages[roomUniqueId] && !isCurrentRoom;

              return (
                <div key={`room-${roomUniqueId}`} style={{ padding: "5px 10px" }}>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      backgroundColor: isCurrentRoom ? "rgba(255,255,255,0.2)" : "transparent",
                      padding: "10px 12px",
                      borderRadius: "8px",
                      cursor: "pointer",
                      transition: "background 0.2s ease",
                      border: isCurrentRoom ? "1px solid rgba(255,255,255,0.3)" : "1px solid transparent",
                      position: "relative",
                    }}
                    onMouseEnter={(e) => {
                      if (!isCurrentRoom) {
                        e.currentTarget.style.backgroundColor = "rgba(255,255,255,0.1)"
                      }
                    }}
                    onMouseLeave={(e) => {
                      if (!isCurrentRoom) {
                        e.currentTarget.style.backgroundColor = "transparent"
                      }
                    }}
                  >
                    <div
                      onClick={() => navigateToRoom(roomUniqueId, roomInviteCode)}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        flex: 1,
                        overflow: "hidden",
                      }}
                    >
                      <div
                        style={{
                          backgroundColor: isCurrentRoom ? "#fff" : "rgba(255,255,255,0.7)",
                          color: "#2588F1",
                          borderRadius: "50%",
                          width: "28px",
                          height: "28px",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          marginRight: "10px",
                          flexShrink: 0,
                          position: "relative",
                        }}
                      >
                        <FaRegCommentDots size={14} />
                        {/* 읽지 않은 메시지 알림 점 (빨간점)*/}
                        {hasUnreadMessage && (
                          <div
                            style={{
                              position: "absolute",
                              top: "-3px",
                              right: "-3px",
                              width: "12px",
                              height: "12px",
                              backgroundColor: "#ff4757",
                              borderRadius: "50%",
                              border: "2px solid #2588F1",
                              animation: "pulse 2s infinite",
                            }}
                          />
                        )}
                      </div>
                      <span
                        style={{
                          fontWeight: isCurrentRoom ? "bold" : hasUnreadMessage ? "600" : "normal",
                          whiteSpace: "nowrap",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          color: hasUnreadMessage ? "#fff" : "inherit",
                        }}
                      >
                        {room.name || room.roomName || `Room ${roomUniqueId}`}
                      </span>
                    </div>

                    <div
                      onClick={() => showMembersInfo(room)}
                      style={{
                        cursor: "pointer",
                        width: "28px",
                        height: "28px",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        borderRadius: "50%",
                        backgroundColor: isSelectedForModal ? "rgba(255,255,255,0.3)" : "rgba(255,255,255,0.1)",
                        transition: "all 0.2s ease",
                        position: "relative",
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.backgroundColor = "rgba(255,255,255,0.2)"
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.backgroundColor = isSelectedForModal
                          ? "rgba(255,255,255,0.3)"
                          : "rgba(255,255,255,0.1)"
                      }}
                    >
                      <FaInfoCircle size={14} />
                      {isSelectedForModal && (
                        <div
                          style={{
                            position: "absolute",
                            top: "-2px",
                            right: "-2px",
                            width: "8px",
                            height: "8px",
                            backgroundColor: "#fff",
                            borderRadius: "50%",
                            boxShadow: "0 0 0 2px #2588F1",
                          }}
                        />
                      )}
                    </div>
                  </div>
                </div>
              )
            })
          )}
        </div>

        {/* 하단 버튼 */}
        <div
          style={{
            padding: "16px",
            borderTop: "1px solid rgba(255,255,255,0.15)",
            backgroundColor: "rgba(0,0,0,0.05)",
          }}
        >
          <button
            onClick={() => setShowJoinModal(true)}
            style={{
              width: "100%",
              padding: "12px",
              backgroundColor: "rgba(255,255,255,0.1)",
              border: "1px solid rgba(255,255,255,0.2)",
              borderRadius: "8px",
              color: "white",
              fontWeight: "bold",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              marginBottom: "12px",
              cursor: "pointer",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = "rgba(255,255,255,0.15)"
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = "rgba(255,255,255,0.1)"
            }}
          >
            <FaComments style={{ marginRight: "8px" }} />
            Join Chat Room
          </button>
          <button
            onClick={() => setShowCreateModal(true)}
            style={{
              width: "100%",
              padding: "12px",
              backgroundColor: "#1366d6",
              border: "1px solid rgba(255,255,255,0.2)",
              borderRadius: "8px",
              color: "white",
              fontWeight: "bold",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = "#0d5bca"
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = "#1366d6"
            }}
          >
            <FaPlus style={{ marginRight: "8px" }} />
            New Chat Room
          </button>
        </div>
      </>
    )
  }

  return (
    <>
      <div
        ref={sidebarRef}
        className="sidebar-container"
        style={{
          width: "260px",
          height: "100%",
          backgroundColor: "#2588F1",
          color: "white",
          display: "flex",
          flexDirection: "column",
          position: "relative",
          overflow: "hidden",
        }}
      >
        {/* MODIFIED HEADER - Add tabs */}
        <div
          style={{
            padding: "20px 15px 0px", // Changed bottom padding
            borderBottom: "1px solid rgba(255,255,255,0.15)",
            background: "linear-gradient(to bottom, rgba(0,0,0,0.1), transparent)",
          }}
        >
          <h3 style={{ margin: "0 0 15px 0", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: "18px", fontWeight: "600" }}>
              {activeTab === "chat" ? "Chat Rooms" : "Friends"}
            </span>
          
            {/* Show different counters based on active tab */}
            {activeTab === "chat" && (
              <span
                style={{
                  fontSize: "14px",
                  color: "rgba(255,255,255,0.8)",
                  padding: "2px 8px",
                  backgroundColor: "rgba(0,0,0,0.1)",
                  borderRadius: "12px",
                  whiteSpace: "nowrap",
                }}
              >
              </span>
            )}
          </h3>

          {/* ADD TAB NAVIGATION */}
          <div
            style={{
              display: "flex",
              marginBottom: "15px",
              backgroundColor: "rgba(0,0,0,0.1)",
              borderRadius: "8px",
              padding: "4px",
            }}
          >
            <button
              onClick={() => setActiveTab("chat")}
              style={{
                flex: 1,
                padding: "8px 12px",
                backgroundColor: activeTab === "chat" ? "rgba(255,255,255,0.2)" : "transparent",
                border: "none",
                borderRadius: "6px",
                color: "white",
                fontWeight: activeTab === "chat" ? "bold" : "normal",
                cursor: "pointer",
                transition: "all 0.2s ease",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "14px",
              }}
            >
              <FaComments style={{ marginRight: "6px", fontSize: "12px" }} />
              Chat
            </button>
            <button
              onClick={() => setActiveTab("friends")}
              style={{
                flex: 1,
                padding: "8px 12px",
                backgroundColor: activeTab === "friends" ? "rgba(255,255,255,0.2)" : "transparent",
                border: "none",
                borderRadius: "6px",
                color: "white",
                fontWeight: activeTab === "friends" ? "bold" : "normal",
                cursor: "pointer",
                transition: "all 0.2s ease",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "14px",
              }}
            >
              <FaUsers style={{ marginRight: "6px", fontSize: "12px" }} />
              Friends
            </button>
          </div>
        </div>

        {/* REPLACE CONTENT AREA WITH TAB CONTENT */}
        {renderTabContent()}
      </div>

      {/* 모달 컴포넌트들 */}
      {showMembersModal && selectedRoom && (
        <RoomInfoModal
          room={selectedRoom}
          onClose={() => setShowMembersModal(false)}
          sidebarRef={sidebarRef}
          showToast={showToastMessage}
        />
      )}

      {showCreateModal && <CreateRoomModal onClose={() => setShowCreateModal(false)} onSubmit={handleCreateRoom} />}

      {showJoinModal && <JoinRoomModal onClose={() => setShowJoinModal(false)} onSubmit={handleJoinRoom} />}

      {showToast && (
        <Toast message={toastMessage} />
      )}

      {/* CSS 애니메이션 추가 */}
      <style jsx>{`
        @keyframes pulse {
          0% {
            box-shadow: 0 0 0 0 rgba(255, 71, 87, 0.7);
          }
          70% {
            box-shadow: 0 0 0 8px rgba(255, 71, 87, 0);
          }
          100% {
            box-shadow: 0 0 0 0 rgba(255, 71, 87, 0);
          }
        }
        .hide-scrollbar::-webkit-scrollbar {
          display: none;
        }
      `}</style>
    </>
  )
}

export default Sidebar
