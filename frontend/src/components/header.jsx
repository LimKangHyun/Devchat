"use client"

import { useState, useEffect, useRef } from "react"
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
  const [friendRequests, setFriendRequests] = useState([])
  const [notifications, setNotifications] = useState([]) // New state for WebSocket notifications
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const [isLoadingRequests, setIsLoadingRequests] = useState(false)
  const [processingRequestId, setProcessingRequestId] = useState(null)

  // Add these new state variables after the existing state declarations
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false)
  const [isShaking, setIsShaking] = useState(false)
  const audioRef = useRef(null)

  const notificationRef = useRef(null)

  // WebSocket notification handler
  const handleNotificationReceived = (notification) => {
    console.log("🔔 Processing new notification:", notification)

    // Play notification sound
    playNotificationSound()

    // If it's a friend request, add it to friend requests instead of notifications
    if (notification.type === "FRIEND_REQUESTED") {
      const friendRequest = {
        id: notification.id || `${Date.now()}-${Math.random()}`,
        senderId: notification.referenceId?.toString() || "unknown",
        senderName: notification.sender,
        senderProfileImg: notification.senderImg,
        createdAt: new Date().toISOString(),
        isNew: true,
      }

      setFriendRequests((prev) => [friendRequest, ...prev])

      // Mark as not new after 5 seconds
      setTimeout(() => {
        setFriendRequests((prev) => prev.map((req) => (req.id === friendRequest.id ? { ...req, isNew: false } : req)))
      }, 5000)
    } else {
      // Handle other notification types
      const newNotification = {
        ...notification,
        isNew: true,
      }

      setNotifications((prev) => [newNotification, ...prev])

      // Mark as not new after 5 seconds
      setTimeout(() => {
        setNotifications((prev) => prev.map((n) => (n.id === notification.id ? { ...n, isNew: false } : n)))
      }, 5000)
    }

    // Show immediate notification
    showImmediateNotification(notification)

    // Trigger shake animation and set unread flag
    triggerShakeAnimation()
    setHasUnreadNotifications(true)
  }

  // Show immediate notification popup/toast
  const showImmediateNotification = (notification) => {
    const message = getNotificationMessage(notification)

    // You can replace this with a proper toast library
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
    setTimeout(() => setIsShaking(false), 1000) // Stop shaking after 1 second
  }

  // Check if there are any unread notifications
  const hasUnreadItems = () => {
    const unreadNotifications = notifications.some((n) => n.isNew)
    const unreadFriendRequests = friendRequests.some((req) => req.isNew)
    return unreadNotifications || unreadFriendRequests
  }

  // Initialize WebSocket connection
  useWebSocketNotifications({
    username: username,
    onNotificationReceived: handleNotificationReceived,
  })

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        setIsLoading(true)
        const { data } = await axiosInstance.get("/user/details")
        setProfileImage(data.profileImg)
        setUsername(data.username || data.email) // Adjust based on your API response
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

  // Request notification permission
  useEffect(() => {
    if ("Notification" in window && Notification.permission === "default") {
      Notification.requestPermission()
    }
  }, [])

  // Fetch friend requests
  const fetchNotifications = async () => {
    try {
      setIsLoadingRequests(true)

      // For testing - use mock data
      await new Promise((resolve) => setTimeout(resolve, 200))

      // Uncomment this for real API call:
      // const { data } = await axiosInstance.get("/friend-requests/pending")
      // setFriendRequests(data.requests || [])
    } catch (err) {
      console.error("Error fetching friend requests:", err)
    } finally {
      setIsLoadingRequests(false)
    }
  }

  // Handle accept friend request
  const handleAcceptRequest = async (requestId) => {
    try {
      setProcessingRequestId(requestId)
      await axiosInstance.post(`/friend-requests/${requestId}/accept`)

      setFriendRequests((prev) => prev.filter((req) => req.id !== requestId))
      window.dispatchEvent(new CustomEvent("friend-request-accepted"))
    } catch (err) {
      console.error("Error accepting friend request:", err)
      alert("친구 요청 수락에 실패했습니다.")
    } finally {
      setProcessingRequestId(null)
    }
  }

  // Handle reject friend request
  const handleRejectRequest = async (requestId) => {
    try {
      setProcessingRequestId(requestId)
      await axiosInstance.post(`/friend-requests/${requestId}/reject`)

      setFriendRequests((prev) => prev.filter((req) => req.id !== requestId))
    } catch (err) {
      console.error("Error rejecting friend request:", err)
      alert("친구 요청 거절에 실패했습니다.")
    } finally {
      setProcessingRequestId(null)
    }
  }

  // Toggle notification dropdown
  const toggleNotifications = () => {
    if (!isNotificationOpen) {
      fetchNotifications()
      // Clear unread status when opening notifications
      setHasUnreadNotifications(false)
    }
    setIsNotificationOpen(!isNotificationOpen)
  }

  // Close dropdown when clicking outside
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

  // Calculate total notification count
  const totalNotificationCount = friendRequests.length + notifications.length

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
              {totalNotificationCount > 0 && <span className={styles.notificationBadge}>{totalNotificationCount}</span>}
            </button>

            {/* Notification Dropdown */}
            {isNotificationOpen && (
              <div className={styles.notificationDropdown}>
                <div className={styles.notificationHeader}>
                  <h3>Notifications</h3>
                </div>

                <div className={styles.notificationContent}>
                  {isLoadingRequests ? (
                    <div className={styles.notificationLoading}>
                      <div className={styles.loadingSpinner}></div>
                      <span>로딩 중...</span>
                    </div>
                  ) : totalNotificationCount === 0 ? (
                    <div className={styles.noNotifications}>
                      <div className={styles.noNotificationsIcon}>🎉</div>
                      <span className={styles.noNotificationsTitle}>All caught up!</span>
                      <span className={styles.noNotificationsSubtitle}>No notifications.</span>
                    </div>
                  ) : (
                    <div className={styles.notificationsList}>
                      {/* WebSocket Notifications */}
                      {notifications.map((notification) => (
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
                                    {notification.timestamp && new Date(notification.timestamp).toLocaleString("ko-KR")}
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
                      ))}

                      {/* Friend Requests */}
                      {friendRequests.map((request) => (
                        <div
                          key={request.id}
                          className={`${styles.friendRequestItem} ${request.isNew ? styles.friendRequestNew : ""}`}
                        >
                          <div className={styles.requestUserInfo}>
                            <img
                              src={
                                request.senderProfileImg
                                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${request.senderProfileImg}`
                                  : "/images/not-found-profile.png"
                              }
                              alt={request.senderName}
                              className={styles.requestProfileImage}
                              onError={(e) => {
                                e.currentTarget.src = "/images/not-found-profile.png"
                              }}
                            />
                            <div className={styles.requestDetails}>
                              <div className={styles.requestHeader}>
                                <span className={styles.notificationTypeBadge}>친구 요청</span>
                                <span className={styles.requestTime}>
                                  {new Date(request.createdAt).toLocaleDateString("ko-KR")}
                                </span>
                              </div>
                              <div className={styles.requestMessage}>
                                <span className={styles.requestSenderName}>
                                  {request.senderName}님이 친구 요청을 보냈습니다.
                                </span>
                              </div>
                            </div>
                          </div>

                          <div className={styles.requestActionsWrapper}>
                            {request.isNew && <div className={styles.notificationNewIndicator}></div>}
                            <div className={styles.requestActions}>
                              <button
                                className={styles.acceptBtn}
                                onClick={() => handleAcceptRequest(request.id)}
                                disabled={processingRequestId === request.id}
                                aria-label="수락"
                              >
                                <Check size={16} />
                              </button>
                              <button
                                className={styles.rejectBtn}
                                onClick={() => handleRejectRequest(request.id)}
                                disabled={processingRequestId === request.id}
                                aria-label="거절"
                              >
                                <X size={16} />
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
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
      {/* Add this audio element before the closing </header> tag */}
      <audio ref={audioRef} preload="auto">
        <source src="/sounds/notification.mp3" type="audio/mpeg" />
        <source src="/sounds/notification.wav" type="audio/wav" />
      </audio>
    </header>
  )
}

export default HeaderWithNotifications
