"use client"

import { useState, useEffect, useRef } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { FaRegCommentDots, FaInfoCircle, FaComments, FaPlus, FaUsers } from "react-icons/fa"

import CreateRoomModal from "./modals/CreateRoomModal"
import JoinRoomModal from "./modals/JoinRoomModal"
import RoomInfoModal from "./modals/RoomInfoModal"
import Toast from "./common/Toast"
import FriendsSidebar from "./FriendsSidebar"
import axiosInstance from "./api/axiosInstance"
import { useAlarm } from '../context/AlarmContext'
import useWebSocket from './common/useWebSocket'
import styles from './Sidebar.module.css'

const CACHE_KEY = 'sidebar_rooms_cache'

const Sidebar = ({ onStartChat }) => {
  const navigate = useNavigate()
  const { inviteCode } = useParams()
  const sidebarRef = useRef(null)

  const [activeTab, setActiveTab] = useState("chat")

  // localStorage 캐시로 초기값 설정 → 깜빡임 방지
  const [loading, setLoading] = useState(() => {
    const cached = localStorage.getItem(CACHE_KEY)
    return !cached // 캐시 있으면 로딩 false로 시작
  })

  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showJoinModal, setShowJoinModal] = useState(false)
  const [showMembersModal, setShowMembersModal] = useState(false)
  const [selectedRoom, setSelectedRoom] = useState(null)
  const [roomsReady, setRoomsReady] = useState(false)

  const [showToast, setShowToast] = useState(false)
  const [toastMessage, setToastMessage] = useState("")

  const [currentUser, setCurrentUser] = useState(null)
  const [roomId, setRoomId] = useState(null)

  const { getAlarmStatus, alarmStatusMap = {} } = useAlarm()

  // localStorage 캐시로 초기값 설정 → 깜빡임 방지
  const [alarmRooms, setAlarmRooms] = useState(() => {
    try {
      const cached = localStorage.getItem(CACHE_KEY)
      return cached ? JSON.parse(cached) : []
    } catch {
      return []
    }
  })

  const handleSidebarMessage = (roomUniqueId, message) => {
    if (Number(roomId) !== Number(roomUniqueId)) {
      setAlarmRooms(prev => prev.map(r =>
        Number(r.uniqueId) === Number(roomUniqueId)
          ? { ...r, unreadCount: (r.unreadCount ?? 0) + 1 }
          : r
      ))

      const room = alarmRooms.find(r => r.uniqueId === roomUniqueId)

      if (room) {
        setAlarmRooms(prev => {
          const updated = prev.filter(r => r.uniqueId !== roomUniqueId)
          const updatedRoom = prev.find(r => r.uniqueId === roomUniqueId)
          return [updatedRoom, ...updated]
        })
      }

      const isAlarmEnabled = getAlarmStatus(roomUniqueId)
      if (isAlarmEnabled === false) return

      window.dispatchEvent(
        new CustomEvent("chat:notify", {
          detail: {
            senderNickname: message.senderName,
            body: message.content,
            senderImg: message.profileImageUrl,
            url: room?.inviteCode ? `/chat/${room.inviteCode}` : undefined,
            tag: `room-${Date.now()}`,
            silent: false,
            roomName: room?.roomName || `Room ${roomUniqueId}`
          },
        })
      )
    }
  }

  const stompClientRef = useWebSocket({
    chatRooms: alarmRooms,
    currentRoomId: roomId,
    onSidebarMessage: handleSidebarMessage,
    onMessageReceived: () => {},
    roomId: null,
  })

  useEffect(() => {
    if (activeTab === "chat") {
      fetchAllRooms()
      fetchCurrentUser()
    }
  }, [inviteCode, activeTab, alarmStatusMap])

  useEffect(() => {
    if (!inviteCode) {
      setRoomId(null)
      return
    }
    const foundRoom = alarmRooms.find(room => room.inviteCode === inviteCode)
    if (foundRoom) {
      setRoomId(foundRoom.uniqueId)
    } else {
      setRoomId(null)
    }
  }, [inviteCode, alarmRooms, alarmStatusMap])

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
      // 캐시 있으면 로딩 스피너 안 보여줌
      const cached = localStorage.getItem(CACHE_KEY)
      if (!cached) setLoading(true)

      const res = await axiosInstance.get('/chat-rooms/all')
      const rooms = res.data.map(room => ({
        ...room,
        uniqueId: room.roomId || room.id,
        alarmEnabled: room.alarmEnabled ?? true,
        roomName: room.roomName || `Room ${room.roomId || room.id}`,
        inviteCode: room.inviteCode,
        unreadCount: room.unreadCount ?? 0,
      })).filter(room => room.uniqueId)

      setAlarmRooms(rooms)

      // 최신 데이터 캐시 저장s
      localStorage.setItem(CACHE_KEY, JSON.stringify(rooms))
      setRoomsReady(true)
    } catch (e) {
      console.error('채팅방 목록 로딩 실패', e)
    } finally {
      setLoading(false)
    }
  }

  const navigateToRoom = async (id, inviteCode) => {
    if (id) {
      setAlarmRooms(prev => prev.map(r =>
        Number(r.uniqueId) === Number(roomId) ? { ...r, unreadCount: 0 } : r
      ))
      navigate(`/chat/${inviteCode}`)
    }
  }

  const fetchRoomDetails = async (roomId) => {
    try {
      const existingRoom = alarmRooms.find(room => Number(room.uniqueId) === Number(roomId))
      if (!existingRoom) return null

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

  const showMembersInfo = async (room) => {
    const detailedRoom = await fetchRoomDetails(room.uniqueId)
    const enhancedRoom = detailedRoom || room

    if (
      currentUser &&
      enhancedRoom.ownerId === currentUser.id &&
      (!enhancedRoom.participants || !enhancedRoom.participants.some((p) => p.owner))
    ) {
      enhancedRoom.participants = enhancedRoom.participants || []
      const existingUserIndex = enhancedRoom.participants.findIndex((p) => p.id === currentUser.id)

      if (existingUserIndex >= 0) {
        enhancedRoom.participants[existingUserIndex] = {
          ...enhancedRoom.participants[existingUserIndex],
          owner: true,
        }
      } else {
        enhancedRoom.participants.push({
          ...currentUser,
          owner: true,
          nickname: currentUser.nickname || currentUser.username || "나",
        })
      }
    }

    setSelectedRoom(enhancedRoom)
    setShowMembersModal(true)

    setAlarmRooms(prev => prev.map(r =>
      Number(r.uniqueId) === Number(enhancedRoom.uniqueId) ? enhancedRoom : r
    ))
  }

  const showToastMessage = (message) => {
    setToastMessage(message)
    setShowToast(true)
    setTimeout(() => setShowToast(false), 3000)
  }

  const handleCreateRoom = async (roomName, repoUrl) => {
    try {
      const res = await axiosInstance.post("/chat-rooms", {
        name: roomName,
        repositoryUrl: repoUrl,
      })

      const created = res.data
      setShowCreateModal(false)
      fetchAllRooms()

      if (created) {
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
        setAlarmRooms(prev => [newRoom, ...prev.slice(0, 9)])
        fetchAllRooms()
      }

      if (created?.id) {
        navigate(`/chat/${created.inviteCode}`)
      }
    } catch (err) {
      alert(err.response?.data?.message || err.message || "방 생성에 실패했습니다.")
      throw err
    }
  }

  const handleJoinRoom = async (inviteCode) => {
    try {
      const res = await axiosInstance.post("/chat-rooms/join", { inviteCode })
      const joined = await res.data
      setShowJoinModal(false)
      fetchAllRooms()

      if (joined?.id) {
        navigate(`/chat/${joined.inviteCode}`)
      }
    } catch (err) {
      alert(err.response?.data?.message || err.message || "채팅방 참여에 실패했습니다.")
      throw err
    }
  }

  const renderTabContent = () => {
    if (activeTab === "friends") {
      return <FriendsSidebar onStartChat={onStartChat} />
    }

    return (
      <>
        <div className={styles.roomsContainer}>
          {loading ? (
            <div className={styles.loadingState}>채팅방 불러오는 중...</div>
          ) : alarmRooms.length === 0 ? (
            <div className={styles.emptyState}>참여중인 채팅방이 없습니다</div>
          ) : (
            alarmRooms.map((room) => {
              const roomUniqueId = room.uniqueId
              const isCurrentRoom = roomId && Number(roomId) === Number(roomUniqueId)
              const isSelectedForModal = selectedRoom && Number(selectedRoom.uniqueId) === Number(roomUniqueId) && showMembersModal
              const roomInviteCode = room.inviteCode
              const unreadCount = room.unreadCount ?? 0
              const hasUnread = unreadCount > 0 && !isCurrentRoom

              return (
                <div key={`room-${roomUniqueId}`} className={styles.roomItem}>
                  <div className={`${styles.roomRow} ${isCurrentRoom ? styles.roomRowActive : ''}`}>
                    <div
                      onClick={() => navigateToRoom(roomUniqueId, roomInviteCode)}
                      className={styles.roomLeft}
                    >
                      <div className={`${styles.roomIcon} ${isCurrentRoom ? styles.roomIconActive : ''}`}>
                        <FaRegCommentDots size={14} />
                        {hasUnread && (
                          <div className={styles.unreadBadge}>
                            {unreadCount > 99 ? "99+" : unreadCount}
                          </div>
                        )}
                      </div>
                      <span className={`${styles.roomName} ${isCurrentRoom ? styles.roomNameActive : ''} ${hasUnread ? styles.roomNameUnread : ''}`}>
                        {room.name || room.roomName || `Room ${roomUniqueId}`}
                      </span>
                    </div>

                    <button
                      onClick={() => showMembersInfo(room)}
                      className={`${styles.roomInfoButton} ${isSelectedForModal ? styles.roomInfoButtonActive : ''}`}
                    >
                      <FaInfoCircle size={14} />
                      {isSelectedForModal && <div className={styles.roomInfoDot} />}
                    </button>
                  </div>
                </div>
              )
            })
          )}
        </div>

        <div className={styles.sidebarFooter}>
          <button onClick={() => setShowJoinModal(true)} className={styles.joinButton}>
            <FaComments />
            Join Chat Room
          </button>
          <button onClick={() => setShowCreateModal(true)} className={styles.createButton}>
            <FaPlus />
            New Chat Room
          </button>
        </div>
      </>
    )
  }

  return (
    <>
      <div ref={sidebarRef} className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <h3 className={styles.sidebarTitle}>
            {activeTab === "chat" ? "채팅방" : "친구"}
          </h3>

          <div className={styles.tabNav}>
            <button
              onClick={() => setActiveTab("chat")}
              className={`${styles.tabButton} ${activeTab === "chat" ? styles.tabButtonActive : ''}`}
            >
              <FaComments size={12} />
              Chat
            </button>
            <button
              onClick={() => setActiveTab("friends")}
              className={`${styles.tabButton} ${activeTab === "friends" ? styles.tabButtonActive : ''}`}
            >
              <FaUsers size={12} />
              Friends
            </button>
          </div>
        </div>

        {renderTabContent()}
      </div>

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
      {showToast && <Toast message={toastMessage} />}
    </>
  )
}

export default Sidebar