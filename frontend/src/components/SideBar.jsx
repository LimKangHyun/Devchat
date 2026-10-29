import { useState, useEffect, useRef, useCallback, useMemo } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { Search, Info, Plus, LogIn, X, ChevronDown, GitBranch } from "lucide-react"

import CreateRoomModal from "./modals/CreateRoomModal"
import JoinRoomModal from "./modals/JoinRoomModal"
import RoomInfoModal from "./modals/RoomInfoModal"
import Toast from "./common/Toast"
import FriendsSidebar from "./FriendsSidebar"
import ScheduleSidebar from "./schedule/ScheduleSidebar"
import axiosInstance from "./api/axiosInstance"
import { useAlarm } from '../context/AlarmContext'
import { useUser } from "../context/UserContext"
import { useWebSocketContext } from "./common/WebSocketContext"
import styles from './Sidebar.module.css'

const CACHE_KEY = 'sidebar_rooms_cache'

const getRepoLabel = (url) => {
  if (!url) return null
  const match = url.match(/github\.com\/([^/]+\/[^/?#]+)/)
  return match ? match[1].replace(/\.git$/, "") : url
}

const Sidebar = ({ onStartChat, activeTab = "chat", hidden = false }) => {
  const navigate = useNavigate()
  const { inviteCode } = useParams()
  const sidebarRef = useRef(null)

  const [roomQuery, setRoomQuery] = useState("")
  const [loading, setLoading] = useState(() => !localStorage.getItem(CACHE_KEY))
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showJoinModal, setShowJoinModal] = useState(false)
  const [showMembersModal, setShowMembersModal] = useState(false)
  const [selectedRoom, setSelectedRoom] = useState(null)
  const [showToast, setShowToast] = useState(false)
  const [toastMessage, setToastMessage] = useState("")
  const [roomId, setRoomId] = useState(null)

  const { currentUser } = useUser()
  const { getAlarmStatus, alarmStatusMap, alarmRooms: allRooms, updateRooms, clearUnread, enterRoom, isRoomsTrusted } = useAlarm()
  // 다른 계정이 남긴 캐시는 보여주지 않는다 (서버 목록이 오거나 내 캐시로 확인될 때까지 로딩)
  const roomsTrusted = isRoomsTrusted(currentUser?.username)
  const alarmRooms = useMemo(() => (roomsTrusted ? allRooms : []), [roomsTrusted, allRooms])

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

  // 다른 창·기기에서 방을 만들거나 들어가거나 나가면 서버가 알려준다. 방 목록을 다시 받아야
  // 그 방을 구독해서 새 메시지가 안읽음·알림에 잡힌다 (예전에는 새로고침해야 했다).
  // 다시 연결됐을 때도 끊긴 사이 바뀐 것을 맞추려고 다시 받는다.
  const { stompClientRef, connected } = useWebSocketContext() || {}
  const everConnectedRef = useRef(false)
  useEffect(() => {
    const client = stompClientRef?.current
    if (!connected || !client?.connected) return
    if (everConnectedRef.current) fetchAllRooms() // 첫 연결은 처음 불러오기가 맡는다
    everConnectedRef.current = true
    const sub = client.subscribe("/user/queue/rooms", () => fetchAllRooms())
    return () => { try { if (client.connected) sub.unsubscribe() } catch (e) {} }
  }, [connected, stompClientRef, fetchAllRooms])

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


  const [roomsCollapsed, setRoomsCollapsed] = useState(false)

  const filteredRooms = roomQuery.trim()
    ? alarmRooms.filter((room) =>
        (room.name || room.roomName || "").toLowerCase().includes(roomQuery.trim().toLowerCase()))
    : alarmRooms

  const renderRooms = () => (
    <>
      <div className={styles.searchBox}>
        <Search size={14} className={styles.searchIcon} />
        <input
          type="text"
          value={roomQuery}
          onChange={(e) => setRoomQuery(e.target.value)}
          placeholder="채팅방 검색"
          className={styles.searchInput}
        />
        {roomQuery && (
          <button className={styles.searchClear} onClick={() => setRoomQuery("")} aria-label="검색어 지우기">
            <X size={12} />
          </button>
        )}
      </div>

      <div className={`${styles.roomsContainer} dc-scroll-dark`}>
        <button
          type="button"
          className={styles.sectionHeader}
          onClick={() => setRoomsCollapsed((v) => !v)}
          aria-expanded={!roomsCollapsed}
        >
          <ChevronDown size={14} className={`${styles.sectionChevron} ${roomsCollapsed ? styles.sectionChevronCollapsed : ""}`} />
          <span>참여 중인 방</span>
          {alarmRooms.length > 0 && <span className={styles.sectionCount}>{alarmRooms.length}</span>}
        </button>

        {!roomsCollapsed && (
          loading || !roomsTrusted ? (
            <div className={styles.loadingState}>채팅방 불러오는 중...</div>
          ) : alarmRooms.length === 0 ? (
            <div className={styles.emptyState}>
              아직 참여한 방이 없어요.
              <br />방을 새로 만들거나 초대 코드로 들어가세요.
            </div>
          ) : filteredRooms.length === 0 ? (
            <div className={styles.emptyState}>“{roomQuery.trim()}” 검색 결과가 없어요</div>
          ) : (
            filteredRooms.map((room) => {
              const roomUniqueId = room.uniqueId
              const isCurrentRoom = roomId && Number(roomId) === Number(roomUniqueId)
              const isSelectedForModal = selectedRoom && Number(selectedRoom.uniqueId) === Number(roomUniqueId) && showMembersModal
              const unreadCount = room.unreadCount ?? 0
              const hasUnread = unreadCount > 0 && !isCurrentRoom
              const displayName = room.name || room.roomName || `Room ${roomUniqueId}`
              const repoLabel = getRepoLabel(room.repositoryUrl)

              return (
                <div
                  key={`room-${roomUniqueId}`}
                  className={`${styles.roomRow} ${isCurrentRoom ? styles.roomRowActive : ""} ${hasUnread ? styles.roomRowUnread : ""}`}
                  onClick={() => navigateToRoom(roomUniqueId, room.inviteCode)}
                  title={repoLabel ? `${displayName} · ${repoLabel}` : displayName}
                >
                  <span className={styles.roomIcon}>
                    {displayName.trim().slice(0, 1).toUpperCase()}
                  </span>

                  <div className={styles.roomBody}>
                    <span className={styles.roomTitleLine}>
                      <span className={styles.roomName}>{displayName}</span>
                      {repoLabel && (
                        <GitBranch size={12} className={styles.roomRepoIcon} aria-label={`레포 연결됨: ${repoLabel}`} />
                      )}
                    </span>
                  </div>

                  <div className={styles.roomSide}>
                    <button
                      onClick={(e) => { e.stopPropagation(); showMembersInfo(room) }}
                      className={`${styles.roomInfoButton} ${isSelectedForModal ? styles.roomInfoButtonActive : ""}`}
                      aria-label="채팅방 정보"
                    >
                      <Info size={14} />
                    </button>
                    {hasUnread && (
                      <span className={styles.unreadBadge}>{unreadCount > 99 ? "99+" : unreadCount}</span>
                    )}
                  </div>
                </div>
              )
            })
          )
        )}

        <button type="button" className={styles.addRow} onClick={() => setShowCreateModal(true)}>
          <span className={styles.addIcon}><Plus size={14} /></span>
          새 방 만들기
        </button>
        <button type="button" className={styles.addRow} onClick={() => setShowJoinModal(true)}>
          <span className={styles.addIcon}><LogIn size={13} /></span>
          초대 코드로 들어가기
        </button>
      </div>
    </>
  )

  return (
    <>
      <aside ref={sidebarRef} className={`${styles.sidebar} ${hidden ? styles.sidebarHidden : ""}`}>
        <div className={styles.sidebarHeader}>
          <h3 className={styles.sidebarTitle}>
            {activeTab === "chat" ? "채팅방" : activeTab === "calendar" ? "일정" : "친구"}
          </h3>
          {activeTab === "chat" && (
            <button
              type="button"
              className={styles.headerAction}
              onClick={() => setShowCreateModal(true)}
              aria-label="새 채팅방 만들기"
              title="새 채팅방 만들기"
            >
              <Plus size={18} />
            </button>
          )}
        </div>
        {activeTab === "friends" ? <FriendsSidebar onStartChat={onStartChat} />
          : activeTab === "calendar" ? <ScheduleSidebar />
          : renderRooms()}
      </aside>

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
      {showToast && <Toast open={showToast} message={toastMessage} />}
    </>
  )
}

export default Sidebar
