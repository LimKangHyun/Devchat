"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { flushSync } from "react-dom"
import { Bell, Check, X, ArrowRight, User, Code, MessageSquare, Clock } from "lucide-react"
import styles from "./header.module.css"
import axiosInstance from "./api/axiosInstance"
import { Link } from "react-router-dom"
import { useUser } from '../context/UserContext'
import { useAlarm } from '../context/AlarmContext'
import useWebSocketNotifications from "./common/useWebSocket"

window.__devchatActiveNoti ??= null;

export function HeaderWithNotifications() {
  const { currentUser, isLoading } = useUser()
  const { currentRoomId } = useAlarm()

  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const profileDropdownRef = useRef(null)

  const [apiNotifications, setApiNotifications] = useState([])
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const [isLoadingNotifications, setIsLoadingNotifications] = useState(false)
  const [markingAsReadId, setMarkingAsReadId] = useState(null)
  const [processingRequestId, setProcessingRequestId] = useState(null)

  const [notificationFilter, setNotificationFilter] = useState("all")

  const [currentPage, setCurrentPage] = useState(0)
  const [hasMoreNotifications, setHasMoreNotifications] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)

  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0)
  const [realtimeNotificationCount, setRealtimeNotificationCount] = useState(0)

  const [openChatUsernames, setOpenChatUsernames] = useState(new Set())
  const openChatUsernamesRef = useRef(new Set())

  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false)
  const [isShaking, setIsShaking] = useState(false)
  const audioRef = useRef(null)
  const notificationRef = useRef(null)
  const notificationContentRef = useRef(null)

  const handleNotificationReceived = useCallback((notification) => {
    console.log("🔔 Processing new real-time notification:", notification)

    if (notification.type === "NEW_DM") {
      const senderUsername = notification.senderUsername
      if (openChatUsernamesRef.current.has(senderUsername)) {
        console.log(`📨 NEW_DM from ${senderUsername} received, but chat is open. Suppressing alert.`)
        return
      }
      playNotificationSound()
      showImmediateNotification(notification)
      setHasUnreadNotifications(true)
      window.dispatchEvent(new CustomEvent("new-dm-for-sidebar", {
        detail: { senderUsername },
      }))
      return
    }

    playNotificationSound()
    triggerShakeAnimation()
    flushSync(() => {
      setRealtimeNotificationCount((prev) => prev + 1)
    })
    showImmediateNotification(notification)
    setHasUnreadNotifications(true)
  }, [])

  const fetchUnreadCount = async () => {
    try {
      const response = await axiosInstance.get("/notification/unread?page=0&size=1")
      setUnreadNotificationCount(response.data.totalElements)
    } catch (err) {
      console.error("Error fetching unread count:", err)
    }
  }

  const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

  useEffect(() => {
    if (currentUser?.username) {
      fetchUnreadCount()
    }
  }, [currentUser?.username])

  useWebSocketNotifications({
    username: currentUser?.username,
    onNotificationReceived: handleNotificationReceived,
  })

  useEffect(() => {
    const handleExternalNotify = (e) => {
      const { senderNickname, body, senderImg, url, tag, silent, roomName } = e.detail || {}
      if (!silent) playNotificationSound()
      showImmediateNotification({
        type: "CHAT_MESSAGE",
        senderNickname,
        senderImg,
        content: body,
        roomName,
        url,
      })
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
        case "FRIEND_REQUESTED": return "👋 새로운 친구 요청이 도착했습니다!"
        case "FRIEND_ACCEPTED": return "✅ 친구 요청이 수락되었습니다!"
        case "FRIEND_REJECTED": return "❌ 친구 요청이 거절되었습니다."
        case "WE_ARE_FRIEND_NOW": return "🎉 친구가 되었습니다!"
        case "NEW_DM": return `💬 ${notification.senderNickname}`
        case "CODE_REVIEW": return "🧪 코드 리뷰 알림이 있습니다."
        case "CHAT_MESSAGE": return `💬 ${notification.senderNickname} (${notification.roomName})`
        case "STUDY_APPLY": return "📬 새로운 스터디 신청이 도착했습니다!"
        case "STUDY_APPROVED": return "🎉 스터디 신청이 승인되었습니다!"
        case "STUDY_REJECTED": return "😢 스터디 신청이 거절되었습니다."
        default: return "🔔 DevChat 알림"
      }
    }

    if (Notification.permission === "granted") {
      if (window.__devchatActiveNoti) {
        try { window.__devchatActiveNoti.onclose = null; } catch {}
        try { window.__devchatActiveNoti.close(); } catch {}
        window.__devchatActiveNoti = null;
      }

      const noti = new Notification(getTitleByType(notification.type), {
        body: message,
        icon: notification.senderImg
          ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
          : "/images/not-found-profile.png",
      })

      if (notification.url) {
        noti.onclick = () => {
          try { window.focus() } catch {}
          window.location.href = notification.url
          noti.close()
        }
      }

      window.__devchatActiveNoti = noti;
      noti.onclose = () => {
        if (window.__devchatActiveNoti === noti) {
          window.__devchatActiveNoti = null;
        }
      }
      return noti;
    }
  }

  const getNotificationTypeInfo = (type) => {
    switch (type) {
      case "FRIEND_REQUESTED": return { label: "Friend Request", icon: <User size={14} />, color: "#10b981", bgColor: "#ecfdf5" }
      case "CODE_REVIEW": return { label: "Code Review", icon: <Code size={14} />, color: "#3b82f6", bgColor: "#eff6ff" }
      case "MESSAGE": return { label: "Message", icon: <MessageSquare size={14} />, color: "#8b5cf6", bgColor: "#f3e8ff" }
      case "WE_ARE_FRIEND_NOW": return { label: "New Friend", icon: <User size={14} />, color: "#10b981", bgColor: "#ecfdf5" }
      case "STUDY_APPLY": return { label: "Study Apply", icon: <User size={14} />, color: "#2588F1", bgColor: "#ebf4ff" }
      case "STUDY_APPROVED": return { label: "Study Approved", icon: <Check size={14} />, color: "#10b981", bgColor: "#ecfdf5" }
      case "STUDY_REJECTED": return { label: "Study Rejected", icon: <X size={14} />, color: "#ef4444", bgColor: "#fef2f2" }
      default: return { label: "Notification", icon: <Bell size={14} />, color: "#6b7280", bgColor: "#f9fafb" }
    }
  }

  const playNotificationSound = () => {
    if (audioRef.current) {
      audioRef.current.currentTime = 0
      audioRef.current.play().catch((error) => {
        console.log("Could not play notification sound:", error)
      })
    }
  }

  const triggerShakeAnimation = () => {
    setIsShaking(true)
    setTimeout(() => setIsShaking(false), 1500)
  }

  const hasUnreadItems = () => {
    return hasUnreadNotifications || unreadNotificationCount > 0 || realtimeNotificationCount > 0
  }

  const fetchNotifications = async (page = 0, reset = false, filter = notificationFilter) => {
    try {
      if (reset) setIsLoadingNotifications(true)
      else setIsLoadingMore(true)

      await delay(200)

      const endpoint = filter === "unread"
        ? `/notification/unread?page=${page}&size=10`
        : `/notification?page=${page}&size=10`

      const response = await axiosInstance.get(endpoint)
      const { content, last } = response.data

      const transformedNotifications = content.map((notification) => ({
        id: notification.notificationId,
        type: notification.type,
        sender: notification.senderNickname || notification.senderUsername,
        senderImg: notification.senderImg,
        referenceId: notification.referenceId,
        content: notification.content,
        timestamp: notification.createdAt || new Date().toISOString(),
        isNew: !notification.isRead,
        isRead: notification.isRead,
        isRealtime: false,
      })).sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))

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

  const loadMoreNotifications = useCallback(() => {
    if (!isLoadingMore && hasMoreNotifications) {
      fetchNotifications(currentPage + 1, false)
    }
  }, [currentPage, isLoadingMore, hasMoreNotifications, notificationFilter])

  const handleScroll = useCallback(() => {
    if (!notificationContentRef.current) return
    const { scrollTop, scrollHeight, clientHeight } = notificationContentRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100 && hasMoreNotifications && !isLoadingMore) {
      loadMoreNotifications()
    }
  }, [hasMoreNotifications, isLoadingMore, loadMoreNotifications])

  const handleAcceptRequest = async (notification) => {
    try {
      setProcessingRequestId(notification.id)
      await axiosInstance.post(`/friend/request/${notification.referenceId}/accept`)
      flushSync(() => {
        setApiNotifications((prev) => prev.filter((n) => n.id !== notification.id))
        setUnreadNotificationCount((prev) => Math.max(0, prev - 1))
      })
      window.dispatchEvent(new CustomEvent("friend-request-accepted"))
    } catch (err) {
      console.error("Error accepting friend request:", err)
      alert(err?.response?.data?.message || "친구 요청 수락에 실패했습니다.")
    } finally {
      setProcessingRequestId(null)
    }
  }

  const handleRejectRequest = async (notification) => {
    try {
      setProcessingRequestId(notification.id)
      await axiosInstance.post(`/friend/request/${notification.referenceId}/reject`)
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

  const handleNotificationNavigation = async (notification) => {
    if (!notification.isRead && notification.id) {
      try {
        await axiosInstance.post(`/notification/read/${notification.id}`)
        setApiNotifications((prev) =>
          prev.map((n) => (n.id === notification.id ? { ...n, isRead: true, isNew: false } : n))
        )
        setUnreadNotificationCount((prev) => Math.max(0, prev - 1))
      } catch (err) {
        console.error("읽음 처리 실패:", err)
      }
    }
    const entityId = notification.referenceId
    switch (notification.type) {
      case "CODE_REVIEW": window.location.href = `/code-review/${entityId}`; break
      case "STUDY_APPLY": window.location.href = `/community/${entityId}/applicants`; break
      case "STUDY_APPROVED":
      case "STUDY_REJECTED": window.location.href = `/community/${entityId}`; break
      default: break
    }
    setIsNotificationOpen(false)
  }

  const handleMarkAsRead = async (notification) => {
    if (!notification.id) return
    try {
      setMarkingAsReadId(notification.id)
      await axiosInstance.post(`/notification/read/${notification.id}`)
      flushSync(() => {
        setApiNotifications((prev) =>
          prev.map((n) => (n.id === notification.id ? { ...n, isRead: true, isNew: false } : n))
        )
        if (!notification.isRead) {
          setUnreadNotificationCount((prev) => Math.max(0, prev - 1))
        }
        if (notificationFilter === "unread") {
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

  const formatRelativeTime = (timestamp) => {
    const now = new Date()
    const time = new Date(timestamp)
    const diffInMinutes = Math.floor((now - time) / (1000 * 60))
    if (diffInMinutes < 1) return "방금 전"
    if (diffInMinutes < 60) return `${diffInMinutes}분 전`
    if (diffInMinutes < 1440) return `${Math.floor(diffInMinutes / 60)}시간 전`
    return `${Math.floor(diffInMinutes / 1440)}일 전`
  }

  const renderNotificationItem = (notification) => {
    const isFriendRequest = notification.type === "FRIEND_REQUESTED"
    const typeInfo = getNotificationTypeInfo(notification.type)
    const showActionButtonsForFriendRequest = isFriendRequest && !notification.isRead
    const typesWithoutMarkAsReadButton = ["FRIEND_REQUESTED", "CODE_REVIEW", "MESSAGE", "STUDY_APPLY", "STUDY_APPROVED", "STUDY_REJECTED"]
    const canMarkAsRead = !typesWithoutMarkAsReadButton.includes(notification.type) && !notification.isRead

    let actionElement = null

    if (showActionButtonsForFriendRequest) {
      actionElement = (
        <div className={styles.notificationActions}>
          <button className={`${styles.actionButton} ${styles.acceptButton}`} onClick={() => handleAcceptRequest(notification)} disabled={processingRequestId === notification.id} aria-label="Accept friend request">
            {processingRequestId === notification.id ? <div className={styles.buttonSpinner}></div> : <Check size={16} />}
          </button>
          <button className={`${styles.actionButton} ${styles.rejectButton}`} onClick={() => handleRejectRequest(notification)} disabled={processingRequestId === notification.id} aria-label="Reject friend request">
            {processingRequestId === notification.id ? <div className={styles.buttonSpinner}></div> : <X size={16} />}
          </button>
        </div>
      )
    } else if (canMarkAsRead) {
      actionElement = (
        <button className={`${styles.actionButton} ${styles.markAsReadButton}`} onClick={() => handleMarkAsRead(notification)} disabled={markingAsReadId === notification.id} aria-label="Mark as read">
          {markingAsReadId === notification.id ? <div className={styles.buttonSpinner}></div> : <Check size={16} />}
        </button>
      )
    } else if (["CODE_REVIEW", "MESSAGE", "STUDY_APPLY", "STUDY_APPROVED", "STUDY_REJECTED"].includes(notification.type)) {
      actionElement = (
        <button className={styles.navigationButton} onClick={() => handleNotificationNavigation(notification)} aria-label={`Navigate to ${typeInfo.label}`}>
          <ArrowRight size={16} />
        </button>
      )
    } else if (!notification.isRead && !typesWithoutMarkAsReadButton.includes(notification.type)) {
      actionElement = (
        <button className={`${styles.actionButton} ${styles.markAsReadButton}`} onClick={() => handleMarkAsRead(notification)} disabled={markingAsReadId === notification.id} aria-label="Mark as read">
          {markingAsReadId === notification.id ? <div className={styles.buttonSpinner}></div> : <Check size={16} />}
        </button>
      )
    } else if (notification.isRead && !typesWithoutMarkAsReadButton.includes(notification.type)) {
      actionElement = (
        <button className={`${styles.actionButton} ${styles.markAsReadButton}`} disabled={true} aria-label="Marked as read" style={{ opacity: 0.6, cursor: "default" }}>
          <Check size={16} />
        </button>
      )
    }

    return (
      <div key={notification.id} className={styles.notificationItem}>
        <div className={styles.notificationContent}>
          <div className={styles.notificationAvatar}>
            <img
              src={notification.senderImg ? `${process.env.REACT_APP_PROFILE_IMAGE_URL || "/placeholder.svg"}/${notification.senderImg}` : "/images/not-found-profile.png"}
              alt={notification.sender}
              className={styles.avatarImage}
              onError={(e) => { e.currentTarget.src = "/images/not-found-profile.png" }}
            />
            {notificationFilter === "unread" && !notification.isRead && <div className={styles.newIndicator}></div>}
          </div>
          <div className={styles.notificationBody}>
            <div className={styles.notificationHeader}>
              <div className={styles.badgeContainer}>
                <span className={styles.notificationBadge} style={{ color: typeInfo.color, backgroundColor: typeInfo.bgColor }}>
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

  const toggleNotifications = () => {
    if (!isNotificationOpen) fetchNotifications(0, true)
    setIsNotificationOpen(!isNotificationOpen)
  }

  const handleFilterChange = () => {
    const newFilter = notificationFilter === "all" ? "unread" : "all"
    setNotificationFilter(newFilter)
    fetchNotifications(0, true, newFilter)
  }

  const handleLogout = async () => {
    try {
      // 현재 채팅방에 있으면 안읽음 카운트 동기화
      if (currentRoomId) {
        await axiosInstance.post(`/chat-rooms/${currentRoomId}/read`).catch(() => {})
      }
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
  }

  const displayCount = unreadNotificationCount + realtimeNotificationCount

  useEffect(() => {
    const handleChatModalStatus = (event) => {
      const { action, username } = event.detail
      setOpenChatUsernames((prev) => {
        const newSet = new Set(prev)
        if (action === "open") newSet.add(username)
        else newSet.delete(username)
        openChatUsernamesRef.current = newSet
        return newSet
      })
    }
    window.addEventListener("chat-modal-status", handleChatModalStatus)
    return () => window.removeEventListener("chat-modal-status", handleChatModalStatus)
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
    if (isNotificationOpen) document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
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
          <div className={styles.leftSection}>
            <Link to="/" className={styles.logoLink}>
              <img src="/images/devchat-logo.webp" alt="DevChat Logo" className={styles.headerLogoImage} />
            </Link>
            <div className={styles.divider} />
            <Link
              to="/community"
              className={`${styles.communityTab} ${window.location.pathname.startsWith('/community') ? styles.communityTabActive : ''}`}
            >
              Community
            </Link>
          </div>

          <div className={styles.rightSection}>
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

              {isNotificationOpen && (
                <div className={styles.notificationDropdown}>
                  <div className={styles.notificationDropdownHeader}>
                    <div className={styles.headerLeft}>
                      <h3>Notifications</h3>
                      {apiNotifications.length > 0 && <span className={styles.totalCount}>{apiNotifications.length}</span>}
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

            <div className={styles.profileDropdownWrapper} ref={profileDropdownRef}>
              <button className={styles.profileTrigger} onClick={() => setIsProfileOpen(prev => !prev)}>
                {isLoading ? (
                  <div className={styles.profileImageLoading} />
                ) : (
                  <img
                    src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${currentUser?.profileImg}`}
                    alt="User profile"
                    className={styles.profileImage}
                    onError={(e) => { e.currentTarget.src = "/images/not-found-profile.png" }}
                  />
                )}
                <span className={`${styles.profileChevron} ${isProfileOpen ? styles.profileChevronOpen : ''}`}>▾</span>
              </button>

              {isProfileOpen && (
                <div className={styles.profileDropdown}>
                  <div className={styles.profileDropdownUser}>
                    <img
                      src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${currentUser?.profileImg}`}
                      alt="profile"
                      className={styles.profileDropdownAvatar}
                      onError={(e) => { e.currentTarget.src = "/images/not-found-profile.png" }}
                    />
                    <div>
                      <p className={styles.profileDropdownName}>{currentUser?.username}</p>
                      <p className={styles.profileDropdownSub}>개발자</p>
                    </div>
                  </div>
                  <div className={styles.profileDropdownDivider} />
                  <Link to="/myprofile" className={styles.profileDropdownItem} onClick={() => setIsProfileOpen(false)}>
                    <span className={styles.profileDropdownIcon}>👤</span>
                    내 프로필
                  </Link>
                  <div className={styles.profileDropdownDivider} />
                  <button
                    className={`${styles.profileDropdownItem} ${styles.profileDropdownLogout}`}
                    onClick={handleLogout}
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
          <source src="/sounds/notification.mp3" type="audio/mpeg" />
        </audio>
      </header>
    </>
  )
}

export default HeaderWithNotifications