"use client"

import { useState, useEffect, useRef } from "react"
import { X, MessageCircle, Search, Users } from "lucide-react"
import styles from "./friends-list-modal.module.css"
import axiosInstance from "../api/axiosInstance"

export function FriendsListModal({ isOpen, onClose, onStartChat, currentUser }) {
  const [friends, setFriends] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchTerm, setSearchTerm] = useState("")
  const modalRef = useRef(null)

  useEffect(() => {
    if (isOpen) {
      fetchFriends()
    }
  }, [isOpen])

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (modalRef.current && !modalRef.current.contains(event.target)) {
        onClose()
      }
    }

    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside)
      document.body.style.overflow = "hidden"
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
      document.body.style.overflow = "unset"
    }
  }, [isOpen, onClose])

  const fetchFriends = async () => {
    try {
      setLoading(true)
      const response = await axiosInstance.get("/friend?page=0&size=100")
      const transformedFriends = response.data.content.map((friend) => ({
        username: friend.username,
        nickname: friend.nickname,
        status: friend.status || "offline",
        avatar: friend.profileImage,
      }))
      setFriends(transformedFriends)
    } catch (err) {
      console.error("Error fetching friends:", err)
    } finally {
      setLoading(false)
    }
  }

  const filteredFriends = friends.filter(
    (friend) =>
      friend.nickname.toLowerCase().includes(searchTerm.toLowerCase()) ||
      friend.username.toLowerCase().includes(searchTerm.toLowerCase()),
  )

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

  const handleStartChat = (friend) => {
    onStartChat(friend)
    onClose()
  }

  if (!isOpen) return null

  return (
    <div className={styles.overlay}>
      <div className={styles.modal} ref={modalRef}>
        <div className={styles.header}>
          <div className={styles.headerLeft}>
            <Users size={20} />
            <h2>Start a Chat</h2>
          </div>
          <button className={styles.closeButton} onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <div className={styles.searchContainer}>
          <Search size={16} className={styles.searchIcon} />
          <input
            type="text"
            placeholder="Search friends..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className={styles.searchInput}
          />
        </div>

        <div className={styles.friendsList}>
          {loading ? (
            <div className={styles.loading}>
              <div className={styles.spinner}></div>
              <span>Loading friends...</span>
            </div>
          ) : filteredFriends.length === 0 ? (
            <div className={styles.emptyState}>
              <Users size={48} />
              <h3>No friends found</h3>
              <p>{searchTerm ? "Try a different search term" : "Add some friends to start chatting!"}</p>
            </div>
          ) : (
            filteredFriends.map((friend) => (
              <div key={friend.username} className={styles.friendItem} onClick={() => handleStartChat(friend)}>
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
                  <div className={styles.friendUsername}>@{friend.username}</div>
                </div>
                <div className={styles.friendStatus} style={{ color: getStatusColor(friend.status) }}>
                  {friend.status}
                </div>
                <button className={styles.chatButton}>
                  <MessageCircle size={16} />
                </button>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
