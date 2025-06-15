"use client"

import { useState, useEffect, useRef } from "react"
import { FaSearch, FaTimes, FaUserPlus, FaUser, FaSpinner } from "react-icons/fa"

// Mock search results - replace with your actual API
const mockSearchResults = [
  { username: "alice_wonder", nickname: "Alice", status: "online", isFriend: false },
  { username: "bob_builder", nickname: "Bob", status: "offline", isFriend: false },
  { username: "charlie_brown", nickname: "Charlie", status: "away", isFriend: true },
  { username: "diana_prince", nickname: "Diana", status: "online", isFriend: false },
  { username: "edward_elric", nickname: "Edward", status: "online", isFriend: false },
]

const FindUserModal = ({ onClose, onSendFriendRequest }) => {
  const [searchQuery, setSearchQuery] = useState("")
  const [searchResults, setSearchResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [hasSearched, setHasSearched] = useState(false)
  const [sendingRequests, setSendingRequests] = useState({})
  const modalRef = useRef(null)
  const searchInputRef = useRef(null)

  // Focus search input when modal opens
  useEffect(() => {
    if (searchInputRef.current) {
      searchInputRef.current.focus()
    }
  }, [])

  // Handle click outside modal
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (modalRef.current && !modalRef.current.contains(event.target)) {
        onClose()
      }
    }

    document.addEventListener("mousedown", handleClickOutside)
    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
    }
  }, [onClose])

  // Handle escape key
  useEffect(() => {
    const handleEscape = (event) => {
      if (event.key === "Escape") {
        onClose()
      }
    }

    document.addEventListener("keydown", handleEscape)
    return () => {
      document.removeEventListener("keydown", handleEscape)
    }
  }, [onClose])

  useEffect(() => {
    if (!searchQuery.trim()) {
        setSearchResults([])
        setHasSearched(false)
        return
    }

    const delayDebounce = setTimeout(() => {
        handleSearch()
    }, 400) // 400ms 대기 후 검색 실행

    return () => clearTimeout(delayDebounce) // 입력 중이면 이전 타이머 제거
  }, [searchQuery])

  const handleSearch = async () => {
    if (!searchQuery.trim()) return

    setLoading(true)
    setHasSearched(true)

    try {
      // Replace with your actual API call
      // const res = await axiosInstance.get(`/users/search?username=${searchQuery}`);
      // setSearchResults(res.data);

      // Mock API call - search only by username
      setTimeout(() => {
        const filtered = mockSearchResults.filter((user) =>
          user.username.toLowerCase().includes(searchQuery.toLowerCase()),
        )
        setSearchResults(filtered)
        setLoading(false)
      }, 800)
    } catch (err) {
      console.error("사용자 검색 오류:", err)
      setSearchResults([])
      setLoading(false)
    }
  }

  const handleSendFriendRequest = async (user) => {
    setSendingRequests((prev) => ({ ...prev, [user.username]: true }))

    try {
      // Replace with your actual API call using username
      // await axiosInstance.post('/friends/request', { username: user.username });

      // Mock API call
      setTimeout(() => {
        // Update the user's friend status in search results
        setSearchResults((prev) =>
          prev.map((u) => (u.username === user.username ? { ...u, isFriend: true, requestSent: true } : u)),
        )

        setSendingRequests((prev) => ({ ...prev, [user.username]: false }))

        // Call parent callback if provided
        if (onSendFriendRequest) {
          onSendFriendRequest(user)
        }

        // Show success message (you can implement toast notification here)
        console.log(`Friend request sent to ${user.username}`)
      }, 1000)
    } catch (err) {
      console.error("친구 요청 전송 오류:", err)
      setSendingRequests((prev) => ({ ...prev, [user.username]: false }))
      // Show error message
      alert("친구 요청 전송에 실패했습니다.")
    }
  }

  const handleKeyPress = (event) => {
    if (event.key === "Enter") {
      handleSearch()
    }
  }

  const getStatusColor = (status) => {
    switch (status) {
      case "online":
        return "#10B981"
      case "away":
        return "#F59E0B"
      case "offline":
        return "#6B7280"
      default:
        return "#6B7280"
    }
  }

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: "rgba(0, 0, 0, 0.6)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        backdropFilter: "blur(4px)",
      }}
    >
      <div
        ref={modalRef}
        style={{
          backgroundColor: "#ffffff",
          borderRadius: "20px",
          width: "90%",
          maxWidth: "520px",
          maxHeight: "85vh",
          color: "#1F2937",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
          border: "1px solid rgba(229, 231, 235, 0.8)",
          animation: "modalSlideIn 0.3s ease-out",
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: "24px 28px 20px",
            borderBottom: "1px solid #F3F4F6",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            background: "linear-gradient(135deg, #F9FAFB 0%, #F3F4F6 100%)",
          }}
        >
          <div>
            <h2 style={{ margin: 0, fontSize: "24px", fontWeight: "700", color: "#111827", letterSpacing: "-0.025em" }}>
              Find Friends
            </h2>
            <p style={{ margin: "4px 0 0 0", fontSize: "14px", color: "#6B7280", fontWeight: "400" }}>
              Wanna be lonely? Don’t search here.
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              color: "#9CA3AF",
              cursor: "pointer",
              padding: "10px",
              borderRadius: "12px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              transition: "all 0.2s ease",
              width: "40px",
              height: "40px",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = "#F3F4F6"
              e.currentTarget.style.color = "#374151"
              e.currentTarget.style.transform = "scale(1.05)"
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = "transparent"
              e.currentTarget.style.color = "#9CA3AF"
              e.currentTarget.style.transform = "scale(1)"
            }}
          >
            <FaTimes size={18} />
          </button>
        </div>

        {/* Search Section */}
        <div style={{ padding: "28px 28px 24px" }}>
          <div style={{ display: "flex", gap: "16px", alignItems: "stretch" }}>
            <div style={{ position: "relative", flex: 1 }}>
              <input
                ref={searchInputRef}
                type="text"
                placeholder="Enter ID to search..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyPress={handleKeyPress}
                style={{
                  width: "100%",
                  padding: "16px 20px 16px 48px",
                  backgroundColor: "#F9FAFB",
                  border: "2px solid #E5E7EB",
                  borderRadius: "16px",
                  color: "#111827",
                  fontSize: "16px",
                  outline: "none",
                  transition: "all 0.3s ease",
                  fontWeight: "500",
                  boxShadow: "0 1px 3px rgba(0, 0, 0, 0.1)",
                }}
                onFocus={(e) => {
                  e.currentTarget.style.backgroundColor = "#ffffff"
                  e.currentTarget.style.borderColor = "#3B82F6"
                  e.currentTarget.style.boxShadow = "0 0 0 3px rgba(59, 130, 246, 0.1)"
                }}
                onBlur={(e) => {
                  e.currentTarget.style.backgroundColor = "#F9FAFB"
                  e.currentTarget.style.borderColor = "#E5E7EB"
                  e.currentTarget.style.boxShadow = "0 1px 3px rgba(0, 0, 0, 0.1)"
                }}
              />
              <FaSearch
                style={{
                  position: "absolute",
                  left: "18px",
                  top: "50%",
                  transform: "translateY(-50%)",
                  color: "#9CA3AF",
                  fontSize: "16px",
                }}
              />
            </div>
            <button
              onClick={handleSearch}
              disabled={!searchQuery.trim() || loading}
              style={{
                padding: "16px 24px",
                background:
                  !searchQuery.trim() || loading ? "#E5E7EB" : "linear-gradient(135deg, #3B82F6 0%, #1D4ED8 100%)",
                border: "none",
                borderRadius: "16px",
                color: !searchQuery.trim() || loading ? "#9CA3AF" : "white",
                fontSize: "16px",
                fontWeight: "600",
                cursor: !searchQuery.trim() || loading ? "not-allowed" : "pointer",
                transition: "all 0.3s ease",
                display: "flex",
                alignItems: "center",
                gap: "10px",
                minWidth: "120px",
                justifyContent: "center",
                boxShadow: !searchQuery.trim() || loading ? "none" : "0 4px 14px 0 rgba(59, 130, 246, 0.3)",
              }}
              onMouseEnter={(e) => {
                if (searchQuery.trim() && !loading) {
                  e.currentTarget.style.transform = "translateY(-2px)"
                  e.currentTarget.style.boxShadow = "0 8px 25px 0 rgba(59, 130, 246, 0.4)"
                }
              }}
              onMouseLeave={(e) => {
                if (searchQuery.trim() && !loading) {
                  e.currentTarget.style.transform = "translateY(0px)"
                  e.currentTarget.style.boxShadow = "0 4px 14px 0 rgba(59, 130, 246, 0.3)"
                }
              }}
            >
              {loading ? <FaSpinner className="spin" size={16} /> : <FaSearch size={16} />}
              {loading ? "Searching" : "Search"}
            </button>
          </div>
        </div>

        {/* Results Section */}
        <div
          style={{
            flex: 1,
            overflowY: "auto",
            padding: "0 28px 28px",
            minHeight: "240px",
            maxHeight: "450px",
          }}
        >
          {loading ? (
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                padding: "60px 20px",
                color: "#6B7280",
              }}
            >
              <FaSpinner className="spin" size={32} style={{ marginBottom: "16px", color: "#3B82F6" }} />
              <p style={{ margin: 0, fontSize: "18px", fontWeight: "500" }}>Searching users...</p>
              <p style={{ margin: "8px 0 0 0", fontSize: "14px", color: "#9CA3AF" }}>Please wait a moment</p>
            </div>
          ) : !hasSearched ? (
            <div
              style={{
                textAlign: "center",
                padding: "60px 20px",
                color: "#6B7280",
              }}
            >
              <div
                style={{
                  width: "80px",
                  height: "80px",
                  backgroundColor: "#F3F4F6",
                  borderRadius: "50%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  margin: "0 auto 24px",
                }}
              >
                <FaSearch size={32} style={{ color: "#9CA3AF" }} />
              </div>
              <h3 style={{ margin: "0 0 8px 0", fontSize: "20px", fontWeight: "600", color: "#374151" }}>
                Ready to search
              </h3>
              <p style={{ margin: 0, fontSize: "16px", color: "#6B7280", lineHeight: "1.5" }}>
                Enter an ID above to find and connect with other users
              </p>
            </div>
          ) : searchResults.length === 0 ? (
            <div
              style={{
                textAlign: "center",
                padding: "60px 20px",
                color: "#6B7280",
              }}
            >
              <div
                style={{
                  width: "80px",
                  height: "80px",
                  backgroundColor: "#FEF3F2",
                  borderRadius: "50%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  margin: "0 auto 24px",
                }}
              >
                <FaUser size={32} style={{ color: "#F87171" }} />
              </div>
              <h3 style={{ margin: "0 0 8px 0", fontSize: "20px", fontWeight: "600", color: "#374151" }}>
                No users found
              </h3>
              <p style={{ margin: 0, fontSize: "16px", color: "#6B7280", lineHeight: "1.5" }}>
                No users match "<strong>{searchQuery}</strong>". Try a different username.
              </p>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              {searchResults.map((user, index) => (
                <div
                  key={user.username}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "20px",
                    backgroundColor: "#FAFBFC",
                    borderRadius: "16px",
                    border: "1px solid #F3F4F6",
                    transition: "all 0.3s ease",
                    animation: `fadeInUp 0.4s ease-out ${index * 0.1}s both`,
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = "#F8FAFC"
                    e.currentTarget.style.borderColor = "#E2E8F0"
                    e.currentTarget.style.transform = "translateY(-2px)"
                    e.currentTarget.style.boxShadow = "0 8px 25px -8px rgba(0, 0, 0, 0.1)"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = "#FAFBFC"
                    e.currentTarget.style.borderColor = "#F3F4F6"
                    e.currentTarget.style.transform = "translateY(0px)"
                    e.currentTarget.style.boxShadow = "none"
                  }}
                >
                  <div style={{ display: "flex", alignItems: "center", flex: 1 }}>
                    <div
                      style={{
                        background: "linear-gradient(135deg, #E5E7EB 0%, #D1D5DB 100%)",
                        color: "#374151",
                        borderRadius: "50%",
                        width: "48px",
                        height: "48px",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        marginRight: "16px",
                        position: "relative",
                        boxShadow: "0 2px 8px rgba(0, 0, 0, 0.1)",
                      }}
                    >
                      <FaUser size={18} />
                      {/* Status indicator */}
                      <div
                        style={{
                          position: "absolute",
                          bottom: "2px",
                          right: "2px",
                          width: "14px",
                          height: "14px",
                          backgroundColor: getStatusColor(user.status),
                          borderRadius: "50%",
                          border: "3px solid #ffffff",
                          boxShadow: "0 2px 4px rgba(0, 0, 0, 0.1)",
                        }}
                      />
                    </div>
                    <div style={{ flex: 1 }}>
                      <div
                        style={{
                          fontWeight: "600",
                          fontSize: "17px",
                          marginBottom: "4px",
                          color: "#111827",
                          letterSpacing: "-0.025em",
                        }}
                      >
                        {user.nickname || user.username}
                      </div>
                      <div style={{ fontSize: "14px", color: "#6B7280", fontWeight: "500" }}>@{user.username}</div>
                    </div>
                    <div
                      style={{
                        fontSize: "12px",
                        color: getStatusColor(user.status),
                        fontWeight: "600",
                        textTransform: "capitalize",
                        marginRight: "16px",
                        padding: "4px 12px",
                        backgroundColor: `${getStatusColor(user.status)}15`,
                        borderRadius: "20px",
                        border: `1px solid ${getStatusColor(user.status)}30`,
                      }}
                    >
                      {user.status}
                    </div>
                  </div>

                  <div>
                    {user.isFriend || user.requestSent ? (
                      <span
                        style={{
                          padding: "10px 20px",
                          backgroundColor: "#ECFDF5",
                          color: "#059669",
                          borderRadius: "12px",
                          fontSize: "14px",
                          fontWeight: "600",
                          border: "1px solid #A7F3D0",
                        }}
                      >
                        {user.requestSent ? "Request Sent" : "Friends"}
                      </span>
                    ) : (
                      <button
                        onClick={() => handleSendFriendRequest(user)}
                        disabled={sendingRequests[user.username]}
                        style={{
                          padding: "10px 20px",
                          background: sendingRequests[user.username]
                            ? "#F3F4F6"
                            : "linear-gradient(135deg, #10B981 0%, #059669 100%)",
                          border: "none",
                          borderRadius: "12px",
                          color: sendingRequests[user.username] ? "#6B7280" : "white",
                          fontSize: "14px",
                          fontWeight: "600",
                          cursor: sendingRequests[user.username] ? "not-allowed" : "pointer",
                          display: "flex",
                          alignItems: "center",
                          gap: "8px",
                          transition: "all 0.3s ease",
                          boxShadow: sendingRequests[user.username] ? "none" : "0 4px 14px 0 rgba(16, 185, 129, 0.3)",
                        }}
                        onMouseEnter={(e) => {
                          if (!sendingRequests[user.username]) {
                            e.currentTarget.style.transform = "translateY(-2px)"
                            e.currentTarget.style.boxShadow = "0 8px 25px 0 rgba(16, 185, 129, 0.4)"
                          }
                        }}
                        onMouseLeave={(e) => {
                          if (!sendingRequests[user.username]) {
                            e.currentTarget.style.transform = "translateY(0px)"
                            e.currentTarget.style.boxShadow = "0 4px 14px 0 rgba(16, 185, 129, 0.3)"
                          }
                        }}
                      >
                        {sendingRequests[user.username] ? (
                          <>
                            <FaSpinner className="spin" size={12} />
                            Sending...
                          </>
                        ) : (
                          <>
                            <FaUserPlus size={12} />
                            Add Friend
                          </>
                        )}
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Enhanced CSS animations */}
      <style jsx>{`
        .spin {
          animation: spin 1s linear infinite;
        }

        @keyframes spin {
          from {
            transform: rotate(0deg);
          }
          to {
            transform: rotate(360deg);
          }
        }

        @keyframes modalSlideIn {
          from {
            opacity: 0;
            transform: scale(0.95) translateY(-20px);
          }
          to {
            opacity: 1;
            transform: scale(1) translateY(0);
          }
        }

        @keyframes fadeInUp {
          from {
            opacity: 0;
            transform: translateY(20px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
      `}</style>
    </div>
  )
}

export default FindUserModal
