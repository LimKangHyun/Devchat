"use client"

import { useState, useEffect, useRef } from "react"
import { Bell, Check, X } from "lucide-react"
import "./header.css"
import axiosInstance from "./api/axiosInstance"
import { Link } from "react-router-dom"

// Mock data for testing
const mockFriendRequests = [
  {
    id: "1",
    senderId: "user123",
    senderName: "Alice Johnson",
    senderProfileImg: "alice-profile.jpg",
    createdAt: "2024-01-15T10:30:00Z",
  },
  {
    id: "2",
    senderId: "user456",
    senderName: "Bob Smith",
    senderProfileImg: null,
    createdAt: "2024-01-14T15:45:00Z",
  },
  {
    id: "3",
    senderId: "user789",
    senderName: "Carol Davis",
    senderProfileImg: "carol-profile.jpg",
    createdAt: "2024-01-13T09:20:00Z",
  },
  
]

export function Header() {
  const [profileImage, setProfileImage] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)

  // Notification states
  const [friendRequests, setFriendRequests] = useState([])
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const [isLoadingRequests, setIsLoadingRequests] = useState(false)
  const [processingRequestId, setProcessingRequestId] = useState(null)

  const notificationRef = useRef(null)

  useEffect(() => {
    const fetchProfileImage = async () => {
      try {
        setIsLoading(true)
        const { data } = await axiosInstance.get("/user/details")
        setProfileImage(data.profileImg)
        setIsLoading(false)
      } catch (err) {
        console.error("Error fetching profile image:", err)
        setError(err.message)
        setIsLoading(false)
      }
    }

    fetchProfileImage()

    const handler = () => {
      console.log("🔥 Header 이벤트 수신됨")
      fetchProfileImage()
    }

    window.addEventListener("profile-updated", handler)
    return () => window.removeEventListener("profile-updated", handler)
  }, [])

  // Fetch friend requests
  const fetchFriendRequests = async () => {
    try {
      setIsLoadingRequests(true)

      // For testing - use mock data
      // Comment out these lines and uncomment the API call when ready
      await new Promise((resolve) => setTimeout(resolve, 500)) // Simulate API delay
      setFriendRequests(mockFriendRequests)

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

      // Remove the accepted request from the list
      setFriendRequests((prev) => prev.filter((req) => req.id !== requestId))

      // Dispatch custom event for other components that might need to know
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

      // Remove the rejected request from the list
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
      fetchFriendRequests()
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

  // Fetch friend requests on component mount
  useEffect(() => {
    fetchFriendRequests()
  }, [])

  return (
    <header className="header">
      <div className="container">
        <Link to="/">
          <img src="/images/devchat-logo.png" alt="DevChat Logo" className="header-logo-image" />
        </Link>

        <div className="profile-container">
          {/* Notification Bell */}
          <div className="notification-container" ref={notificationRef}>
            <button className="notification-bell" onClick={toggleNotifications} aria-label="알림">
              <Bell size={20} />
              {friendRequests.length > 0 && <span className="notification-badge">{friendRequests.length}</span>}
            </button>

            {/* Notification Dropdown */}
            {isNotificationOpen && (
              <div className="notification-dropdown">
                <div className="notification-header">
                  <h3>Notifications</h3>
                </div>

                <div className="notification-content">
                  {isLoadingRequests ? (
                    <div className="notification-loading">
                      <div className="loading-spinner"></div>
                      <span>로딩 중...</span>
                    </div>
                  ) : friendRequests.length === 0 ? (
                    <div className="no-notifications">
                      <div className="no-notifications-icon">🎉</div>
                      <span className="no-notifications-title">All caught up!</span>
                      <span className="no-notifications-subtitle">No notifications.</span>
                    </div>
                  ) : (
                    <div className="friend-requests-list">
                      {friendRequests.map((request) => (
                        <div key={request.id} className="friend-request-item">
                          <div className="request-user-info">
                            <img
                              src={
                                request.senderProfileImg
                                  ? `${process.env.REACT_APP_PROFILE_IMAGE_URL}/${request.senderProfileImg}`
                                  : "/images/not-found-profile.png"
                              }
                              alt={request.senderName}
                              className="request-profile-image"
                              onError={(e) => {
                                e.currentTarget.src = "/images/not-found-profile.png"
                              }}
                            />
                            <div className="request-details">
                              <span className="request-sender-name">{request.senderName}</span>
                              <span className="request-time">
                                {new Date(request.createdAt).toLocaleDateString("ko-KR")}
                              </span>
                            </div>
                          </div>

                          <div className="request-actions">
                            <button
                              className="accept-btn"
                              onClick={() => handleAcceptRequest(request.id)}
                              disabled={processingRequestId === request.id}
                              aria-label="수락"
                            >
                              <Check size={16} />
                            </button>
                            <button
                              className="reject-btn"
                              onClick={() => handleRejectRequest(request.id)}
                              disabled={processingRequestId === request.id}
                              aria-label="거절"
                            >
                              <X size={16} />
                            </button>
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
            <div className="profile-image-loading"></div>
          ) : error ? (
            <div className="profile-image-error">
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
                className="profile-image"
                onError={(e) => {
                  e.currentTarget.src = "/images/not-found-profile.png"
                }}
              />
            </Link>
          )}

          <button
            className="logout-text"
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
    </header>
  )
}

export default Header
