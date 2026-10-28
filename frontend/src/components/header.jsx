"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { flushSync, createPortal } from "react-dom"
import { Bell, Check, X, ArrowRight, User, Users, Code, MessageSquare, Newspaper, LogOut, UserRound, MessageSquareHeart, MonitorSmartphone } from "lucide-react"
import styles from "./header.module.css"
import axiosInstance from "./api/axiosInstance"
import { Link, useLocation, useNavigate } from "react-router-dom"
import { useUser } from '../context/UserContext'
import { useAlarm, clearRoomsCache } from '../context/AlarmContext'
import AppMenuItems from "./pwa/AppMenuItems"
import IOSInstallGuide from "./pwa/IOSInstallGuide"
import FeedbackModal from "./modals/FeedbackModal"
import LoginDevicesModal from "./modals/LoginDevicesModal"
import { safeRefreshToken } from "./api/refreshManager"
import { loginUrlFor } from "../utils/postLoginRedirect"
import { disablePush, syncPushSubscription } from "../utils/pushNotifications"
import { showAppNotification, closeSeenNotifications } from "../utils/showNotification"
import { clearAppBadge } from "../utils/appBadge"
import useWebSocketNotifications from "./common/useWebSocket"
import { useWebSocketContext } from "./common/WebSocketContext"



