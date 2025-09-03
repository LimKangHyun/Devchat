"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { X, Send, Minimize2, Maximize2 } from "lucide-react"
import styles from "./chat-modal.module.css"
import axiosInstance from "../api/axiosInstance"
import useWebSocket from "../common/useWebSocket"

export function ChatModal({ isOpen, onClose, friend, currentUser, initialPosition, stompClient }) {
  const [messages, setMessages] = useState([])
  const [newMessage, setNewMessage] = useState("")
  const [loading, setLoading] = useState(false)
  const [isMinimized, setIsMinimized] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [position, setPosition] = useState(initialPosition || { x: 100, y: 100 })
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 })
  const [size, setSize] = useState({ width: 400, height: 500 })
  const [chatRoomId, setChatRoomId] = useState(null)

  const [isResizing, setIsResizing] = useState(false)
  const [resizeStart, setResizeStart] = useState({ x: 0, y: 0, width: 0, height: 0 })

  // 페이지네이션 관련 상태
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [isFetching, setIsFetching] = useState(false)
  const [isInitialized, setIsInitialized] = useState(false)

  const modalRef = useRef(null)
  const headerRef = useRef(null)
  const messagesEndRef = useRef(null)
  const inputRef = useRef(null)
  const resizeFrame = useRef(null)
  const messagesContainerRef = useRef(null)

  // 초기화 완료 추적용
  const initializationRef = useRef(false)

  // Enhanced dragging with immediate response
  const handleMouseDown = useCallback(
    (e) => {
      if (!modalRef.current) return
      e.preventDefault()

      setDragStart({
        x: e.clientX - position.x,
        y: e.clientY - position.y,
      })
      setIsDragging(true)

      document.body.style.cursor = "grabbing"
      document.body.style.userSelect = "none"
    },
    [position],
  )

  const handleMouseMove = useCallback(
    (e) => {
      if (!isDragging) return

      // Calculate new position immediately
      const newX = e.clientX - dragStart.x
      const newY = e.clientY - dragStart.y

      // Constrain to viewport with padding
      const padding = 10
      const maxX = window.innerWidth - (isMinimized ? 240 : size.width) - padding
      const maxY = window.innerHeight - (isMinimized ? 60 : size.height) - padding

      const constrainedX = Math.max(padding, Math.min(newX, maxX))
      const constrainedY = Math.max(padding, Math.min(newY, maxY))

      // Update position immediately for smooth movement
      setPosition({ x: constrainedX, y: constrainedY })
    },
    [isDragging, dragStart, size],
  )

  const handleMouseUp = useCallback(() => {
    setIsDragging(false)
    document.body.style.cursor = "auto"
    document.body.style.userSelect = "auto"
  }, [])

  // Resize functionality
  const handleResizeMouseDown = useCallback(
    (e) => {
      e.preventDefault()
      e.stopPropagation()

      setResizeStart({
        x: e.clientX,
        y: e.clientY,
        width: size.width,
        height: size.height,
      })
      setIsResizing(true)

      document.body.style.cursor = "se-resize"
      document.body.style.userSelect = "none"
    },
    [size],
  )

  const handleResizeMouseMove = useCallback(
    (e) => {
      if (!isResizing) return

      if (resizeFrame.current) cancelAnimationFrame(resizeFrame.current)

      resizeFrame.current = requestAnimationFrame(() => {
        const deltaX = e.clientX - resizeStart.x
        const deltaY = e.clientY - resizeStart.y

        const newWidth = Math.min(window.innerWidth - position.x - 20, Math.max(300, resizeStart.width + deltaX))
        const newHeight = Math.min(window.innerHeight - position.y - 20, Math.max(200, resizeStart.height + deltaY))

        setSize({ width: newWidth, height: newHeight })
      })
    },
    [isResizing, resizeStart, position],
  )

  const handleResizeMouseUp = useCallback(() => {
    setIsResizing(false)
    document.body.style.cursor = "auto"
    document.body.style.userSelect = "auto"
  }, [])

  // 💡 useCallback으로 고정된 콜백 생성
  const handleDmMessageReceived = useCallback(
    (received) => {
      const newMessageData = {
        id: received.messageId, // Use messageId as the unique key
        // For potentially temporary client-side messages before backend ID, consider:
        // id: received.messageId || `temp-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
        content: received.content,
        sender: received.senderNickName,
        timestamp: received.sendAt || new Date().toISOString(),
        isOwn: received.senderNickName === currentUser?.nickname,
        type: received.type,
        messageId: received.messageId,
      }

      setMessages((prev) => {
        const alreadyExists = prev.some((msg) => msg.messageId === received.messageId)
        if (alreadyExists) return prev
        return [...prev, newMessageData]
      })
    },
    [currentUser?.nickname],
  )

  // 📡 전달 시 고정된 콜백 전달
  const { connected, stompClientRef } = useWebSocket({
    username: currentUser.username,
    roomId: null,
    dmRoomId: chatRoomId,
    onDmMessageReceived: handleDmMessageReceived,
  })

  // 더 많은 메시지 가져오기
  const fetchMoreMessages = useCallback(async () => {
    // Guard: Do not fetch if already fetching, no more messages, no room ID, not initialized, or if currentPage is 0 (initial load handled elsewhere)
    if (isFetching || !hasMore || !chatRoomId || !isInitialized) {
      if (currentPage === 0 && isInitialized) {
        console.log("🚫 fetchMoreMessages: Attempted to fetch page 0, but it should be handled by initial load.")
      }
      return
    }

    console.log(`📥 Fetching page ${currentPage}`)
    setIsFetching(true)

    try {
      const response = await axiosInstance.get(`/dm/history/${chatRoomId}`, {
        params: { page: currentPage, size: 20 },
      })

      const fetchedMessages = response.data.content.map((msg) => ({
        id: msg.messageId, // Use messageId as the unique key
        content: msg.content,
        sender: msg.senderNickName,
        timestamp: msg.sendAt,
        isOwn: msg.senderNickName === currentUser?.nickname,
        messageId: msg.messageId,
      }))

      if (fetchedMessages.length === 0 || fetchedMessages.length < 20) {
        setHasMore(false)
      }

      if (fetchedMessages.length > 0) {
        setMessages((prevMessages) => {
          const existingMessageIds = new Set(prevMessages.map((m) => m.messageId))
          // Filter out messages that are already in the state
          const uniqueNewMessages = fetchedMessages.filter((m) => !existingMessageIds.has(m.messageId))
          // Fetched messages are older, so prepend them, ensuring chronological order
          const sortedUniqueNewMessages = uniqueNewMessages.sort(
            (a, b) => new Date(a.timestamp) - new Date(b.timestamp),
          )
          return [...sortedUniqueNewMessages, ...prevMessages]
        })
        setCurrentPage((prev) => prev + 1)
      }
    } catch (error) {
      console.error("Error fetching more messages:", error)
    } finally {
      setIsFetching(false)
    }
  }, [chatRoomId, currentPage, isFetching, hasMore, currentUser?.nickname, isInitialized])

  // 스크롤 이벤트 핸들러 (무한 스크롤)
  const handleScroll = useCallback(() => {
    const container = messagesContainerRef.current
    if (!container || !isInitialized) return

    // 스크롤이 상단 근처에 도달했을 때만 로드
    if (container.scrollTop <= 100 && hasMore && !isFetching) {
      console.log("🔝 Scroll reached top, loading more messages")
      fetchMoreMessages()
    }
  }, [fetchMoreMessages, hasMore, isFetching, isInitialized])

  useEffect(() => {
    if (isDragging) {
      document.addEventListener("mousemove", handleMouseMove, { passive: false })
      document.addEventListener("mouseup", handleMouseUp)
    }

    if (isResizing) {
      document.addEventListener("mousemove", handleResizeMouseMove, { passive: false })
      document.addEventListener("mouseup", handleResizeMouseUp)
    }

    return () => {
      document.removeEventListener("mousemove", handleMouseMove)
      document.removeEventListener("mouseup", handleMouseUp)
      document.removeEventListener("mousemove", handleResizeMouseMove)
      document.removeEventListener("mouseup", handleResizeMouseUp)
    }
  }, [isDragging, isResizing, handleMouseMove, handleMouseUp, handleResizeMouseMove, handleResizeMouseUp])

  // Auto-position new modals to avoid overlap
  useEffect(() => {
    if (isOpen && !initialPosition) {
      const offset = Math.random() * 100 + 50
      setPosition({
        x: 100 + offset,
        y: 100 + offset,
      })
    }
  }, [isOpen, initialPosition])

  // Effect to broadcast modal status (open/closed) for other components to listen to.
  useEffect(() => {
    if (isOpen && friend?.username) {
      // Announce that this chat modal is open
      window.dispatchEvent(
        new CustomEvent("chat-modal-status", {
          detail: { action: "open", username: friend.username },
        }),
      )

      // Return a cleanup function that runs when the modal closes or the friend changes
      return () => {
        window.dispatchEvent(
          new CustomEvent("chat-modal-status", {
            detail: { action: "close", username: friend.username },
          }),
        )
      }
    }
  }, [isOpen, friend?.username])

  // 채팅방 초기화 - 한 번만 실행되도록 수정
  useEffect(() => {
    // If the modal is not open, or friend/user details are missing, do nothing.
    if (!isOpen || !friend?.username || !currentUser?.username) {
      // Reset the initialization flag if the modal is closed or key identifiers are missing
      // This ensures that if it re-opens with the same friend, it re-initializes.
      if (!isOpen) {
        initializationRef.current = false
      }
      return
    }

    // If initialization has already run for this opening, do nothing.
    if (initializationRef.current) {
      return
    }

    // Mark as initializing
    initializationRef.current = true

    // 상태 초기화
    // setMessages([])
    // setCurrentPage(0) // Page 0 will be fetched by initializeChat
    // setHasMore(true)
    // setIsFetching(false)
    // setIsInitialized(false)
    // setChatRoomId(null)
    setLoading(true)

    const initializeChat = async () => {
      try {
        console.log("🚀 Initializing chat room for:", friend.username)

        // 1. Fetch Chat Room ID
        const roomResponse = await axiosInstance.get(`/dm/room/${friend.username}`)
        const newRoomId = roomResponse.data.roomId
        console.log("✅ Chat room ID:", newRoomId)
        setChatRoomId(newRoomId)

        // 2. Load Initial Messages (Page 0)
        console.log("📥 Loading initial messages for room:", newRoomId)
        const messagesResponse = await axiosInstance.get(`/dm/history/${newRoomId}`, {
          params: { page: 0, size: 20 },
        })

        const initialMessagesData = messagesResponse.data.content.map((msg) => ({
          id: msg.messageId,
          content: msg.content,
          sender: msg.senderNickName,
          timestamp: msg.sendAt,
          isOwn: msg.senderNickName === currentUser.nickname,
          messageId: msg.messageId,
        }))

        console.log("✅ Loaded initial messages:", initialMessagesData.length)

        const sortedMessages = initialMessagesData.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))

        setMessages(sortedMessages)
        setCurrentPage(1) // Next page to fetch will be page 1
        setHasMore(initialMessagesData.length === 20)
        setIsInitialized(true)
      } catch (err) {
        console.error("Error initializing chat:", err)
        setIsInitialized(true) // Ensure UI can proceed even on error
      } finally {
        setLoading(false)
        setTimeout(() => inputRef.current?.focus(), 100)
      }
    }

    initializeChat()

    // Cleanup function: This will run when the component unmounts OR BEFORE the effect runs again
    // if its dependencies change.
    return () => {
      // We only want to reset the initializationRef if the modal is truly being "closed"
      // or if the friend context is changing.
      // If isOpen becomes false, it means the modal is closing.
      // If friend.username changes, it's a new chat.
      // This check might be too aggressive if other dependencies cause a re-run without closing.
      // The primary guard is `initializationRef.current` at the top of the effect.
      // For now, let's rely on the top guard and reset on explicit close/change.
      // If the effect is re-running due to other dep changes, the `if (initializationRef.current)` check
      // at the top should prevent re-initialization.
    }
  }, [isOpen, friend?.username, currentUser?.username, currentUser?.nickname]) // Keep dependencies

  // Add a separate effect to reset initializationRef when the modal is closed.
  // useEffect(() => {
  //   if (!isOpen) {
  //     initializationRef.current = false
  //   }
  // }, [isOpen])

  // Effect to reset all state when the modal is closed
  useEffect(() => {
    if (!isOpen) {
      // Reset all chat-specific state to initial values
      setMessages([])
      setCurrentPage(0)
      setHasMore(true)
      setIsFetching(false)
      setIsInitialized(false)
      setChatRoomId(null)
      setLoading(false) // Also reset loading state
      initializationRef.current = false // Reset the initialization guard
      console.log("🧼 Chat modal closed and state reset.")
    }
  }, [isOpen])

  // 스크롤 이벤트 리스너 등록
  useEffect(() => {
    const container = messagesContainerRef.current
    if (!container || !isInitialized || !hasMore) return // Added !hasMore check

    container.addEventListener("scroll", handleScroll)
    return () => {
      if (container) {
        // Check if container still exists on cleanup
        container.removeEventListener("scroll", handleScroll)
      }
    }
  }, [handleScroll, isInitialized, hasMore])

  // 초기 로드 후 스크롤을 맨 아래로
  useEffect(() => {
    if (isInitialized && messages.length > 0 && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: "instant" })
    }
  }, [isInitialized])

  // 새 메시지가 도착했을 때 스크롤
  useEffect(() => {
    if (isInitialized && messages.length > 0) {
      const container = messagesContainerRef.current
      if (container) {
        const isNearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 100
        if (isNearBottom && messagesEndRef.current) {
          messagesEndRef.current.scrollIntoView({ behavior: "smooth" })
        }
      }
    }
  }, [messages.length, isInitialized])

  const handleSendMessage = async (e) => {
    e.preventDefault()
    if (!newMessage.trim() || !chatRoomId || !stompClientRef.current?.connected) {
      return
    }

    const messageContent = newMessage.trim()
    setNewMessage("")

    try {
      // 웹소켓을 통해 메시지 전송
      stompClientRef.current.publish({
        destination: `/dm/send/${chatRoomId}`,
        body: JSON.stringify({
          receiverUsername: friend.username,
          content: messageContent,
          type: "TEXT",
        }),
      })

      console.log(`📤 Message sent to room ${chatRoomId}:`, messageContent)
    } catch (err) {
      console.error("Error sending message:", err)
      // 에러 시 메시지 다시 설정
      setNewMessage(messageContent)
    }
  }

  const formatTime = (timestamp) => {
    const date = new Date(timestamp)
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
  }

  const getStatusColor = (status) => {
    switch (status) {
      case "online":
        return "#10b981"
      case "away":
        return "#f59e0b"
      case "offline":
        return "#6b7280"
      default:
        return "#6b7280"
    }
  }

  if (!isOpen) return null

  const readyToSend = !loading && chatRoomId && stompClientRef.current?.connected

  return (
    <div
      className={`${styles.modal} ${isMinimized ? styles.minimized : ""} ${isDragging ? styles.dragging : ""}`}
      ref={modalRef}
      style={{
        transform: `translate(${position.x}px, ${position.y}px)`,
        width: isMinimized ? `240px` : `${size.width}px`,
        height: isMinimized ? `60px` : `${size.height}px`,
        transition: isDragging ? "none" : "transform 0.2s ease-out",
      }}
    >
      <div
        className={styles.header}
        ref={headerRef}
        onMouseDown={handleMouseDown}
        style={{ cursor: isDragging ? "grabbing" : "grab" }}
      >
        <div className={styles.headerLeft}>
          <div className={styles.friendAvatar}>
            <img
              src={
                friend.avatar
                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${friend.avatar}`
                  : "/placeholder.svg?height=36&width=36"
              }
              alt={friend.nickname}
              onError={(e) => {
                e.currentTarget.src = "/placeholder.svg?height=36&width=36"
              }}
            />
            <div className={styles.statusIndicator} style={{ backgroundColor: getStatusColor(friend.status) }} />
          </div>
          <div className={styles.friendInfo}>
            <div className={styles.friendName}>{friend.nickname}</div>
            <div className={styles.friendStatus} style={{ color: getStatusColor(friend.status) }}>
              {friend.status}
            </div>
          </div>
        </div>

        <div className={styles.headerActions}>
          <button
            className={styles.actionButton}
            onClick={(e) => {
              e.stopPropagation()
              setIsMinimized(!isMinimized)
            }}
            title="Minimize"
          >
            {isMinimized ? <Maximize2 size={16} /> : <Minimize2 size={16} />}
          </button>
          <button
            className={styles.closeButton}
            onClick={(e) => {
              e.stopPropagation()
              onClose()
            }}
            title="Close"
          >
            <X size={16} />
          </button>
        </div>
      </div>

      {!isMinimized && (
        <>
          <div className={styles.messagesContainer} ref={messagesContainerRef}>
            {loading ? (
              <div className={styles.loading}>
                <div className={styles.spinner}></div>
                <span>Loading messages...</span>
              </div>
            ) : (
              <>
                {isFetching && (
                  <div className={styles.loadingMore}>
                    <div className={styles.spinner}></div>
                    <span>Loading more messages...</span>
                  </div>
                )}
                {messages.map((message) => (
                  <div key={message.id} className={`${styles.message} ${message.isOwn ? styles.ownMessage : ""}`}>
                    <div className={styles.messageContent}>
                      <div className={styles.messageText}>{message.content}</div>
                      <div className={styles.messageTime}>{formatTime(message.timestamp)}</div>
                    </div>
                  </div>
                ))}
                <div ref={messagesEndRef} />
              </>
            )}
          </div>

          <form className={styles.inputContainer} onSubmit={handleSendMessage}>
            <input
              ref={inputRef}
              type="text"
              value={newMessage}
              onChange={(e) => setNewMessage(e.target.value)}
              placeholder={`Message ${friend.nickname}...`}
              className={styles.messageInput}
              disabled={!readyToSend}
            />
            <button type="submit" className={styles.sendButton} disabled={!readyToSend}>
              <Send size={16} />
            </button>
          </form>
        </>
      )}
      {/* Resize handle */}
      {!isMinimized && (
        <div
          style={{
            position: "absolute",
            width: "16px",
            height: "16px",
            right: "0",
            bottom: "0",
            cursor: "se-resize",
            background: "transparent",
            zIndex: 10,
          }}
          onMouseDown={handleResizeMouseDown}
        />
      )}
    </div>
  )
}
