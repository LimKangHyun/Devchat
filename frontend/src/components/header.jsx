"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { flushSync } from "react-dom"
import { Bell, Check, X, ArrowRight, User, Code, MessageSquare, Clock } from "lucide-react"
import styles from "./header.module.css"
import axiosInstance from "./api/axiosInstance"
import { Link } from "react-router-dom"
import useWebSocketNotifications from "./common/useWebSocket"
window.__devchatActiveNoti ??= null;  // 현재 떠 있는 Notification 인스턴스 보관

export function HeaderWithNotifications() {
  const [profileImage, setProfileImage] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [username, setUsername] = useState("")
  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const profileDropdownRef = useRef(null)

  // Notification states
  const [apiNotifications, setApiNotifications] = useState([])
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const [isLoadingNotifications, setIsLoadingNotifications] = useState(false)
  const [markingAsReadId, setMarkingAsReadId] = useState(null)
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
  const [currentUser, setCurrentUser] = useState(null)
  const [openChatUsernames, setOpenChatUsernames] = useState(new Set())
  const openChatUsernamesRef = useRef(new Set()) // Add this ref

  // UI states
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false)
  const [isShaking, setIsShaking] = useState(false)
  const audioRef = useRef(null)
  const notificationRef = useRef(null)
  const notificationContentRef = useRef(null)

  // WebSocket notification handler
  const handleNotificationReceived = useCallback(
    (notification) => {
      console.log("🔔 Processing new real-time notification:", notification)

      if (notification.type === "NEW_DM") {
        const senderUsername = notification.senderUsername
        // Use the ref for a synchronous, up-to-date check
        if (openChatUsernamesRef.current.has(senderUsername)) {
          console.log(`📨 NEW_DM from ${senderUsername} received, but chat is open. Suppressing alert.`)
          return
        }

        console.log(`📨 NEW_DM from ${senderUsername} received, chat is closed. Showing alert.`)
        playNotificationSound()
        showImmediateNotification(notification)
        setHasUnreadNotifications(true)

        window.dispatchEvent(
          new CustomEvent("new-dm-for-sidebar", {
            detail: { senderUsername: senderUsername },
          }),
        )
        return
      }

      playNotificationSound()
      triggerShakeAnimation()

      flushSync(() => {
        setRealtimeNotificationCount((prev) => prev + 1)
      })

      showImmediateNotification(notification)
      setHasUnreadNotifications(true)
    },
    [], // The dependency array is now empty, making the function stable.
  )

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

  // 채팅 메세지 수신
  useEffect(() => {

    const handleExternalNotify = (e) => {
      const { senderNickname, body, senderImg, url, tag, silent, roomName } = e.detail || {}
      if (!silent) playNotificationSound()
      console.log("이벤트 수신완료");

      showImmediateNotification(
        {
          type: "CHAT_MESSAGE",
          senderNickname: senderNickname,
          senderImg: senderImg,
          content: body,
          roomName: roomName,
          url: url
        },
      )
    }

    window.addEventListener("chat:notify", handleExternalNotify)
    return () => window.removeEventListener("chat:notify", handleExternalNotify)
  }, [])


  const showImmediateNotification = (notification) => {
    const message = notification.content
    if (!message) {
      console.warn("❗ notification.content가 비어있음:", notification)
      return
    }

    const getTitleByType = (type) => {
      switch (type) {
        case "FRIEND_REQUESTED":
          return "👋 새로운 친구 요청이 도착했습니다!"
        case "FRIEND_ACCEPTED":
          return "✅ 친구 요청이 수락되었습니다!"
        case "FRIEND_REJECTED":
          return "❌ 친구 요청이 거절되었습니다."
        case "WE_ARE_FRIEND_NOW":
          return "🎉 친구가 되었습니다!"
        case "NEW_DM":
          return `💬 ${notification.senderNickname}`
        case "CODE_REVIEW":
          return "🧪 코드 리뷰 알림이 있습니다."
        case "CHAT_MESSAGE":
          return `💬 ${notification.senderNickname} (${notification.roomName})`
        default:
          return "🔔 DevChat 알림"
      }
    }

    if (Notification.permission === "granted") {
      // 1) 지금 떠 있는 알림(있으면) 무조건 닫기 → 토스트 재표시 강제
      if (window.__devchatActiveNoti) {
        console.log("이전 noti 존재")
        try { window.__devchatActiveNoti.onclose = null; } catch {}
        try { window.__devchatActiveNoti.close(); } catch {}
        window.__devchatActiveNoti = null;
      }

      // 2) 새 알림 생성
      const noti = new Notification(
        getTitleByType(notification.type), {
        body: message,
        icon: notification.senderImg
          ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
          : "/images/not-found-profile.png",
      });

      // 3) 클릭 시 이동 (현재 chat_message에만 적용)
      if(notification.url){
        noti.onclick = () => {
        try { window.focus() } catch {}
        window.location.href = notification.url
        noti.close()
        }
      }
      
      // 4) 새 알림을 전역에 보관
      window.__devchatActiveNoti = noti;

      // 5) 사용자가 닫으면 포인터 정리
      noti.onclose = () => {
        if (window.__devchatActiveNoti === noti) {
          window.__devchatActiveNoti = null;
        }
      };

      return noti;
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
      case "WE_ARE_FRIEND_NOW": // Added based on new server format
        return {
          label: "New Friend",
          icon: <User size={14} />,
          color: "#10b981", // Same as friend request for consistency, or choose a new one
          bgColor: "#ecfdf5",
        }
      default: // This will catch 'NOTIFICATION' or any other types
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
        .map((notification) => ({
          // Removed index from map as notificationId should be unique
          id: notification.notificationId, // Use server's notificationId as the primary client-side ID
          type: notification.type,
          sender: notification.senderNickname || notification.senderUsername,
          senderImg: notification.senderImg,
          referenceId: notification.referenceId, // This is used for specific actions like friend requests or navigating to a code review
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
      setProcessingRequestId(notification.id) // notification.id here is notificationId from server

      // For friend requests, referenceId is the ID of the friend request itself
      const friendRequestId = notification.referenceId
      await axiosInstance.post(`/friend/request/${friendRequestId}/accept`)

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
      setProcessingRequestId(notification.id) // notification.id here is notificationId from server

      const friendRequestId = notification.referenceId
      await axiosInstance.post(`/friend/request/${friendRequestId}/reject`)

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
    // For these types, referenceId usually points to the entity (e.g., code review ID, chat ID)
    const entityId = notification.referenceId
    switch (notification.type) {
      case "CODE_REVIEW":
        window.location.href = `/code-review/${entityId}`
        break
      case "MESSAGE":
        console.log(
          "Header: 'MESSAGE' notification clicked for",
          notification.sender,
          "with referenceId (chat/user ID):",
          entityId,
          "- chat opening should be handled by ChatManager now.",
        )
        // Example: if (props.openChatFromHeader) { props.openChatFromHeader(entityId); }
        break
      default:
        console.log("Navigation not implemented for type:", notification.type, "or no referenceId for navigation.")
    }
    setIsNotificationOpen(false)
  }

  // Handle mark notification as read
  const handleMarkAsRead = async (notification) => {
    // For marking a notification as read, we use its own ID (notification.id, which is notificationId from server)
    if (!notification.id) {
      // Check if notification.id (notificationId from server) exists
      console.error("Cannot mark notification as read: missing valid notification.id", notification)
      alert("Error: This notification cannot be marked as read due to missing information.")
      return
    }
    try {
      setMarkingAsReadId(notification.id)
      // API endpoint uses the notification's own ID
      await axiosInstance.post(`/notification/read/${notification.id}`)

      flushSync(() => {
        setApiNotifications((prev) =>
          prev.map((n) => (n.id === notification.id ? { ...n, isRead: true, isNew: false } : n)),
        )
        if (!notification.isRead) {
          // if it was unread before this action
          setUnreadNotificationCount((prev) => Math.max(0, prev - 1))
        }
        if (notificationFilter === "unread") {
          // If on "unread" tab, remove it after marking as read
          setApiNotifications((prev) => prev.filter((n) => n.id !== notification.id))
        }
      })
    } catch (err) {
      console.error("Error marking notification as read:", err)
      alert("Failed to mark notification as read.")
    } finally {
      setMarkingAsReadId(null)
    }
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
    const showActionButtonsForFriendRequest = isFriendRequest && !notification.isRead

    // Determine if this notification type should have a "mark as read" button
    // These types typically have direct navigation or specific actions rather than just "mark as read"
    const typesWithoutMarkAsReadButton = ["FRIEND_REQUESTED", "CODE_REVIEW", "MESSAGE"]
    const canMarkAsRead = !typesWithoutMarkAsReadButton.includes(notification.type) && !notification.isRead

    let actionElement = null

    if (showActionButtonsForFriendRequest) {
      actionElement = (
        <div className={styles.notificationActions}>
          <button
            className={`${styles.actionButton} ${styles.acceptButton}`}
            onClick={() => handleAcceptRequest(notification)}
            disabled={processingRequestId === notification.id}
            aria-label="Accept friend request"
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
            aria-label="Reject friend request"
          >
            {processingRequestId === notification.id ? <div className={styles.buttonSpinner}></div> : <X size={16} />}
          </button>
        </div>
      )
    } else if (canMarkAsRead) {
      actionElement = (
        <button
          className={`${styles.actionButton} ${styles.markAsReadButton}`}
          onClick={() => handleMarkAsRead(notification)}
          disabled={markingAsReadId === notification.id}
          aria-label="Mark as read"
        >
          {markingAsReadId === notification.id ? <div className={styles.buttonSpinner}></div> : <Check size={16} />}
        </button>
      )
    } else if (notification.type === "CODE_REVIEW" || notification.type === "MESSAGE") {
      // Navigation for specific types if not a friend request and not eligible for mark as read
      actionElement = (
        <button
          className={styles.navigationButton}
          onClick={() => handleNotificationNavigation(notification)}
          aria-label={`Navigate to ${typeInfo.label}`}
        >
          <ArrowRight size={16} />
        </button>
      )
    } else if (!notification.isRead && !typesWithoutMarkAsReadButton.includes(notification.type)) {
      // Fallback for other unread notifications that might need a mark as read button
      // This handles types like "WE_ARE_FRIEND_NOW" or generic "NOTIFICATION"
      actionElement = (
        <button
          className={`${styles.actionButton} ${styles.markAsReadButton}`}
          onClick={() => handleMarkAsRead(notification)}
          disabled={markingAsReadId === notification.id}
          aria-label="Mark as read"
        >
          {markingAsReadId === notification.id ? <div className={styles.buttonSpinner}></div> : <Check size={16} />}
        </button>
      )
    } else if (notification.isRead && !typesWithoutMarkAsReadButton.includes(notification.type)) {
      // Already read, show a disabled/confirmed checkmark for generic types
      actionElement = (
        <button
          className={`${styles.actionButton} ${styles.markAsReadButton}`}
          disabled={true}
          aria-label="Marked as read"
          style={{ opacity: 0.6, cursor: "default" }}
        >
          <Check size={16} />
        </button>
      )
    }

    return (
      <div key={notification.id} className={styles.notificationItem}>
        <div className={styles.notificationContent}>
          <div className={styles.notificationAvatar}>
            <img
              src={
                notification.senderImg
                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL || "/placeholder.svg"}/${notification.senderImg}`
                  : "/images/not-found-profile.png"
              }
              alt={notification.sender}
              className={styles.avatarImage}
              onError={(e) => {
                e.currentTarget.src = "/images/not-found-profile.png"
              }}
            />
            {notificationFilter === "unread" && !notification.isRead && <div className={styles.newIndicator}></div>}
          </div>

          <div className={styles.notificationBody}>
            <div className={styles.notificationHeader}>
              <div className={styles.badgeContainer}>
                <span
                  className={styles.notificationBadge}
                  style={{ color: typeInfo.color, backgroundColor: typeInfo.bgColor }}
                >
                  {typeInfo.icon}
                  <span className={styles.badgeText}>{typeInfo.label}</span>
                </span>
              </div>
              <div className={styles.timestampContainer}>
                <Clock size={12} className={styles.clockIcon} />
                <span className={styles.notificationTime}>{formatRelativeTime(notification.timestamp)}</span>
              </div>
            </div>
            <div className={styles.notificationMessage}>{notification.content}</div>
          </div>
          {actionElement}
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
    const handleChatModalStatus = (event) => {
      const { action, username } = event.detail
      setOpenChatUsernames((prev) => {
        const newSet = new Set(prev)
        if (action === "open") {
          newSet.add(username)
        } else {
          newSet.delete(username)
        }
        // Also update the ref synchronously
        openChatUsernamesRef.current = newSet
        return newSet
      })
    }

    window.addEventListener("chat-modal-status", handleChatModalStatus)
    return () => window.removeEventListener("chat-modal-status", handleChatModalStatus)
  }, [])

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

        {/* 왼쪽: 로고 + 커뮤니티 */}
        <div className={styles.leftSection}>
          <Link to="/" className={styles.logoLink}>
            <img src="/images/devchat-logo.png" alt="DevChat Logo" className={styles.headerLogoImage} />
          </Link>
          <div className={styles.divider} />
          <Link
            to="/community"
            className={`${styles.communityTab} ${window.location.pathname.startsWith('/community') ? styles.communityTabActive : ''}`}
          >
            Community
          </Link>
        </div>

        {/* 오른쪽: 알림 + 프로필 드롭다운 */}
        <div className={styles.rightSection}>

          {/* 알림 벨 - 기존 코드 그대로 */}
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

            {/* 알림 드롭다운 - 기존 코드 그대로 */}
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
                      <p>{notificationFilter === "unread" ? "You have no unread notifications" : "You have no new notifications"}</p>
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
                        <div className={styles.endMessage}><span>You're all caught up!</span></div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* 프로필 드롭다운 */}
          <div className={styles.profileDropdownWrapper} ref={profileDropdownRef}>
            <button
              className={styles.profileTrigger}
              onClick={() => setIsProfileOpen(prev => !prev)}
            >
              {isLoading ? (
                <div className={styles.profileImageLoading} />
              ) : (
                <img
                  src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${profileImage}`}
                  alt="User profile"
                  className={styles.profileImage}
                  onError={(e) => { e.currentTarget.src = "/images/not-found-profile.png" }}
                />
              )}
              <span className={`${styles.profileChevron} ${isProfileOpen ? styles.profileChevronOpen : ''}`}>
                ▾
              </span>
            </button>

            {isProfileOpen && (
              <div className={styles.profileDropdown}>
                {/* 상단 유저 정보 */}
                <div className={styles.profileDropdownUser}>
                  <img
                    src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${profileImage}`}
                    alt="profile"
                    className={styles.profileDropdownAvatar}
                    onError={(e) => { e.currentTarget.src = "/images/not-found-profile.png" }}
                  />
                  <div>
                    <p className={styles.profileDropdownName}>{username}</p>
                    <p className={styles.profileDropdownSub}>개발자</p>
                  </div>
                </div>

                <div className={styles.profileDropdownDivider} />

                {/* 메뉴 아이템 */}
                <Link
                  to="/myprofile"
                  className={styles.profileDropdownItem}
                  onClick={() => setIsProfileOpen(false)}
                >
                  <span className={styles.profileDropdownIcon}>👤</span>
                  내 프로필
                </Link>

                <div className={styles.profileDropdownDivider} />

                <button
                  className={`${styles.profileDropdownItem} ${styles.profileDropdownLogout}`}
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
                  <span className={styles.profileDropdownIcon}>🚪</span>
                  로그아웃
                </button>
              </div>
            )}
          </div>

        </div>
      </div>
      <audio ref={audioRef} preload="auto">
        <source src="/sounds/notification2.mp3" type="audio/mpeg" />
      </audio>
    </header>
    </>
  )
}

export default HeaderWithNotifications
