import { useState, useEffect, useRef, useCallback } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { FaRegCommentDots, FaInfoCircle, FaComments, FaPlus, FaUsers } from "react-icons/fa"

import CreateRoomModal from "./modals/CreateRoomModal"
import JoinRoomModal from "./modals/JoinRoomModal"
import RoomInfoModal from "./modals/RoomInfoModal"
import Toast from "./common/Toast"
import FriendsSidebar from "./FriendsSidebar"
import axiosInstance from "./api/axiosInstance"
import { useAlarm } from '../context/AlarmContext'
import { useUser } from "../context/UserContext"
import styles from './Sidebar.module.css'

const CACHE_KEY = 'sidebar_rooms_cache'

const Sidebar = ({ onStartChat }) => {
  const navigate = useNavigate()
  const { inviteCode } = useParams()
  const sidebarRef = useRef(null)

  const [activeTab, setActiveTab] = useState("chat")
  const [loading, setLoading] = useState(() => !localStorage.getItem(CACHE_KEY))
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showJoinModal, setShowJoinModal] = useState(false)
  const [showMembersModal, setShowMembersModal] = useState(false)
  const [selectedRoom, setSelectedRoom] = useState(null)
  const [showToast, setShowToast] = useState(false)
  const [toastMessage, setToastMessage] = useState("")
  const [roomId, setRoomId] = useState(null)

  const { currentUser } = useUser()
  const { getAlarmStatus, alarmStatusMap, alarmRooms, updateRooms, clearUnread, enterRoom } = useAlarm()

  useEffect(() => {
    if (!inviteCode) {
      setRoomId(null)
      return
    }
    const foundRoom = alarmRooms.find(room => room.inviteCode === inviteCode)
    setRoomId(foundRoom ? foundRoom.uniqueId : null)
  }, [inviteCode, alarmRooms, alarmStatusMap])

  const fetchAllRooms = useCallback(async () => {
    try {
      if (!localStorage.getItem(CACHE_KEY)) setLoading(true)
      const res = await axiosInstance.get('/chat-rooms/all')
      const rooms = res.data.map(room => ({
        ...room,
        uniqueId: room.roomId || room.id,
        alarmEnabled: room.alarmEnabled ?? true,
        roomName: room.roomName || `Room ${room.roomId || room.id}`,
        inviteCode: room.inviteCode,
        unreadCount: room.unreadCount ?? 0,
      })).filter(room => room.uniqueId)
      updateRooms(rooms)
    } catch (e) {
      console.error('채팅방 목록 로딩 실패', e)
    } finally {
      setLoading(false)
    }
  }, [updateRooms])

  useEffect(() => {
    fetchAllRooms()
  }, [fetchAllRooms])

  useEffect(() => {
    const handleRoomRead = () => fetchAllRooms()
    window.addEventListener('room:read', handleRoomRead)
    return () => window.removeEventListener('room:read', handleRoomRead)
  }, [fetchAllRooms])

  const navigateToRoom = (id, inviteCode) => {
    if (id) {
      enterRoom(id)
      clearUnread(id)
      enterRoom(id)
      navigate(`/chat/${inviteCode}`)
    }
  }

  const alarmRoomsRef = useRef(alarmRooms);
  useEffect(() => {
    alarmRoomsRef.current = alarmRooms;
  }, [alarmRooms]);

  useEffect(() => {
    const handleRoomDeleted = (e) => {
      const deletedRoomId = e.detail.roomId;
      const remaining = alarmRoomsRef.current.filter(r => Number(r.uniqueId) !== Number(deletedRoomId));
      if (remaining.length > 0) {
        navigate(`/chat/${remaining[0].inviteCode}`);
      } else {
        navigate('/');
      }
      fetchAllRooms();
    };

    window.addEventListener('room:deleted', handleRoomDeleted);
    return () => window.removeEventListener('room:deleted', handleRoomDeleted);
  }, [fetchAllRooms, navigate]);

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
      await fetchAllRooms()
      if (created?.id) navigate(`/chat/${created.inviteCode}`)
    } catch (err) {
      alert(err.response?.data?.message || err.message || "방 생성에 실패했습니다.")
      throw err
    }
  }

  const handleJoinRoom = async (inviteCode) => {
    try {
      const res = await axiosInstance.post("/chat-rooms/join", { inviteCode })
      const joined = res.data
      setShowJoinModal(false)
      await fetchAllRooms()
      if (joined?.id) navigate(`/chat/${joined.inviteCode}`)
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
          indexingStatus={selectedRoom?.indexingStatus}
        />
      )}
      {showCreateModal && <CreateRoomModal onClose={() => setShowCreateModal(false)} onSubmit={handleCreateRoom} />}
      {showJoinModal && <JoinRoomModal onClose={() => setShowJoinModal(false)} onSubmit={handleJoinRoom} />}
      {showToast && <Toast message={toastMessage} />}
    </>
  )
}

export default Sidebar