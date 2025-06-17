"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { X, Send, Minimize2, Maximize2 } from "lucide-react"
import styles from "./chat-modal.module.css"
import axiosInstance from "../api/axiosInstance"

export function ChatModal({ isOpen, onClose, friend, currentUser, initialPosition }) {
  const [messages, setMessages] = useState([])
  const [newMessage, setNewMessage] = useState("")
  const [loading, setLoading] = useState(false)
  const [isMinimized, setIsMinimized] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [position, setPosition] = useState(initialPosition || { x: 100, y: 100 })
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 })
  const [size, setSize] = useState({ width: 400, height: 500 })

  const [isResizing, setIsResizing] = useState(false)
  const [resizeStart, setResizeStart] = useState({ x: 0, y: 0, width: 0, height: 0 })

  const modalRef = useRef(null)
  const headerRef = useRef(null)
  const messagesEndRef = useRef(null)
  const inputRef = useRef(null)
  const resizeFrame = useRef(null)

  // Enhanced dragging with immediate response
  const handleMouseDown = useCallback((e) => {
    if (!modalRef.current) return
    e.preventDefault()

    setDragStart({
        x: e.clientX - position.x,
        y: e.clientY - position.y,
    })
    setIsDragging(true)

    document.body.style.cursor = "grabbing"
    document.body.style.userSelect = "none"
  }, [position])

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

  const handleResizeMouseMove = useCallback((e) => {
    if (!isResizing) return

    if (resizeFrame.current) cancelAnimationFrame(resizeFrame.current)

    resizeFrame.current = requestAnimationFrame(() => {
        const deltaX = e.clientX - resizeStart.x
        const deltaY = e.clientY - resizeStart.y

        const newWidth = Math.min(window.innerWidth - position.x - 20, Math.max(300, resizeStart.width + deltaX))
        const newHeight = Math.min(window.innerHeight - position.y - 20, Math.max(200, resizeStart.height + deltaY))

        setSize({ width: newWidth, height: newHeight })
    })
  }, [isResizing, resizeStart, position])

  const handleResizeMouseUp = useCallback(() => {
    setIsResizing(false)
    document.body.style.cursor = "auto"
    document.body.style.userSelect = "auto"
  }, [])

  // Global mouse event listeners for smooth dragging
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

  useEffect(() => {
    if (isOpen && friend) {
      fetchChatHistory()
      markNotificationAsRead()
      // Focus input when opened
      setTimeout(() => inputRef.current?.focus(), 100)
    }
  }, [isOpen, friend])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const fetchChatHistory = async () => {
    try {
      setLoading(true)
      const response = await axiosInstance.get(`/chat/history/${friend.username}`)
      const transformedMessages = response.data.map((msg) => ({
        id: msg.id,
        content: msg.content,
        sender: msg.sender,
        timestamp: msg.timestamp,
        isOwn: msg.sender === currentUser?.username,
      }))
      setMessages(transformedMessages)
    } catch (err) {
      console.error("Error fetching chat history:", err)
      // Demo messages
      setMessages([
        {
          id: "1",
          content: "Hey there! 👋",
          sender: friend.username,
          timestamp: new Date(Date.now() - 300000).toISOString(),
          isOwn: false,
        },
        {
          id: "2",
          content: "Hi! How are you doing?",
          sender: currentUser?.username || "me",
          timestamp: new Date(Date.now() - 240000).toISOString(),
          isOwn: true,
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  const markNotificationAsRead = async () => {
    try {
      await axiosInstance.post(`/notification/read`, {
        type: "MESSAGE",
        sender: friend.username,
      })
    } catch (err) {
      console.error("Error marking notification as read:", err)
    }
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }

  const handleSendMessage = async (e) => {
    e.preventDefault()
    if (!newMessage.trim()) return

    const tempMessage = {
      id: Date.now().toString(),
      content: newMessage,
      sender: currentUser?.username || "me",
      timestamp: new Date().toISOString(),
      isOwn: true,
    }

    setMessages((prev) => [...prev, tempMessage])
    setNewMessage("")

    try {
      await axiosInstance.post("/chat/send", {
        recipient: friend.username,
        content: newMessage,
      })
    } catch (err) {
      console.error("Error sending message:", err)
      setMessages((prev) => prev.filter((msg) => msg.id !== tempMessage.id))
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

  return (
    <div
      className={`${styles.modal} ${isMinimized ? styles.minimized : ""} ${isDragging ? styles.dragging : ""}`}
      ref={modalRef}
      style={{
        transform: `translate(${position.x}px, ${position.y}px)`,
        width: isMinimized ? `240px` : `${size.width}px`, 
        height: isMinimized ? `60px` : `${size.height}px`, // ✅ 이 부분 추가!
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
          <div className={styles.messagesContainer}>
            {loading ? (
              <div className={styles.loading}>
                <div className={styles.spinner}></div>
                <span>Loading messages...</span>
              </div>
            ) : (
              <>
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
            />
            <button type="submit" className={styles.sendButton} disabled={!newMessage.trim()}>
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
