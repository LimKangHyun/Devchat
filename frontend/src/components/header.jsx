"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { Bell, Check, X } from "lucide-react"
import styles from "./header.module.css"
import axiosInstance from "./api/axiosInstance"
import { Link } from "react-router-dom"
import useWebSocketNotifications from "./common/useWebSocket"

export function HeaderWithNotifications() {
  const [profileImage, setProfileImage] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [username, setUsername] = useState("")

  // Notification states
  const [realtimeNotifications, setRealtimeNotifications] = useState([]) // WebSocket notifications (real-time only)
  const [apiNotifications, setApiNotifications] = useState([]) // API notifications (paginated)
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const [isLoadingNotifications, setIsLoadingNotifications] = useState(false)
  const [processingRequestId, setProcessingRequestId] = useState(null)

  // Pagination states
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMoreNotifications, setHasMoreNotifications] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [totalApiNotificationCount, setTotalApiNotificationCount] = useState(0)

  // UI states
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false)
  const [isShaking, setIsShaking] = useState(false)
  const audioRef = useRef(null)
  const notificationRef = useRef(null)
  const notificationContentRef = useRef(null)

  // WebSocket notification handler - only for real-time notifications
  const handleNotificationReceived = useCallback((notification) => {
    console.log("🔔 Processing new real-time notification:", notification)

    playNotificationSound()

    setTotalApiNotificationCount((prev) => prev + 1)
    showImmediateNotification(notification)
    triggerShakeAnimation()
    setHasUnreadNotifications(true)
  }, [])

  // Initialize WebSocket connection only once when username is available
  useEffect(() => {
    if (username) {
      // Initialize WebSocket connection immediately when username is available
      // This will be handled by the useWebSocketNotifications hook
      console.log("🔌 Initializing WebSocket for user:", username)
      fetchApiNotifications(0, true)
    }
  }, [username]) // Only depend on username

  // WebSocket hook call - moved outside of any conditional logic
  useWebSocketNotifications({
    username: username,
    onNotificationReceived: handleNotificationReceived,
  })

  // Show immediate notification popup/toast
  const showImmediateNotification = (notification) => {
    const message = getNotificationMessage(notification)

    if (Notification.permission === "granted") {
      new Notification("DevChat Notification", {
        body: message,
        icon: notification.senderImg
          ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
          : "/images/not-found-profile.png",
      })
    }
  }

  // Get notification message based on type
  const getNotificationMessage = (notification) => {
    switch (notification.type) {
      case "FRIEND_REQUESTED":
        return `${notification.sender}님이 친구 요청을 보냈습니다.`
      case "CODE_REVIEW":
        return `${notification.sender}님이 코드 리뷰를 요청했습니다.`
      default:
        return `${notification.sender}님으로부터 새 알림이 있습니다.`
    }
  }

  // Get notification type badge
  const getNotificationTypeBadge = (type) => {
    switch (type) {
      case "FRIEND_REQUESTED":
        return <span className={styles.notificationTypeBadge}>친구 요청</span>
      case "CODE_REVIEW":
        return <span className={styles.notificationTypeBadge}>코드 리뷰</span>
      default:
        return <span className={styles.notificationTypeBadge}>알림</span>
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

  // Trigger shake animation
  const triggerShakeAnimation = () => {
    setIsShaking(true)
    setTimeout(() => setIsShaking(false), 1000)
  }

  // Check if there are any unread notifications
  const hasUnreadItems = () => {
    return realtimeNotifications.some((n) => n.isNew) || hasUnreadNotifications
  }

  // Fetch paginated notifications from API
  const fetchApiNotifications = async (page = 0, reset = false) => {
    try {
      if (reset) {
        setIsLoadingNotifications(true)
      } else {
        setIsLoadingMore(true)
      }

      const response = await axiosInstance.get(`/notification?page=${page}&size=10`)
      const { content, totalElements, last } = response.data

      // Transform API response to match our notification format
      const transformedNotifications = content.map((notification, index) => ({
        id: `api-${page}-${index}-${notification.referenceId || "no-ref"}`,
        type: notification.type,
        sender: notification.sender,
        senderImg: notification.senderImg,
        referenceId: notification.referenceId,
        timestamp: notification.createdAt || new Date().toISOString(),
        isNew: false, // API notifications are considered read
        isRealtime: false,
      }))

      if (reset) {
        setApiNotifications(transformedNotifications)
        setCurrentPage(0)
      } else {
        setApiNotifications((prev) => [...prev, ...transformedNotifications])
      }

      setHasMoreNotifications(!last)
      setTotalApiNotificationCount(totalElements)
      setCurrentPage(page)
    } catch (err) {
      console.error("Error fetching API notifications:", err)
    } finally {
      setIsLoadingNotifications(false)
      setIsLoadingMore(false)
    }
  }

  // Load more notifications
  const loadMoreNotifications = useCallback(() => {
    if (!isLoadingMore && hasMoreNotifications) {
      fetchApiNotifications(currentPage + 1, false)
    }
  }, [currentPage, isLoadingMore, hasMoreNotifications])

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

      // Use referenceId for API call
      const requestId = notification.referenceId
      await axiosInstance.post(`/friend/request/${requestId}/accept`)

      // Remove from appropriate notification list
      if (notification.isRealtime) {
        setRealtimeNotifications((prev) => prev.filter((n) => n.id !== notification.id))
      } else {
        setApiNotifications((prev) => prev.filter((n) => n.id !== notification.id))
        setTotalApiNotificationCount((prev) => prev - 1)
      }

      window.dispatchEvent(new CustomEvent("friend-request-accepted"))
    } catch (err) {
      console.error("Error accepting friend request:", err)
      const message =
        err?.response?.data?.message 
      alert(message || "친구 요청 수락에 실패했습니다.")
    } finally {
      setProcessingRequestId(null)
    }
  }

  // Handle reject friend request
  const handleRejectRequest = async (notification) => {
    try {
      setProcessingRequestId(notification.id)

      // Use referenceId for API call
      const requestId = notification.referenceId || notification.senderId
      await axiosInstance.post(`/friend-requests/${requestId}/reject`)

      // Remove from appropriate notification list
      if (notification.isRealtime) {
        setRealtimeNotifications((prev) => prev.filter((n) => n.id !== notification.id))
      } else {
        setApiNotifications((prev) => prev.filter((n) => n.id !== notification.id))
        setTotalApiNotificationCount((prev) => prev - 1)
      }
    } catch (err) {
      console.error("Error rejecting friend request:", err)
      alert("친구 요청 거절에 실패했습니다.")
    } finally {
      setProcessingRequestId(null)
    }
  }

  // Render notification item
  const renderNotificationItem = (notification) => {
    const isFriendRequest = notification.type === "FRIEND_REQUESTED"

    if (isFriendRequest) {
      return (
        <div
          key={notification.id}
          className={`${styles.friendRequestItem} ${notification.isNew ? styles.friendRequestNew : ""}`}
        >
          <div className={styles.requestUserInfo}>
            <img
              src={
                notification.senderImg
                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
                  : "/images/not-found-profile.png"
              }
              alt={notification.sender}
              className={styles.requestProfileImage}
              onError={(e) => {
                e.currentTarget.src = "/images/not-found-profile.png"
              }}
            />
            <div className={styles.requestDetails}>
              <div className={styles.requestHeader}>
                {getNotificationTypeBadge(notification.type)}
                <span className={styles.requestTime}>
                  {new Date(notification.timestamp).toLocaleDateString("ko-KR")}
                </span>
              </div>
              <div className={styles.requestMessage}>
                <span className={styles.requestSenderName}>{notification.sender}님이 친구 요청을 보냈습니다.</span>
              </div>
            </div>
          </div>

          <div className={styles.requestActionsWrapper}>
            {notification.isNew && <div className={styles.notificationNewIndicator}></div>}
            <div className={styles.requestActions}>
              <button
                className={styles.acceptBtn}
                onClick={() => handleAcceptRequest(notification)}
                disabled={processingRequestId === notification.id}
                aria-label="수락"
              >
                <Check size={16} />
              </button>
              <button
                className={styles.rejectBtn}
                onClick={() => handleRejectRequest(notification)}
                disabled={processingRequestId === notification.id}
                aria-label="거절"
              >
                <X size={16} />
              </button>
            </div>
          </div>
        </div>
      )
    }

    // Regular notification
    return (
      <div
        key={notification.id}
        className={`${styles.notificationItem} ${notification.isNew ? styles.notificationNew : ""}`}
      >
        <div className={styles.notificationContentWrapper}>
          <div className={styles.notificationUserInfo}>
            <img
              src={
                notification.senderImg
                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
                  : "/images/not-found-profile.png"
              }
              alt={notification.sender}
              className={styles.notificationProfileImage}
              onError={(e) => {
                e.currentTarget.src = "/images/not-found-profile.png"
              }}
            />
            <div className={styles.notificationDetails}>
              <div className={styles.notificationHeader}>
                {getNotificationTypeBadge(notification.type)}
                <span className={styles.notificationTime}>
                  {new Date(notification.timestamp).toLocaleString("ko-KR")}
                </span>
              </div>
              <div className={styles.notificationMessage}>
                <span>{getNotificationMessage(notification)}</span>
              </div>
            </div>
          </div>
          {notification.isNew && <div className={styles.notificationNewIndicator}></div>}
        </div>
      </div>
    )
  }

  // Toggle notification dropdown
  const toggleNotifications = () => {
    if (!isNotificationOpen) {
      // Fetch API notifications when opening
      fetchApiNotifications(0, true)
      // Clear unread status when opening notifications
      setHasUnreadNotifications(false)
    }
    setIsNotificationOpen(!isNotificationOpen)
  }

  // Combine and sort all notifications (realtime first, then API)
  const allNotifications = [...realtimeNotifications, ...apiNotifications]
  const totalNotificationCount = realtimeNotifications.length + totalApiNotificationCount

  // Rest of the useEffect hooks remain the same...
  useEffect(() => {
    const fetchUserData = async () => {
      try {
        setIsLoading(true)
        const { data } = await axiosInstance.get("/user/details")
        setProfileImage(data.profileImg)
        setUsername(data.username || data.email)
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
    <header className={styles.header}>
      <div className={styles.container}>
        <Link to="/">
          <img src="/images/devchat-logo.png" alt="DevChat Logo" className={styles.headerLogoImage} />
        </Link>

        <div className={styles.profileContainer}>
          {/* Notification Bell */}
          <div className={styles.notificationContainer} ref={notificationRef}>
            <button
              className={`${styles.notificationBell} ${isShaking || hasUnreadItems() ? styles.shake : ""}`}
              onClick={toggleNotifications}
              aria-label="알림"
            >
              <Bell size={20} />
              {totalNotificationCount > 0 && (
                <span className={styles.notificationBadge}>
                  {totalNotificationCount > 99 ? "99+" : totalNotificationCount}
                </span>
              )}
            </button>

            {/* Notification Dropdown */}
            {isNotificationOpen && (
              <div className={styles.notificationDropdown}>
                <div className={styles.notificationHeader}>
                  <h3>Notifications</h3>
                </div>

                <div className={styles.notificationContent} ref={notificationContentRef} onScroll={handleScroll}>
                  {isLoadingNotifications ? (
                    <div className={styles.notificationLoading}>
                      <div className={styles.loadingSpinner}></div>
                      <span>로딩 중...</span>
                    </div>
                  ) : allNotifications.length === 0 ? (
                    <div className={styles.noNotifications}>
                      <div className={styles.noNotificationsIcon}>🎉</div>
                      <span className={styles.noNotificationsTitle}>All caught up!</span>
                      <span className={styles.noNotificationsSubtitle}>No notifications.</span>
                    </div>
                  ) : (
                    <div className={styles.notificationsList}>
                      {allNotifications.map(renderNotificationItem)}

                      {/* Loading more indicator */}
                      {isLoadingMore && (
                        <div className={styles.loadingMore}>
                          <div className={styles.loadingSpinner}></div>
                          <span>더 많은 알림을 불러오는 중...</span>
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
            className={styles.logoutText}
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
  )
}

export default HeaderWithNotifications
