"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { FaUsers, FaSearch } from "react-icons/fa"
import FindUserModal from "./modals/FindUserModal"
import axiosInstance from "./api/axiosInstance"

const FriendsSidebar = () => {
  const [friends, setFriends] = useState([])
  const [loading, setLoading] = useState(false)
  const [showFindUserModal, setShowFindUserModal] = useState(false)

  // Pagination states
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMoreFriends, setHasMoreFriends] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [totalFriendsCount, setTotalFriendsCount] = useState(0)

  // Refs for infinite scroll
  const friendsContainerRef = useRef(null)

  useEffect(() => {
    fetchFriends(0, true) // Initial load
  }, [])

  const fetchFriends = async (page = 0, reset = false) => {
    try {
      if (reset) {
        setLoading(true)
      } else {
        setIsLoadingMore(true)
      }

      const response = await axiosInstance.get(`/friend?page=${page}&size=20`)
      const { content, totalElements, last } = response.data

      // Transform API response to match our component format
      const transformedFriends = content.map((friend) => ({
        username: friend.username,
        nickname: friend.nickname,
        status: friend.status || "offline",
        avatar: friend.profileImage,
      }))

      if (reset) {
        setFriends(transformedFriends)
        setCurrentPage(0)
      } else {
        setFriends((prev) => [...prev, ...transformedFriends])
      }

      setHasMoreFriends(!last)
      setTotalFriendsCount(totalElements)
      setCurrentPage(page)
    } catch (err) {
      console.error("친구 목록 로딩 오류:", err)

      // Fallback to mock data if API fails
      if (reset) {
        const mockFriends = [
          { username: "john_doe", nickname: "John", status: "online", avatar: null },
          { username: "jane_smith", nickname: "Jane", status: "offline", avatar: null },
          { username: "mike_wilson", nickname: "Mike", status: "online", avatar: null },
          { username: "sarah_jones", nickname: "Sarah", status: "away", avatar: null },
          { username: "alex_brown", nickname: "Alex", status: "online", avatar: null },
          { username: "chris_lee", nickname: "Chris", status: "online", avatar: null },
          { username: "emma_davis", nickname: "Emma", status: "offline", avatar: null },
        ]
        setFriends(mockFriends)
        setHasMoreFriends(false)
        setTotalFriendsCount(mockFriends.length)
      }
    } finally {
      setLoading(false)
      setIsLoadingMore(false)
    }
  }

  // Load more friends
  const loadMoreFriends = useCallback(() => {
    if (!isLoadingMore && hasMoreFriends) {
      fetchFriends(currentPage + 1, false)
    }
  }, [currentPage, isLoadingMore, hasMoreFriends])

  // Infinite scroll handler
  const handleScroll = useCallback(() => {
    if (!friendsContainerRef.current) return

    const { scrollTop, scrollHeight, clientHeight } = friendsContainerRef.current
    const isNearBottom = scrollTop + clientHeight >= scrollHeight - 100 // 100px threshold

    if (isNearBottom && hasMoreFriends && !isLoadingMore) {
      loadMoreFriends()
    }
  }, [hasMoreFriends, isLoadingMore, loadMoreFriends])

  // Add scroll listener
  useEffect(() => {
    const containerElement = friendsContainerRef.current
    if (containerElement) {
      containerElement.addEventListener("scroll", handleScroll)
      return () => containerElement.removeEventListener("scroll", handleScroll)
    }
  }, [handleScroll])

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
              position: "relative",
              marginRight: "10px",
              flexShrink: 0,
            }}
          >
            {friend.avatar ? (
              <img
                src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${friend.avatar}`}
                alt={friend.nickname || friend.username}
                style={{
                  width: "28px",
                  height: "28px",
                  borderRadius: "50%",
                  objectFit: "cover",
                }}
                onError={(e) => {
                  // Fallback to default avatar
                  e.currentTarget.style.display = "none"
                  e.currentTarget.nextSibling.style.display = "flex"
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
                position: friend.avatar ? "absolute" : "static",
                top: 0,
                left: 0,
              }}
            >
              <FaUsers size={14} />
            </div>
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
  )

  return (
    <>
      {/* Friends List */}
      <div
        ref={friendsContainerRef}
        className="friends-container"
        style={{
          overflowY: "auto",
          flex: 1,
          padding: "5px 0",
          scrollbarWidth: "none",
          msOverflowStyle: "none",
          scrollBehavior: "smooth",
        }}
        onScroll={handleScroll}
      >
        {loading ? (
          <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>
            친구 목록 불러오는 중...
          </div>
        ) : friends.length === 0 ? (
          <div style={{ textAlign: "center", padding: "20px", color: "rgba(255,255,255,0.7)" }}>친구가 없습니다</div>
        ) : (
          <>
            {friends.map(renderFriendItem)}

            {/* Loading more indicator */}
            {isLoadingMore && (
              <div
                style={{
                  textAlign: "center",
                  padding: "15px",
                  color: "rgba(255,255,255,0.7)",
                  fontSize: "14px",
                }}
              >
                <div
                  style={{
                    display: "inline-block",
                    width: "16px",
                    height: "16px",
                    border: "2px solid rgba(255,255,255,0.3)",
                    borderTop: "2px solid #2588F1",
                    borderRadius: "50%",
                    animation: "spin 1s linear infinite",
                    marginRight: "8px",
                  }}
                />
                더 많은 친구를 불러오는 중...
              </div>
            )}

            {/* End of friends indicator */}
            {!hasMoreFriends && friends.length > 0 && (
              <div
                style={{
                  textAlign: "center",
                  padding: "15px",
                  color: "rgba(255,255,255,0.5)",
                  fontSize: "12px",
                  borderTop: "1px solid rgba(255,255,255,0.1)",
                  marginTop: "10px",
                }}
              >
                모든 친구를 확인했습니다 ({totalFriendsCount}명)
              </div>
            )}
          </>
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

      {/* Hide scrollbar styles and loading animation */}
      <style jsx>{`
        .friends-container::-webkit-scrollbar {
          display: none;
        }
        
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </>
  )
}

export default FriendsSidebar
