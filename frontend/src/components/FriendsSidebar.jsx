"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { FaUsers, FaSearch } from "react-icons/fa"
import { MessageCircle } from "lucide-react" // Chat icon
import FindUserModal from "./modals/FindUserModal"
import axiosInstance from "./api/axiosInstance"
import useWebSocketNotifications from "./common/useWebSocket"

const FriendsSidebar = ({ onStartChat }) => {
  // This check is important for debugging.
  useEffect(() => {
    if (typeof onStartChat !== "function") {
      console.error("EnhancedFriendsSidebar: CRITICAL - onStartChat prop is NOT A FUNCTION. Chat modals will not open.")
    }
  }, [onStartChat])

  const [friends, setFriends] = useState([])
  const [loading, setLoading] = useState(false)
  const [showFindUserModal, setShowFindUserModal] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMoreFriends, setHasMoreFriends] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [totalFriendsCount, setTotalFriendsCount] = useState(0)
  const friendsContainerRef = useRef(null)

  const handleFriendNotification = useCallback((notification) => {
    switch (notification.type) {
      case "FRIEND_ACCEPTED":
        const newFriend = {
          username: notification.senderUsername,
          nickname: notification.senderNickname,
          status: "online",
          avatar: notification.senderImg,
        }
        setFriends((prev) => {
          if (!prev.some((f) => f.username === newFriend.username)) {
            setTotalFriendsCount((c) => c + 1)
            return [newFriend, ...prev]
          }
          return prev
        })
        break
      case "FRIEND_STATUS_UPDATE":
        setFriends((prev) =>
          prev.map((friend) =>
            friend.username === notification.sender ? { ...friend, status: notification.status || "offline" } : friend,
          ),
        )
        break
      case "FRIEND_REMOVED":
        setFriends((prev) => {
          const filtered = prev.filter((friend) => friend.username !== notification.sender)
          if (filtered.length !== prev.length) {
            setTotalFriendsCount((count) => Math.max(0, count - 1))
          }
          return filtered
        })
        break
      default:
        break
    }
  }, [])

  useWebSocketNotifications({
    username: currentUser?.username,
    onNotificationReceived: handleFriendNotification,
    // other props...
  })

  useEffect(() => {
    const handleFriendRequestAccepted = () => fetchFriends(0, true)
    window.addEventListener("friend-request-accepted", handleFriendRequestAccepted)
    return () => window.removeEventListener("friend-request-accepted", handleFriendRequestAccepted)
  }, [])

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await axiosInstance.get("/user/details")
        setCurrentUser(res.data)
      } catch (err) {
        console.error("Error loading current user:", err)
      }
    }
    fetchUser()
  }, [])

  useEffect(() => {
    if (currentUser) {
      fetchFriends(0, true)
    }
  }, [currentUser])

  const fetchFriends = async (page = 0, reset = false) => {
    if (isLoadingMore && !reset) return
    if (reset) setLoading(true)
    else setIsLoadingMore(true)

    try {
      const response = await axiosInstance.get(`/friend?page=${page}&size=20`)
      const { content, totalElements, last } = response.data
      const newFriends = content.map((f) => ({
        username: f.username,
        nickname: f.nickname,
        status: f.status || "offline",
        avatar: f.profileImage,
      }))
      setFriends((prev) => (reset ? newFriends : [...prev, ...newFriends]))
      setHasMoreFriends(!last)
      setTotalFriendsCount(totalElements)
      setCurrentPage(page)
    } catch (err) {
      console.error("Error loading friends:", err)
    } finally {
      setLoading(false)
      setIsLoadingMore(false)
    }
  }

  const loadMoreFriends = useCallback(() => {
    if (hasMoreFriends && !isLoadingMore) {
      fetchFriends(currentPage + 1, false)
    }
  }, [currentPage, hasMoreFriends, isLoadingMore])

  const handleScroll = useCallback(() => {
    if (!friendsContainerRef.current) return
    const { scrollTop, scrollHeight, clientHeight } = friendsContainerRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      loadMoreFriends()
    }
  }, [loadMoreFriends])

  useEffect(() => {
    const el = friendsContainerRef.current
    if (el) {
      el.addEventListener("scroll", handleScroll)
      return () => el.removeEventListener("scroll", handleScroll)
    }
  }, [handleScroll])

  const handleFindUser = () => setShowFindUserModal(true)
  const handleSendFriendRequest = (user) => {}

  const handleStartChatClick = (e, friend) => {
    e.stopPropagation() // Prevent any other click events on the row
    console.log(`Chat icon clicked for ${friend.username}.`)
    if (typeof onStartChat === "function") {
      onStartChat(friend) // This calls the function from ChatManager
    } else {
      console.error(`Cannot start chat for ${friend.username}, onStartChat is not a function!`)
    }
  }

  const getStatusColor = (status) => {
    switch (status?.toLowerCase()) {
      case "online":
        return "#4CAF50"
      case "away":
        return "#FF9800"
      default:
        return "#9E9E9E"
    }
  }

  const renderFriendItem = (friend) => (
    <div key={friend.username} style={{ padding: "5px 10px" }}>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          backgroundColor: "transparent",
          padding: "10px 12px",
          borderRadius: "8px",
          transition: "all 0.2s ease",
          position: "relative",
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.backgroundColor = "rgba(255,255,255,0.1)"
          const chatIcon = e.currentTarget.querySelector(".chat-icon")
          if (chatIcon) chatIcon.style.opacity = "1"
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.backgroundColor = "transparent"
          const chatIcon = e.currentTarget.querySelector(".chat-icon")
          if (chatIcon) chatIcon.style.opacity = "0"
        }}
      >
        {/* User avatar and name section (no changes) */}
        <div style={{ display: "flex", alignItems: "center", flex: 1, overflow: "hidden" }}>
          <div style={{ position: "relative", marginRight: "10px", flexShrink: 0 }}>
            {friend.avatar ? (
              <img
                src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${friend.avatar}`}
                alt={friend.nickname || friend.username}
                style={{ width: "28px", height: "28px", borderRadius: "50%", objectFit: "cover" }}
                onError={(e) => {
                  e.currentTarget.style.display = "none"
                  if (e.currentTarget.nextSibling) e.currentTarget.nextSibling.style.display = "flex"
                }}
              />
            ) : null}
            <div
              style={{
                backgroundColor: "rgba(255,255,255,0.7)",
                color: "#2588F1",
                borderRadius: "50%",
                width: "28px",
                height: "28px",
                display: friend.avatar ? "none" : "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <FaUsers size={14} />
            </div>
            <div
              style={{
                position: "absolute",
                bottom: "-2px",
                right: "-2px",
                width: "10px",
                height: "10px",
                backgroundColor: getStatusColor(friend.status),
                borderRadius: "50%",
                border: "2px solid #2588F1",
              }}
            />
          </div>
          <div style={{ flex: 1, overflow: "hidden" }}>
            <div
              style={{
                fontWeight: "600",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
                fontSize: "14px",
              }}
            >
              {friend.nickname || friend.username}
            </div>
            <div
              style={{
                fontSize: "12px",
                color: "rgba(255,255,255,0.7)",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              @{friend.username}
            </div>
          </div>
        </div>

        {/* Status and Chat Icon Section */}
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <div
            style={{
              fontSize: "11px",
              color: getStatusColor(friend.status),
              fontWeight: "500",
              textTransform: "capitalize",
              minWidth: "50px",
              textAlign: "right",
            }}
          >
            {friend.status}
          </div>

          {/* MODIFICATION: The chat icon is now a clickable button */}
          <button
            className="chat-icon"
            onClick={(e) => handleStartChatClick(e, friend)}
            aria-label={`Chat with ${friend.nickname}`}
            style={{
              opacity: 0, // Initially hidden, shown on hover via parent
              transition: "all 0.3s ease",
              background: "linear-gradient(135deg, #10b981, #059669)",
              borderRadius: "8px",
              padding: "6px",
              color: "white",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              boxShadow: "0 2px 8px rgba(16, 185, 129, 0.3)",
              border: "none",
              cursor: "pointer",
            }}
          >
            <MessageCircle size={14} />
          </button>
        </div>
      </div>
    </div>
  )

  return (
    <>
      <div
        ref={friendsContainerRef}
        className="friends-container"
        style={{ overflowY: "auto", flex: 1, padding: "5px 0" }}
      >
        {loading && friends.length === 0 ? (
          <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>Loading friends...</div>
        ) : !loading && friends.length === 0 ? (
          <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>No friends yet.</div>
        ) : (
          <>
            {friends.map(renderFriendItem)}
            {isLoadingMore && <div style={{ textAlign: "center", padding: "15px" }}>Loading more...</div>}
          </>
        )}
      </div>
      <div style={{ padding: "16px", borderTop: "1px solid rgba(255,255,255,0.15)" }}>
        <button
          onClick={handleFindUser}
          style={{
            width: "100%",
            padding: "12px",
            backgroundColor: "#1366d6",
            border: "none",
            borderRadius: "8px",
            color: "white",
            fontWeight: "bold",
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <FaSearch style={{ marginRight: "8px" }} /> Find Friends
        </button>
      </div>
      {showFindUserModal && (
        <FindUserModal onClose={() => setShowFindUserModal(false)} onSendFriendRequest={handleSendFriendRequest} />
      )}
      <style jsx>{`
        .friends-container::-webkit-scrollbar { display: none; }
      `}</style>
    </>
  )
}

export default FriendsSidebar
