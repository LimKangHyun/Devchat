"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import styles from "./Sidebar.module.css"
import { MessageCircle, UserPlus } from "lucide-react"
import FindUserModal from "./modals/FindUserModal"
import axiosInstance from "./api/axiosInstance"
import { useUser } from '../context/UserContext'
import useWebSocketNotifications from "./common/useWebSocket"
import { useWebSocketContext } from "./common/WebSocketContext"

const FriendsSidebar = ({ onStartChat }) => {
  useEffect(() => {
    if (typeof onStartChat !== "function") {
      console.error("EnhancedFriendsSidebar: CRITICAL - onStartChat prop is NOT A FUNCTION. Chat modals will not open.")
    }
  }, [onStartChat])

  const { currentUser } = useUser()

  const [friends, setFriends] = useState([])
  const [loading, setLoading] = useState(false)
  const [showFindUserModal, setShowFindUserModal] = useState(false)
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMoreFriends, setHasMoreFriends] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [totalFriendsCount, setTotalFriendsCount] = useState(0)
  const [friendsWithNewMessages, setFriendsWithNewMessages] = useState(new Set())
  const friendsContainerRef = useRef(null)

  const handleFriendNotification = useCallback((notification) => {
    switch (notification.type) {
      case "FRIEND_ACCEPTED":
        // 새 친구의 실제 접속 상태를 받아오도록 목록을 다시 불러온다
        window.dispatchEvent(new Event("friend-request-accepted"))
        break
      case "FRIEND_REMOVED":
        setFriends((prev) => {
          const filtered = prev.filter((friend) => friend.username !== notification.sender)
          if (filtered.length !== prev.length) {
            setTotalFriendsCount((count) => Math.max(0, count - 1))
          }
          return filtered
        })
        break
      default:
        break
    }
  }, [])

  useWebSocketNotifications({
    username: currentUser?.username,
    onNotificationReceived: handleFriendNotification,
  })

  // 친구 접속 상태 실시간 반영. 다시 연결되면 끊긴 사이 바뀐 상태를 맞추려고 목록을 다시 받는다
  const { stompClientRef, connected } = useWebSocketContext()
  const wasConnectedRef = useRef(false)
  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !currentUser) return

    if (wasConnectedRef.current) fetchFriends(0, true)
    wasConnectedRef.current = true

    const sub = client.subscribe("/user/queue/presence", (message) => {
      try {
        const { username, status } = JSON.parse(message.body)
        setFriends((prev) => prev.map((f) => (f.username === username ? { ...f, status } : f)))
      } catch (e) {
        console.error("접속 상태 메시지 처리 실패", e)
      }
    })
    return () => {
      try { if (client.connected) sub.unsubscribe() } catch (e) {}
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connected, currentUser])

  useEffect(() => {
    const handleNewDmForSidebar = (event) => {
      const { senderUsername } = event.detail
      if (senderUsername) {
        setFriendsWithNewMessages((prev) => new Set(prev).add(senderUsername))
      }
    }
    window.addEventListener("new-dm-for-sidebar", handleNewDmForSidebar)
    return () => window.removeEventListener("new-dm-for-sidebar", handleNewDmForSidebar)
  }, [])

  useEffect(() => {
    const handleFriendRequestAccepted = () => fetchFriends(0, true)
    window.addEventListener("friend-request-accepted", handleFriendRequestAccepted)
    return () => window.removeEventListener("friend-request-accepted", handleFriendRequestAccepted)
  }, [])

  useEffect(() => {
    if (currentUser) {
      fetchFriends(0, true)
    }
  }, [currentUser])

  const fetchFriends = async (page = 0, reset = false) => {
    if (isLoadingMore && !reset) return
    if (reset) setLoading(true)
    else setIsLoadingMore(true)

    try {
      const response = await axiosInstance.get(`/friend?page=${page}&size=20`)
      const { content, totalElements, last } = response.data
      const newFriends = content.map((f) => ({
        username: f.username,
        nickname: f.nickname,
        status: f.status || "offline",
        avatar: f.profileImage,
      }))
      setFriends((prev) => (reset ? newFriends : [...prev, ...newFriends]))
      setHasMoreFriends(!last)
      setTotalFriendsCount(totalElements)
      setCurrentPage(page)
    } catch (err) {
      console.error("Error loading friends:", err)
    } finally {
      setLoading(false)
      setIsLoadingMore(false)
    }
  }

  const loadMoreFriends = useCallback(() => {
    if (hasMoreFriends && !isLoadingMore) {
      fetchFriends(currentPage + 1, false)
    }
  }, [currentPage, hasMoreFriends, isLoadingMore])

  const handleScroll = useCallback(() => {
    if (!friendsContainerRef.current) return
    const { scrollTop, scrollHeight, clientHeight } = friendsContainerRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      loadMoreFriends()
    }
  }, [loadMoreFriends])

  useEffect(() => {
    const el = friendsContainerRef.current
    if (el) {
      el.addEventListener("scroll", handleScroll)
      return () => el.removeEventListener("scroll", handleScroll)
    }
  }, [handleScroll])

  const handleFindUser = () => setShowFindUserModal(true)
  const handleSendFriendRequest = (user) => {}

  const handleStartChatClick = (e, friend) => {
    e.stopPropagation()
    setFriendsWithNewMessages((prev) => {
      const newSet = new Set(prev)
      newSet.delete(friend.username)
      return newSet
    })
    if (typeof onStartChat === "function") {
      onStartChat(friend)
    } else {
      console.error(`Cannot start chat for ${friend.username}, onStartChat is not a function!`)
    }
  }

  const getStatusColor = (status) => {
    switch (status?.toLowerCase()) {
      case "online": return "var(--online)"
      case "away": return "var(--warning)"
      default: return "transparent"
    }
  }

  const renderFriendItem = (friend) => {
    const hasNewMessage = friendsWithNewMessages.has(friend.username)
    const isOnline = friend.status?.toLowerCase() === "online"
    const name = friend.nickname || friend.username

    return (
      <div
        key={friend.username}
        className={`${styles.friendRow} ${hasNewMessage ? styles.roomRowUnread : ""}`}
        onClick={(e) => handleStartChatClick(e, friend)}
        title={`${name}님과 대화하기`}
      >
        <span className={styles.friendAvatarWrap}>
          {friend.avatar ? (
            <img
              src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${friend.avatar}`}
              alt=""
              className={styles.friendAvatar}
              onError={(e) => { e.currentTarget.src = "/images/not-found-profile.webp" }}
            />
          ) : (
            <span className={`${styles.friendAvatar} ${styles.friendAvatarFallback}`}>
              {name.slice(0, 1).toUpperCase()}
            </span>
          )}
          <span
            className={`${styles.friendStatus} ${isOnline || friend.status?.toLowerCase() === "away" ? "" : styles.friendStatusOffline}`}
            style={{ backgroundColor: getStatusColor(friend.status) }}
          />
        </span>

        <div className={styles.roomBody}>
          <span className={styles.roomName}>{name}</span>
          <span className={styles.roomSub}>@{friend.username}</span>
        </div>

        <div className={styles.roomSide}>
          {hasNewMessage ? (
            <span className={styles.unreadBadge}>N</span>
          ) : (
            <span className={styles.friendChatIcon} aria-hidden="true">
              <MessageCircle size={14} />
            </span>
          )}
        </div>
      </div>
    )
  }

  return (
    <>
      <div ref={friendsContainerRef} className={`${styles.roomsContainer} dc-scroll-dark`}>
        <div className={styles.sectionHeader} style={{ cursor: "default" }}>
          <span>친구</span>
          {totalFriendsCount > 0 && <span className={styles.sectionCount}>{totalFriendsCount}</span>}
        </div>
        {loading && friends.length === 0 ? (
          <div className={styles.loadingState}>친구 목록 불러오는 중...</div>
        ) : !loading && friends.length === 0 ? (
          <div className={styles.emptyState}>아직 친구가 없어요.<br />아이디로 친구를 찾아보세요.</div>
        ) : (
          <>
            {friends.map(renderFriendItem)}
            {isLoadingMore && <div className={styles.loadingState}>불러오는 중...</div>}
          </>
        )}

        <button type="button" className={styles.addRow} onClick={handleFindUser}>
          <span className={styles.addIcon}><UserPlus size={13} /></span>
          친구 찾기
        </button>
      </div>
      {showFindUserModal && (
        <FindUserModal onClose={() => setShowFindUserModal(false)} onSendFriendRequest={handleSendFriendRequest} />
      )}
    </>
  )
}

export default FriendsSidebar