export function HeaderWithNotifications({ activePanel = "chat", onPanelChange, hidden = false }) {
  const { currentUser, isLoading } = useUser()
  const { currentRoomId, currentRoomIdRef, alarmRooms } = useAlarm()
  const location = useLocation()
  const navigate = useNavigate()

  // 멘션 알림의 referenceId(방 id) → 방 주소. 콜백이 오래된 목록을 잡지 않게 ref로 읽는다
  const alarmRoomsRef = useRef(alarmRooms)
  useEffect(() => { alarmRoomsRef.current = alarmRooms }, [alarmRooms])
  const roomUrlById = (roomId) => {
    const room = (alarmRoomsRef.current || []).find((r) => Number(r.uniqueId) === Number(roomId))
    return room?.inviteCode ? `/chat/${room.inviteCode}` : null
  }
  // 캐시에 없는 방(새로 초대된 방 등)이면 방 목록을 다시 받아 찾는다
  const resolveRoomUrl = async (roomId) => {
    const cached = roomUrlById(roomId)
    if (cached) return cached
    try {
      const res = await axiosInstance.get('/chat-rooms/all')
      const room = (res.data || []).find((r) => Number(r.roomId ?? r.id) === Number(roomId))
      return room?.inviteCode ? `/chat/${room.inviteCode}` : null
    } catch (err) {
      console.error("채팅방 주소 조회 실패:", err)
      return null
    }
  }

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

  // 방의 멘션 알림이 읽음 처리됐을 때 (이 창에서 방을 열었거나, 내 다른 창·기기에서 열었을 때)
  const syncAfterRoomRead = useCallback((roomId) => {
    setApiNotifications((prev) => prev.map((n) =>
      n.type === "MENTION" && Number(n.referenceId) === Number(roomId) ? { ...n, isRead: true, isNew: false } : n))
    setRealtimeNotificationCount(0)
    fetchUnreadCount()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const onRoomRead = (e) => syncAfterRoomRead(e.detail?.roomId)
    window.addEventListener("notifications:room-read", onRoomRead)
    return () => window.removeEventListener("notifications:room-read", onRoomRead)
  }, [syncAfterRoomRead])

  const { stompClientRef, connected } = useWebSocketContext() || {}
  useEffect(() => {
    const client = stompClientRef?.current
    if (!connected || !client?.connected) return
    const sub = client.subscribe("/user/queue/notification-sync", (frame) => {
      try {
        const data = JSON.parse(frame.body)
        if (data.type !== "READ_ROOM") return
        // 내 다른 창·기기에서 이 방을 봤다: 이 기기 알림 센터의 그 방 알림을 지운다
        closeSeenNotifications([{ roomId: data.roomId, at: Date.now() }])
        if (data.count > 0) syncAfterRoomRead(data.roomId)
      } catch (e) {
        console.error("알림 동기화 처리 실패", e)
      }
    })
    return () => { try { if (client.connected) sub.unsubscribe() } catch (e) {} }
  }, [connected, stompClientRef, syncAfterRoomRead])

  // 앱을 열거나 다시 보일 때: 다른 기기에서 이미 본 방의 알림을 알림 센터에서 지운다
  useEffect(() => {
    if (!currentUser?.username) return undefined
    const cleanUp = async () => {
      if (document.visibilityState !== "visible") return
      try {
        const { data } = await axiosInstance.get("/notification/seen-rooms")
        await closeSeenNotifications(data)
      } catch { /* 다음에 다시 정리한다 */ }
    }
    cleanUp()
    document.addEventListener("visibilitychange", cleanUp)
    return () => document.removeEventListener("visibilitychange", cleanUp)
  }, [currentUser?.username])

  const handleNotificationReceived = useCallback((notification) => {
    console.log("🔔 Processing new real-time notification:", notification)

    // 지금 보고 있는 방에서 나를 멘션했으면 이미 본 것이므로 바로 읽음 처리하고 알리지 않는다
    if (notification.type === "MENTION"
      && Number(currentRoomIdRef.current) === Number(notification.referenceId)
      && document.visibilityState === "visible") {
      axiosInstance.post(`/notification/read-room/${notification.referenceId}`).catch(() => {})
      return
    }

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
    if (notification.type === "MENTION") {
      showImmediateNotification({
        ...notification,
        url: roomUrlById(notification.referenceId),
        resolveUrl: () => resolveRoomUrl(notification.referenceId),
      })
      setHasUnreadNotifications(true)
      return
    }
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

  // 다른 기기에서 로그아웃시키면 refresh가 실패한다. 이 기기가 끊긴 거라면 로그인 화면으로 보낸다
  const handleSessionRevoked = useCallback(async () => {
    try {
      await safeRefreshToken()
    } catch (e) {
      // 네트워크 오류나 5xx면 그대로 둔다. 인증 거절일 때만 로그인 화면으로 보낸다
      if ([400, 401, 403].includes(e?.response?.status)) {
        window.location.replace(loginUrlFor(window.location.pathname + window.location.search))
      }
    }
  }, [])

  useWebSocketNotifications({
    username: currentUser?.username,
    onNotificationReceived: handleNotificationReceived,
    onSessionRevoked: handleSessionRevoked,
  })

  useEffect(() => {
    const handleExternalNotify = (e) => {
      const { senderNickname, body, senderImg, url, silent, roomName, roomId } = e.detail || {}
      if (!silent) playNotificationSound()
      showImmediateNotification({
        type: "CHAT_MESSAGE",
        senderNickname,
        senderImg,
        content: body,
        roomName,
        roomId,
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
        case "MENTION": return `📣 ${notification.senderNickname}님이 회원님을 언급했습니다`
        case "STUDY_APPLY": return "📬 새로운 스터디 신청이 도착했습니다!"
        case "STUDY_APPROVED": return "🎉 스터디 신청이 승인되었습니다!"
        case "STUDY_REJECTED": return "😢 스터디 신청이 거절되었습니다."
        default: return "🔔 DevChat 알림"
      }
    }

    if (!("Notification" in window) || Notification.permission !== "granted") return

    // 서버 푸시(NotificationPushDispatcher·ChatMessagePushListener)와 같은 tag를 써서
    // 같은 대화의 알림이 겹치지 않고 하나로 합쳐지게 한다
    const tag = notification.type === "CHAT_MESSAGE"
      ? `room-${notification.roomId}`
      : `${notification.type}-${notification.referenceId ?? ""}`

    ;(async () => {
      const url = notification.url || (notification.resolveUrl ? await notification.resolveUrl() : null)
      await showAppNotification({
        title: getTitleByType(notification.type),
        body: message,
        icon: notification.senderImg
          ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${notification.senderImg}`
          : "/images/not-found-profile.webp",
        tag,
        url,
      })
    })()
  }

  const getNotificationTypeInfo = (type) => {
    const neutral = { color: "var(--text-2)", bgColor: "var(--bg-muted)" }
    switch (type) {
      case "FRIEND_REQUESTED": return { label: "친구 요청", icon: <User size={12} />, ...neutral }
      case "CODE_REVIEW": return { label: "코드 리뷰", icon: <Code size={12} />, ...neutral }
      case "MESSAGE": return { label: "메시지", icon: <MessageSquare size={12} />, ...neutral }
      case "MENTION": return { label: "멘션", icon: <MessageSquare size={12} />, ...neutral }
      case "WE_ARE_FRIEND_NOW": return { label: "새 친구", icon: <User size={12} />, ...neutral }
      case "STUDY_APPLY": return { label: "스터디 신청", icon: <User size={12} />, ...neutral }
      case "STUDY_APPROVED": return { label: "스터디 승인", icon: <Check size={12} />, ...neutral }
      case "STUDY_REJECTED": return { label: "스터디 거절", icon: <X size={12} />, ...neutral }
      default: return { label: "알림", icon: <Bell size={12} />, ...neutral }
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
      case "MENTION": {
        const url = await resolveRoomUrl(entityId)
        if (url) navigate(url)
        else alert("채팅방을 찾을 수 없습니다. 방에서 나갔거나 삭제된 방일 수 있습니다.")
        break
      }
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
    const typesWithoutMarkAsReadButton = ["FRIEND_REQUESTED", "CODE_REVIEW", "MESSAGE", "MENTION", "STUDY_APPLY", "STUDY_APPROVED", "STUDY_REJECTED"]
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
    } else if (["CODE_REVIEW", "MESSAGE", "MENTION", "STUDY_APPLY", "STUDY_APPROVED", "STUDY_REJECTED"].includes(notification.type)) {
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
      <div key={notification.id} className={`${styles.notificationItem} ${!notification.isRead ? styles.notificationUnread : ""}`}>
        <div className={styles.notificationAvatar}>
          <img
            src={notification.senderImg ? `${process.env.REACT_APP_PROFILE_IMAGE_URL || "/placeholder.svg"}/${notification.senderImg}` : "/images/not-found-profile.webp"}
            alt={notification.sender}
            className={styles.avatarImage}
            onError={(e) => { e.currentTarget.src = "/images/not-found-profile.webp" }}
          />
        </div>
        <div className={styles.notificationBody}>
          <div className={styles.notificationHeader}>
            <span className={styles.notificationBadge} style={{ color: typeInfo.color, backgroundColor: typeInfo.bgColor }}>
              {typeInfo.icon}
              <span>{typeInfo.label}</span>
            </span>
            <span className={styles.notificationTime}>{formatRelativeTime(notification.timestamp)}</span>
            {!notification.isRead && <span className={styles.newIndicator} aria-label="읽지 않음" />}
          </div>
          <div className={styles.notificationMessage}>{notification.content}</div>
        </div>
        {actionElement}
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
      // 로그아웃한 기기로 이 계정의 휴대폰 알림이 계속 오지 않게 (로그아웃 전에, 인증이 살아 있을 때)
      await disablePush().catch(() => {})
      const response = await axiosInstance.post("/logout", {})
      if (response.status === 204) {
        // 같은 브라우저로 다른 계정이 로그인할 때 이전 계정의 방 목록이 남지 않게
        clearRoomsCache()
        clearAppBadge() // 로그아웃하면 앱 아이콘 숫자도 지운다
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
      Notification.requestPermission().then((p) => { if (p === "granted") syncPushSubscription() })
    }
  }, [])

  // 이미 알림을 허용한 기기면 로그인한 계정으로 휴대폰 알림 구독을 맞춘다 (로그아웃 때 해지되므로)
  useEffect(() => {
    if (currentUser?.username) syncPushSubscription()
  }, [currentUser?.username])

  const [showIOSGuide, setShowIOSGuide] = useState(false)
  const [showFeedback, setShowFeedback] = useState(false)
  const [showDevices, setShowDevices] = useState(false)

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

  useEffect(() => {
    const handleClickOutsideProfile = (event) => {
      if (profileDropdownRef.current && !profileDropdownRef.current.contains(event.target)) {
        setIsProfileOpen(false)
      }
    }
    if (isProfileOpen) document.addEventListener("mousedown", handleClickOutsideProfile)
    return () => document.removeEventListener("mousedown", handleClickOutsideProfile)
  }, [isProfileOpen])

  const isCommunity = location.pathname.startsWith("/community")
  const isProfilePage = location.pathname.startsWith("/myprofile")
  const chatUnread = (alarmRooms || []).reduce((sum, room) => {
    if (currentRoomId && Number(room.uniqueId) === Number(currentRoomId)) return sum
    return sum + (room.unreadCount ?? 0)
  }, 0)

  const goPanel = (panel) => {
    onPanelChange?.(panel)
    if (isCommunity || isProfilePage) navigate("/")
  }

  const railItemClass = (active) => `${styles.railItem} ${active ? styles.railItemActive : ""}`

  const renderBadge = (count) =>
    count > 0 ? <span className={styles.railBadge}>{count > 99 ? "99+" : count}</span> : null

  const profileSrc = `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${currentUser?.profileImg}`
  const onProfileError = (e) => { e.currentTarget.src = "/images/not-found-profile.webp" }
  const isChatTab = activePanel === "chat" && !isCommunity && !isProfilePage
  const isFriendsTab = activePanel === "friends" && !isCommunity && !isProfilePage

  return (
    <nav className={`${styles.rail} ${hidden ? styles.railHidden : ""}`}>
      <div className={styles.railTop}>
        <Link to="/" className={styles.railLogo} aria-label="DevChat 홈" title="DevChat" onClick={() => onPanelChange?.("chat")}>
          {/* 로고의 말풍선 + </> 마크를 흰색으로 */}
          <svg className={styles.railLogoSvg} viewBox="0 0 40 40" aria-hidden="true">
            <path d="M8 4h24a5 5 0 0 1 5 5v17a5 5 0 0 1-5 5H15l-7 6v-6a5 5 0 0 1-5-5V9a5 5 0 0 1 5-5z" fill="#fff" />
            <path d="M15 12.5l-5 5 5 5M25 12.5l5 5-5 5M22 11l-4 13" fill="none" stroke="#2588f1" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </Link>

        <div className={styles.railUtil}>
          <div className={styles.notificationContainer} ref={notificationRef}>
            <button
              type="button"
              className={`${railItemClass(isNotificationOpen)} ${styles.railUtilItem}`}
              onClick={toggleNotifications}
              aria-label="알림"
              title="알림"
            >
              <span className={`${styles.railIcon} ${isShaking ? styles.shake : ""}`}>
                <Bell size={18} />
                {displayCount > 0 ? (
                  <span key={`count-${displayCount}`} className={styles.railBadge}>
                    {displayCount > 99 ? "99+" : displayCount}
                  </span>
                ) : hasUnreadItems() ? (
                  <span className={styles.railDot} />
                ) : null}
              </span>
              <span className={`${styles.railLabel} ${styles.railLabelMobileOnly}`}>알림</span>
            </button>

            {isNotificationOpen && (
              <div className={styles.notificationDropdown}>
                <div className={styles.notificationDropdownHeader}>
                  <div className={styles.headerLeft}>
                    <h3>알림</h3>
                    {apiNotifications.length > 0 && <span className={styles.totalCount}>{apiNotifications.length}</span>}
                  </div>
                  <div className={styles.headerRight}>
                    <span className={styles.toggleLabel}>안 읽은 것만</span>
                    <button
                      type="button"
                      className={`${styles.toggleSwitch} ${notificationFilter === "unread" ? styles.toggleSwitchActive : ""}`}
                      onClick={handleFilterChange}
                      aria-pressed={notificationFilter === "unread"}
                      aria-label="안 읽은 알림만 보기"
                    >
                      <span className={styles.toggleSlider}></span>
                    </button>
                    <button type="button" className={styles.dropdownClose} onClick={() => setIsNotificationOpen(false)} aria-label="닫기">
                      <X size={18} />
                    </button>
                  </div>
                </div>
                <div className={`${styles.notificationList} dc-scroll`} ref={notificationContentRef}>
                  {isLoadingNotifications ? (
                    <div className={styles.loadingState}>
                      <div className={styles.loadingSpinner}></div>
                      <span>불러오는 중...</span>
                    </div>
                  ) : apiNotifications.length === 0 ? (
                    <div className={styles.emptyState}>
                      <Bell size={28} className={styles.emptyIcon} />
                      <h4>{notificationFilter === "unread" ? "안 읽은 알림이 없어요" : "받은 알림이 없어요"}</h4>
                      <p>친구 요청, 코드 리뷰, 스터디 소식이 오면 여기에 쌓여요.</p>
                    </div>
                  ) : (
                    <div className={styles.notificationsList}>
                      {apiNotifications.map(renderNotificationItem)}
                      {isLoadingMore && (
                        <div className={styles.loadingMore}>
                          <div className={styles.loadingSpinner}></div>
                          <span>더 불러오는 중...</span>
                        </div>
                      )}
                      {!hasMoreNotifications && apiNotifications.length > 0 && (
                        <div className={styles.endMessage}>여기까지가 전부예요</div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          <div className={styles.profileDropdownWrapper} ref={profileDropdownRef}>
            <button
              type="button"
              className={`${styles.railItem} ${styles.railProfile} ${styles.railUtilItem} ${isProfileOpen || isProfilePage ? styles.railItemActive : ""}`}
              onClick={() => setIsProfileOpen(prev => !prev)}
              aria-label="내 계정"
              title="내 계정"
            >
              <span className={styles.railIcon}>
                {isLoading ? (
                  <div className={styles.profileImageLoading} />
                ) : (
                  <img src={profileSrc} alt="" className={styles.railAvatar} onError={onProfileError} />
                )}
              </span>
              <span className={`${styles.railLabel} ${styles.railLabelMobileOnly}`}>나</span>
            </button>

            {isProfileOpen && (
              <>
                <div className={styles.sheetBackdrop} onClick={() => setIsProfileOpen(false)} />
                <div className={styles.profileDropdown}>
                  <div className={styles.profileDropdownUser}>
                    <img src={profileSrc} alt="" className={styles.profileDropdownAvatar} onError={onProfileError} />
                    <div style={{ minWidth: 0 }}>
                      <p className={styles.profileDropdownName}>{currentUser?.nickname || currentUser?.username}</p>
                      <p className={styles.profileDropdownSub}>@{currentUser?.username}</p>
                    </div>
                  </div>
                  <div className={styles.profileDropdownDivider} />
                  <Link to="/myprofile" className={styles.profileDropdownItem} onClick={() => setIsProfileOpen(false)}>
                    <UserRound size={16} />
                    내 프로필
                  </Link>
                  <AppMenuItems
                    itemClassName={styles.profileDropdownItem}
                    noteClassName={styles.profileDropdownNote}
                    onShowIOSGuide={() => setShowIOSGuide(true)}
                    onClose={() => setIsProfileOpen(false)}
                  />
                  <button type="button" className={styles.profileDropdownItem} onClick={() => { setIsProfileOpen(false); setShowDevices(true) }}>
                    <MonitorSmartphone size={16} />
                    로그인 기기
                  </button>
                  <button type="button" className={styles.profileDropdownItem} onClick={() => { setIsProfileOpen(false); setShowFeedback(true) }}>
                    <MessageSquareHeart size={16} />
                    피드백 보내기
                  </button>
                  <button type="button" className={`${styles.profileDropdownItem} ${styles.profileDropdownItemDanger}`} onClick={handleLogout}>
                    <LogOut size={16} />
                    로그아웃
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      <div className={styles.railGroup}>
        <button type="button" aria-current={isChatTab ? "page" : undefined} className={railItemClass(isChatTab)} onClick={() => goPanel("chat")}>
          <span className={styles.railIcon}>
            <MessageSquare size={18} />
            {renderBadge(chatUnread)}
          </span>
          <span className={styles.railLabel}>채팅방</span>
        </button>

        <button type="button" aria-current={isFriendsTab ? "page" : undefined} className={railItemClass(isFriendsTab)} onClick={() => goPanel("friends")}>
          <span className={styles.railIcon}><Users size={18} /></span>
          <span className={styles.railLabel}>친구</span>
        </button>

        <Link to="/community" aria-current={isCommunity ? "page" : undefined} className={railItemClass(isCommunity)}>
          <span className={styles.railIcon}><Newspaper size={18} /></span>
          <span className={styles.railLabel}>커뮤니티</span>
        </Link>
      </div>

      <audio ref={audioRef} preload="auto">
        <source src="/sounds/notification.mp3" type="audio/mpeg" />
      </audio>
      {/* 헤더가 숨겨지는 화면도 있어 body에 그린다 */}
      {showIOSGuide && createPortal(<IOSInstallGuide onClose={() => setShowIOSGuide(false)} />, document.body)}
      {showFeedback && createPortal(<FeedbackModal onClose={() => setShowFeedback(false)} />, document.body)}
      {showDevices && createPortal(<LoginDevicesModal onClose={() => setShowDevices(false)} />, document.body)}
    </nav>
  )
}

export default HeaderWithNotifications
