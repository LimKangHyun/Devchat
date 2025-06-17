"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { X, Send, Minimize2, Maximize2, Phone, Video } from "lucide-react"
import styles from "./chat-modal.module.css"
import axiosInstance from "../api/axiosInstance"

export function ChatModal({ isOpen, onClose, friend, currentUser }) {
  const [messages, setMessages] = useState([])
  const [newMessage, setNewMessage] = useState("")
  const [loading, setLoading] = useState(false)
  const [isMinimized, setIsMinimized] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [position, setPosition] = useState({ x: 100, y: 100 })
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 })

  const modalRef = useRef(null)
  const headerRef = useRef(null)
  const messagesEndRef = useRef(null)
  const inputRef = useRef(null)

  const [size, setSize] = useState({ width: 400, height: 500 })

  // Dragging functionality
  const handleMouseDown = useCallback((e) => {
    if (!headerRef.current || !modalRef.current) return

    const rect = modalRef.current.getBoundingClientRect()
    setDragOffset({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    })
    setIsDragging(true)
  }, [])

  const handleMouseMove = useCallback(
    (e) => {
      if (!isDragging) return

      const newX = e.clientX - dragOffset.x
      const newY = e.clientY - dragOffset.y

      // Keep modal within viewport bounds
      const maxX = window.innerWidth - 400
      const maxY = window.innerHeight - 500

      setPosition({
        x: Math.max(0, Math.min(newX, maxX)),
        y: Math.max(0, Math.min(newY, maxY)),
      })
    },
    [isDragging, dragOffset],
  )

  const handleMouseUp = useCallback(() => {
    setIsDragging(false)
  }, [])

  useEffect(() => {
    if (isDragging) {
      document.addEventListener("mousemove", handleMouseMove)
      document.addEventListener("mouseup", handleMouseUp)
      document.body.style.userSelect = "none"
    }

    return () => {
      document.removeEventListener("mousemove", handleMouseMove)
      document.removeEventListener("mouseup", handleMouseUp)
      document.body.style.userSelect = "auto"
    }
  }, [isDragging, handleMouseMove, handleMouseUp])

  useEffect(() => {
    if (isOpen && friend) {
      fetchChatHistory()
      markNotificationAsRead()
    }
  }, [isOpen, friend])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const fetchChatHistory = async () => {
    try {
      setLoading(true)
      // Replace with your actual chat API endpoint
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
      // For demo purposes, add some mock messages
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
      // Call API to mark message notifications as read
      await axiosInstance.post(`/notification/read`, {
        type: "MESSAGE",
        sender: friend.username,
      })
      console.log("📖 Marked message notifications as read for:", friend.username)
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
      // Replace with your actual send message API
      await axiosInstance.post("/chat/send", {
        recipient: friend.username,
        content: newMessage,
      })
    } catch (err) {
      console.error("Error sending message:", err)
      // Remove the temp message on error
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
            top: position.y,
            left: position.x,
            width: `${size.width}px`,
            height: `${size.height}px`,
            position: "fixed", // transform 대신
        }}
    >
      <div className={styles.header} ref={headerRef} onMouseDown={handleMouseDown}>
        <div className={styles.headerLeft}>
          <div className={styles.friendAvatar}>
            <img
              src={
                friend.avatar
                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${friend.avatar}`
                  : "/images/not-found-profile.png"
              }
              alt={friend.nickname}
              onError={(e) => {
                e.currentTarget.src = "/images/not-found-profile.png"
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
          <button className={styles.actionButton} onClick={() => setIsMinimized(!isMinimized)} title="Minimize">
            {isMinimized ? <Maximize2 size={16} /> : <Minimize2 size={16} />}
          </button>
          <button className={styles.closeButton} onClick={onClose} title="Close">
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
          {/* ✅ 여기에 리사이즈 핸들 추가 */}
    <div
      className={styles.resizeHandle}
      onMouseDown={(e) => {
        e.preventDefault()
        const startX = e.clientX
        const startY = e.clientY
        const startWidth = size.width
        const startHeight = size.height

        const onMouseMove = (e) => {
          const newWidth = Math.min(window.innerWidth, Math.max(300, startWidth + (e.clientX - startX)))
          const newHeight = Math.min(window.innerHeight, Math.max(200, startHeight + (e.clientY - startY)))
          setSize({ width: newWidth, height: newHeight })
        }

        const onMouseUp = () => {
          document.removeEventListener("mousemove", onMouseMove)
          document.removeEventListener("mouseup", onMouseUp)
        }

        document.addEventListener("mousemove", onMouseMove)
        document.addEventListener("mouseup", onMouseUp)
      }}
      />
    </div>
  )
}
