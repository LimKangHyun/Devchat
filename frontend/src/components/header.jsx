"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { flushSync } from "react-dom"
import { Bell, Check, X, ArrowRight, User, Code, MessageSquare, Clock } from "lucide-react"
import styles from "./header.module.css"
import axiosInstance from "./api/axiosInstance"
import { Link } from "react-router-dom"
import useWebSocketNotifications from "./common/useWebSocket"
import { FriendsListModal } from "./chat/friends-list-modal"
import { ChatModal } from "./chat/chat-modal"
import { ChatButton } from "./chat/chat-button"

export function HeaderWithNotifications() {
  const [profileImage, setProfileImage] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [username, setUsername] = useState("")

  // Notification states
  const [apiNotifications, setApiNotifications] = useState([])
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const [isLoadingNotifications, setIsLoadingNotifications] = useState(false)
  const [processingRequestId, setProcessingRequestId] = useState(null)

  // Filter states
  const [notificationFilter, setNotificationFilter] = useState("all")

  // Pagination states
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMoreNotifications, setHasMoreNotifications] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)

  // Count states
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0)
  const [realtimeNotificationCount, setRealtimeNotificationCount] = useState(0)

  // Chat states
  const [isFriendsListOpen, setIsFriendsListOpen] = useState(false)
  const [openChats, setOpenChats] = useState([])
  const [currentUser, setCurrentUser] = useState(null)

  // UI states
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false)
  const [isShaking, setIsShaking] = useState(false)
  const audioRef = useRef(null)
  const notificationRef = useRef(null)
  const notificationContentRef = useRef(null)

  // WebSocket notification handler
  const handleNotificationReceived = useCallback((notification) => {
    console.log("🔔 Processing new real-time notification:", notification)

    playNotificationSound()
    triggerShakeAnimation()

    flushSync(() => {
      setRealtimeNotificationCount((prev) => prev + 1)
    })

    showImmediateNotification(notification)
    setHasUnreadNotifications(true)
  }, [])

  // Fetch ONLY unread count
  const fetchUnreadCount = async () => {
    try {
      console.log("📊 Fetching unread notification count...")
      const response = await axiosInstance.get("/notification/unread?page=0&size=1")
      const unreadCount = response.data.totalElements

      setUnreadNotificationCount(unreadCount)
      console.log("📊 Unread count updated:", unreadCount)
    } catch (err) {
      console.error("Error fetching unread count:", err)
    }
  }

  const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

  // Initialize unread count when username is available
  useEffect(() => {
    if (username) {
      console.log("🔌 Initializing for user:", username)
      fetchUnreadCount()
    }
  }, [username])

  // WebSocket hook call
  useWebSocketNotifications({
    username: username,
    onNotificationReceived: handleNotificationReceived,
  })

  // Show immediate notification popup/toast
  const showImmediateNotification = (notification) => {
    const message = notification.content
    if (!message) {
      console.warn("❗ notification.content가 비어있음:", notification)
      return
    }

    if (Notification.permission === "granted") {
      new Notification("DevChat Notification", {
        body: message,
        icon: notification.senderImg
          ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
          : "/images/not-found-profile.png",
      })
    }
  }

  // Get notification type info
  const getNotificationTypeInfo = (type) => {
    switch (type) {
      case "FRIEND_REQUESTED":
        return {
          label: "Friend Request",
          icon: <User size={14} />,
          color: "#10b981",
          bgColor: "#ecfdf5",
        }
      case "CODE_REVIEW":
        return {
          label: "Code Review",
          icon: <Code size={14} />,
          color: "#3b82f6",
          bgColor: "#eff6ff",
        }
      case "MESSAGE":
        return {
          label: "Message",
          icon: <MessageSquare size={14} />,
          color: "#8b5cf6",
          bgColor: "#f3e8ff",
        }
      default:
        return {
          label: "Notification",
          icon: <Bell size={14} />,
          color: "#6b7280",
          bgColor: "#f9fafb",
        }
    }
  }

  // Play notification sound
  const playNotificationSound = () => {
    if (audioRef.current) {
      audioRef.current.currentTime = 0
      audioRef.current.play().catch((error) => {
        console.log("Could not play notification sound:", error)
      })
    }
  }

  // Enhanced shake animation with more intensity
  const triggerShakeAnimation = () => {
    console.log("🔔 Triggering enhanced shake animation")
    setIsShaking(true)
    setTimeout(() => {
      setIsShaking(false)
      console.log("🔔 Enhanced shake animation ended")
    }, 1500)
  }

  // Check if there are any unread notifications
  const hasUnreadItems = () => {
    return hasUnreadNotifications || unreadNotificationCount > 0 || realtimeNotificationCount > 0
  }

  // Fetch notifications based on current filter
  const fetchNotifications = async (page = 0, reset = false, filter = notificationFilter) => {
    try {
      if (reset) {
        setIsLoadingNotifications(true)
      } else {
        setIsLoadingMore(true)
      }

      await delay(200)

      const endpoint =
        filter === "unread" ? `/notification/unread?page=${page}&size=10` : `/notification?page=${page}&size=10`

      console.log(`🔄 Fetching notifications from: ${endpoint} (filter: ${filter})`)

      const response = await axiosInstance.get(endpoint)
      const { content, totalElements, last } = response.data

      const transformedNotifications = content
        .map((notification, index) => ({
          id: `api-${filter}-${page}-${index}-${notification.referenceId || "no-ref"}-${Date.now()}`,
          type: notification.type,
          sender: notification.senderNickname || notification.senderUsername,
          senderImg: notification.senderImg,
          referenceId: notification.referenceId,
          content: notification.content,
          timestamp: notification.createdAt || new Date().toISOString(),
          isNew: !notification.isRead,
          isRead: notification.isRead,
          isRealtime: false,
        }))
        .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))

      if (reset) {
        setApiNotifications(transformedNotifications)
        setCurrentPage(0)
        setRealtimeNotificationCount(0)
      } else {
        setApiNotifications((prev) => [...prev, ...transformedNotifications])
      }

      setHasMoreNotifications(!last)
      setCurrentPage(page)
    } catch (err) {
      console.error("Error fetching notifications:", err)
    } finally {
      setIsLoadingNotifications(false)
      setIsLoadingMore(false)
    }
  }

  // Load more notifications
  const loadMoreNotifications = useCallback(() => {
    if (!isLoadingMore && hasMoreNotifications) {
      fetchNotifications(currentPage + 1, false)
    }
  }, [currentPage, isLoadingMore, hasMoreNotifications, notificationFilter])

  // Infinite scroll handler
  const handleScroll = useCallback(() => {
    if (!notificationContentRef.current) return

    const { scrollTop, scrollHeight, clientHeight } = notificationContentRef.current
    const isNearBottom = scrollTop + clientHeight >= scrollHeight - 100

    if (isNearBottom && hasMoreNotifications && !isLoadingMore) {
      loadMoreNotifications()
    }
  }, [hasMoreNotifications, isLoadingMore, loadMoreNotifications])

  // Handle accept friend request
  const handleAcceptRequest = async (notification) => {
    try {
      setProcessingRequestId(notification.id)

      const requestId = notification.referenceId
      await axiosInstance.post(`/friend/request/${requestId}/accept`)

      flushSync(() => {
        setApiNotifications((prev) => prev.filter((n) => n.id !== notification.id))
        setUnreadNotificationCount((prev) => Math.max(0, prev - 1))
      })

      window.dispatchEvent(new CustomEvent("friend-request-accepted"))
    } catch (err) {
      console.error("Error accepting friend request:", err)
      const message = err?.response?.data?.message
      alert(message || "친구 요청 수락에 실패했습니다.")
    } finally {
      setProcessingRequestId(null)
    }
  }

  // Handle reject friend request
  const handleRejectRequest = async (notification) => {
    try {
      setProcessingRequestId(notification.id)

      const requestId = notification.referenceId
      await axiosInstance.post(`/friend/request/${requestId}/reject`)

      flushSync(() => {
        setApiNotifications((prev) => prev.filter((n) => n.id !== notification.id))
        setUnreadNotificationCount((prev) => Math.max(0, prev - 1))
      })
    } catch (err) {
      console.error("Error rejecting friend request:", err)
      alert("친구 요청 거절에 실패했습니다.")
    } finally {
      setProcessingRequestId(null)
    }
  }

  // Handle navigation for non-friend-request notifications
  const handleNotificationNavigation = (notification) => {
    switch (notification.type) {
      case "CODE_REVIEW":
        window.location.href = `/code-review/${notification.referenceId}`
        break
      case "MESSAGE":
        // Open chat modal with the sender
        const friend = {
          username: notification.sender,
          nickname: notification.sender,
          status: "online",
          avatar: notification.senderImg,
        }
        openChatModal(friend)
        break
      default:
        console.log("Navigation not implemented for type:", notification.type)
    }
    setIsNotificationOpen(false)
  }

  // Chat functions
  const openChatModal = (friend) => {
    const existingChat = openChats.find((chat) => chat.friend.username === friend.username)
    if (!existingChat) {
      setOpenChats((prev) => [...prev, { id: Date.now(), friend }])
    }
  }

  const closeChatModal = (chatId) => {
    setOpenChats((prev) => prev.filter((chat) => chat.id !== chatId))
  }

  const handleStartChat = (friend) => {
    openChatModal(friend)
  }

  // Format relative time
  const formatRelativeTime = (timestamp) => {
    const now = new Date()
    const time = new Date(timestamp)
    const diffInMinutes = Math.floor((now - time) / (1000 * 60))

    if (diffInMinutes < 1) return "방금 전"
    if (diffInMinutes < 60) return `${diffInMinutes}분 전`
    if (diffInMinutes < 1440) return `${Math.floor(diffInMinutes / 60)}시간 전`
    return `${Math.floor(diffInMinutes / 1440)}일 전`
  }

  // Render notification item
  const renderNotificationItem = (notification) => {
    const isFriendRequest = notification.type === "FRIEND_REQUESTED"
    const typeInfo = getNotificationTypeInfo(notification.type)
    const showActionButtons = isFriendRequest && !notification.isRead

    if (isFriendRequest) {
      return (
        <div key={notification.id} className={styles.notificationItem}>
          <div className={styles.notificationContent}>
            <div className={styles.notificationAvatar}>
              <img
                src={
                  notification.senderImg
                    ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
                    : "/images/not-found-profile.png"
                }
                alt={notification.sender}
                className={styles.avatarImage}
                onError={(e) => {
                  e.currentTarget.src = "/images/not-found-profile.png"
                }}
              />
              {notificationFilter === "unread" && <div className={styles.newIndicator}></div>}
            </div>

            <div className={styles.notificationBody}>
              <div className={styles.notificationHeader}>
                <div className={styles.badgeContainer}>
                  <span
                    className={styles.notificationBadge}
                    style={{
                      color: typeInfo.color,
                      backgroundColor: typeInfo.bgColor,
                    }}
                  >
                    {typeInfo.icon}
                    {typeInfo.label}
                  </span>
                </div>
                <div className={styles.timestampContainer}>
                  <Clock size={12} />
                  <span className={styles.notificationTime}>{formatRelativeTime(notification.timestamp)}</span>
                </div>
              </div>

              <div className={styles.notificationMessage}>
                <strong className={styles.senderName}>{notification.sender}</strong>
                <div className={styles.messageText}>{notification.content}</div>
              </div>
            </div>

            {showActionButtons && (
              <div className={styles.notificationActions}>
                <button
                  className={`${styles.actionButton} ${styles.acceptButton}`}
                  onClick={() => handleAcceptRequest(notification)}
                  disabled={processingRequestId === notification.id}
                >
                  {processingRequestId === notification.id ? (
                    <div className={styles.buttonSpinner}></div>
                  ) : (
                    <Check size={16} />
                  )}
                </button>
                <button
                  className={`${styles.actionButton} ${styles.rejectButton}`}
                  onClick={() => handleRejectRequest(notification)}
                  disabled={processingRequestId === notification.id}
                >
                  {processingRequestId === notification.id ? (
                    <div className={styles.buttonSpinner}></div>
                  ) : (
                    <X size={16} />
                  )}
                </button>
              </div>
            )}
          </div>
        </div>
      )
    }

    // Regular notification with navigation
    return (
      <div key={notification.id} className={styles.notificationItem}>
        <div className={styles.notificationContent}>
          <div className={styles.notificationAvatar}>
            <img
              src={
                notification.senderImg
                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
                  : "/images/not-found-profile.png"
              }
              alt={notification.sender}
              className={styles.avatarImage}
              onError={(e) => {
                e.currentTarget.src = "/images/not-found-profile.png"
              }}
            />
            {notificationFilter === "unread" && <div className={styles.newIndicator}></div>}
          </div>

          <div className={styles.notificationBody}>
            <div className={styles.notificationHeader}>
              <div className={styles.badgeContainer}>
                <span
                  className={styles.notificationBadge}
                  style={{
                    color: typeInfo.color,
                    backgroundColor: typeInfo.bgColor,
                  }}
                >
                  {typeInfo.icon}
                  {typeInfo.label}
                </span>
              </div>
              <div className={styles.timestampContainer}>
                <Clock size={12} />
                <span className={styles.notificationTime}>{formatRelativeTime(notification.timestamp)}</span>
              </div>
            </div>

            <div className={styles.notificationMessage}>{notification.content}</div>
          </div>

          <button className={styles.navigationButton} onClick={() => handleNotificationNavigation(notification)}>
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    )
  }

  // Toggle notification dropdown
  const toggleNotifications = () => {
    if (!isNotificationOpen) {
      fetchNotifications(0, true)
    }
    setIsNotificationOpen(!isNotificationOpen)
  }

  // Handle filter change
  const handleFilterChange = () => {
    const newFilter = notificationFilter === "all" ? "unread" : "all"
    setNotificationFilter(newFilter)
    fetchNotifications(0, true, newFilter)
  }

  // Calculate display count
  const getDisplayCount = () => {
    return unreadNotificationCount + realtimeNotificationCount
  }

  const displayCount = getDisplayCount()

  console.log(
    "🔄 State Debug - Unread:",
    unreadNotificationCount,
    "Realtime:",
    realtimeNotificationCount,
    "Display:",
    displayCount,
    "Filter:",
    notificationFilter,
    "Notifications:",
    apiNotifications.length,
  )

  // Rest of the useEffect hooks
  useEffect(() => {
    const fetchUserData = async () => {
      try {
        setIsLoading(true)
        const { data } = await axiosInstance.get("/user/details")
        setProfileImage(data.profileImg)
        setUsername(data.username || data.email)
        setCurrentUser(data)
        setIsLoading(false)
      } catch (err) {
        console.error("Error fetching user data:", err)
        setError(err.message)
        setIsLoading(false)
      }
    }

    fetchUserData()

    const handler = () => {
      console.log("🔥 Header 이벤트 수신됨")
      fetchUserData()
    }

    window.addEventListener("profile-updated", handler)
    return () => window.removeEventListener("profile-updated", handler)
  }, [])

  useEffect(() => {
    if ("Notification" in window && Notification.permission === "default") {
      Notification.requestPermission()
    }
  }, [])

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (notificationRef.current && !notificationRef.current.contains(event.target)) {
        setIsNotificationOpen(false)
      }
    }

    if (isNotificationOpen) {
      document.addEventListener("mousedown", handleClickOutside)
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
    }
  }, [isNotificationOpen])

  useEffect(() => {
    const contentElement = notificationContentRef.current
    if (contentElement && isNotificationOpen) {
      contentElement.addEventListener("scroll", handleScroll)
      return () => contentElement.removeEventListener("scroll", handleScroll)
    }
  }, [isNotificationOpen, handleScroll])

  return (
    <>
      <header className={styles.header}>
        <div className={styles.container}>
          <Link to="/">
            <img src="/images/devchat-logo.png" alt="DevChat Logo" className={styles.headerLogoImage} />
          </Link>

          <div className={styles.profileContainer}>
            {/* Chat Button */}
            <ChatButton onClick={() => setIsFriendsListOpen(true)} />

            {/* Notification Bell */}
            <div className={styles.notificationContainer} ref={notificationRef}>
              <button
                className={`${styles.notificationBell} ${isShaking || hasUnreadItems() ? styles.shake : ""}`}
                onClick={toggleNotifications}
                aria-label="알림"
              >
                <Bell size={20} />
                {displayCount > 0 && (
                  <span key={`count-${displayCount}`} className={styles.notificationCount}>
                    {displayCount > 99 ? "99+" : displayCount}
                  </span>
                )}
              </button>

              {/* Notification Dropdown */}
              {isNotificationOpen && (
                <div className={styles.notificationDropdown}>
                  <div className={styles.notificationDropdownHeader}>
                    <div className={styles.headerLeft}>
                      <h3>Notifications</h3>
                      {apiNotifications.length > 0 && (
                        <span className={styles.totalCount}>{apiNotifications.length}</span>
                      )}
                    </div>
                    <div className={styles.toggleContainer}>
                      <span className={styles.toggleLabel}>Unread only</span>
                      <button
                        className={`${styles.toggleSwitch} ${notificationFilter === "unread" ? styles.toggleSwitchActive : ""}`}
                        onClick={handleFilterChange}
                        aria-label={`Switch to ${notificationFilter === "all" ? "unread only" : "view all"} notifications`}
                      >
                        <span className={styles.toggleSlider}></span>
                      </button>
                    </div>
                  </div>

                  <div className={styles.notificationList} ref={notificationContentRef}>
                    {isLoadingNotifications ? (
                      <div className={styles.loadingState}>
                        <div className={styles.loadingSpinner}></div>
                        <span>Loading notifications...</span>
                      </div>
                    ) : apiNotifications.length === 0 ? (
                      <div className={styles.emptyState}>
                        <Bell size={48} className={styles.emptyIcon} />
                        <h4>{notificationFilter === "unread" ? "No unread notifications!" : "All caught up!"}</h4>
                        <p>
                          {notificationFilter === "unread"
                            ? "You have no unread notifications"
                            : "You have no new notifications"}
                        </p>
                      </div>
                    ) : (
                      <div className={styles.notificationsList}>
                        {apiNotifications.map(renderNotificationItem)}

                        {isLoadingMore && (
                          <div className={styles.loadingMore}>
                            <div className={styles.loadingSpinner}></div>
                            <span>Loading more...</span>
                          </div>
                        )}

                        {!hasMoreNotifications && apiNotifications.length > 0 && (
                          <div className={styles.endMessage}>
                            <span>You're all caught up!</span>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* Profile Section */}
            {isLoading ? (
              <div className={styles.profileImageLoading}></div>
            ) : error ? (
              <div className={styles.profileImageError}>
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
              </div>
            ) : (
              <Link to="/myprofile">
                <img
                  src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${profileImage}`}
                  alt="User profile"
                  className={styles.profileImage}
                  onError={(e) => {
                    e.currentTarget.src = "/images/not-found-profile.png"
                  }}
                />
              </Link>
            )}

            <button
              className={styles.logoutButton}
              style={{
                background: "none",
                border: "none",
                padding: 0,
                margin: 0,
                cursor: "pointer",
                fontWeight: 500,
              }}
              onClick={async () => {
                try {
                  const response = await axiosInstance.post("/logout", {})
                  if (response.status === 204) {
                    alert("로그아웃 되었습니다.")
                    window.location.href = "/login"
                  } else {
                    alert("로그아웃 실패")
                  }
                } catch (error) {
                  console.error("로그아웃 요청 실패:", error)
                  alert("서버 오류로 로그아웃 실패")
                }
              }}
            >
              Log Out
            </button>
          </div>
        </div>
        <audio ref={audioRef} preload="auto">
          <source src="/sounds/notification.mp3" type="audio/mpeg" />
          <source src="/sounds/notification.wav" type="audio/wav" />
        </audio>
      </header>

      {/* Friends List Modal */}
      <FriendsListModal
        isOpen={isFriendsListOpen}
        onClose={() => setIsFriendsListOpen(false)}
        onStartChat={handleStartChat}
        currentUser={currentUser}
      />

      {/* Chat Modals */}
      {openChats.map((chat) => (
        <ChatModal
          key={chat.id}
          isOpen={true}
          onClose={() => closeChatModal(chat.id)}
          friend={chat.friend}
          currentUser={currentUser}
        />
      ))}
    </>
  )
}

export default HeaderWithNotifications
