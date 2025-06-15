"use client"

import { useState, useEffect } from "react"
import { FaUsers, FaSearch } from "react-icons/fa"
import FindUserModal from "./modals/FindUserModal"

// Mock data for friends - using username as unique key
const mockFriends = [
  { username: "john_doe", nickname: "John", status: "online", avatar: null },
  { username: "jane_smith", nickname: "Jane", status: "offline", avatar: null },
  { username: "mike_wilson", nickname: "Mike", status: "online", avatar: null },
  { username: "sarah_jones", nickname: "Sarah", status: "away", avatar: null },
  { username: "alex_brown", nickname: "Alex", status: "online", avatar: null },
  { username: "chris_lee", nickname: "Chris", status: "online", avatar: null },
  { username: "emma_davis", nickname: "Emma", status: "offline", avatar: null },
]

const FriendsSidebar = () => {
  const [friends, setFriends] = useState([])
  const [loading, setLoading] = useState(false)
  const [showFindUserModal, setShowFindUserModal] = useState(false)

  useEffect(() => {
    fetchFriends()
  }, [])

  const fetchFriends = async () => {
    setLoading(true)
    try {
      // Replace with your actual API call
      // const res = await axiosInstance.get('/friends');
      // setFriends(res.data);

      // Mock API call
      setTimeout(() => {
        setFriends(mockFriends)
        setLoading(false)
      }, 500)
    } catch (err) {
      console.error("친구 목록 로딩 오류:", err)
      setLoading(false)
    }
  }

  const handleFindUser = () => {
    setShowFindUserModal(true)
  }

  const handleSendFriendRequest = (user) => {
    // Handle successful friend request
    console.log("Friend request sent to:", user.username)
    // You can show a toast notification here
    // You might also want to refresh the friends list or update some state
  }

  const getStatusColor = (status) => {
    switch (status) {
      case "online":
        return "#4CAF50"
      case "away":
        return "#FF9800"
      case "offline":
        return "#9E9E9E"
      default:
        return "#9E9E9E"
    }
  }

  return (
    <>
      {/* Friends List */}
      <div
        className="friends-container"
        style={{
          overflowY: "auto",
          flex: 1,
          padding: "5px 0",
          scrollbarWidth: "none",
          msOverflowStyle: "none",
        }}
      >
        {loading ? (
          <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>
            친구 목록 불러오는 중...
          </div>
        ) : friends.length === 0 ? (
          <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>친구가 없습니다</div>
        ) : (
          friends.map((friend) => (
            <div key={friend.username} style={{ padding: "5px 10px" }}>
              {" "}
              {/* Using username as key */}
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  backgroundColor: "transparent",
                  padding: "10px 12px",
                  borderRadius: "8px",
                  cursor: "pointer",
                  transition: "background 0.2s ease",
                  border: "1px solid transparent",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = "rgba(255,255,255,0.1)"
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = "transparent"
                }}
                onClick={() => {
                  // Handle friend click - start chat, view profile, etc.
                  console.log("Friend clicked:", friend.username)
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    flex: 1,
                    overflow: "hidden",
                  }}
                >
                  <div
                    style={{
                      backgroundColor: "rgba(255,255,255,0.7)",
                      color: "#2588F1",
                      borderRadius: "50%",
                      width: "28px",
                      height: "28px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      marginRight: "10px",
                      flexShrink: 0,
                      position: "relative",
                    }}
                  >
                    <FaUsers size={14} />
                    {/* Status indicator */}
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
              </div>
            </div>
          ))
        )}
      </div>

      {/* Find User Button */}
      <div
        style={{
          padding: "16px",
          borderTop: "1px solid rgba(255,255,255,0.15)",
          backgroundColor: "rgba(0,0,0,0.05)",
        }}
      >
        <button
          onClick={handleFindUser}
          style={{
            width: "100%",
            padding: "12px",
            backgroundColor: "#1366d6",
            border: "1px solid rgba(255,255,255,0.2)",
            borderRadius: "8px",
            color: "white",
            fontWeight: "bold",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.backgroundColor = "#0d5bca"
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = "#1366d6"
          }}
        >
          <FaSearch style={{ marginRight: "8px" }} />
          Find Friends
        </button>
      </div>

      {/* Find User Modal */}
      {showFindUserModal && (
        <FindUserModal onClose={() => setShowFindUserModal(false)} onSendFriendRequest={handleSendFriendRequest} />
      )}

      {/* Hide scrollbar styles */}
      <style jsx>{`
        .friends-container::-webkit-scrollbar {
          display: none;
        }
      `}</style>
    </>
  )
}

export default FriendsSidebar
