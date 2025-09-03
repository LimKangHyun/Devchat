"use client"

import { useState, useEffect, useRef, memo, useCallback, useMemo } from "react"
import { FaSearch, FaTimes, FaUserPlus, FaUser, FaSpinner } from "react-icons/fa"
import axioxInstance from "../api/axiosInstance"
import "./FindUserModal.css"

// Memoized Header Component - never re-renders
const ModalHeader = memo(({ onClose }) => (
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
                Wanna be lonely? Don't search here.
            </p>
        </div>
        <button onClick={onClose} className="close-btn">
            <FaTimes size={18} />
        </button>
    </div>
))

// Separate component for search query display - only re-renders when query changes
const SearchQueryDisplay = memo(({ searchQuery }) => <strong>{searchQuery}</strong>)

// Optimized Search Section - minimal re-renders
const SearchSection = memo(({ onSearchChange, onSearch, loading, hasValidQuery }) => {
    const [inputValue, setInputValue] = useState("")
    const searchInputRef = useRef(null)

    useEffect(() => {
        if (searchInputRef.current) {
            searchInputRef.current.focus()
        }
    }, [])

    const handleInputChange = useCallback(
        (e) => {
            const value = e.target.value
            setInputValue(value)
            onSearchChange(value)
        },
        [onSearchChange],
    )

    const handleKeyPress = useCallback(
        (event) => {
            if (event.key === "Enter") {
                onSearch()
            }
        },
        [onSearch],
    )

    return (
        <div style={{ padding: "28px 28px 24px" }}>
            <div style={{ display: "flex", gap: "16px", alignItems: "stretch" }}>
                <div style={{ position: "relative", flex: 1 }}>
                    <input
                        ref={searchInputRef}
                        type="text"
                        placeholder="Enter ID to search..."
                        value={inputValue}
                        onChange={handleInputChange}
                        onKeyPress={handleKeyPress}
                        className="search-input"
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
                <button onClick={onSearch} disabled={!hasValidQuery || loading} className="search-btn">
                    <div style={{ position: "relative", width: "16px", height: "16px" }}>
                        <FaSearch
                            size={16}
                            style={{ position: "absolute", top: 0, left: 0, visibility: loading ? "hidden" : "visible" }}
                        />
                        <FaSpinner
                            className="spinner-animation"
                            size={16}
                            style={{ position: "absolute", top: 0, left: 0, visibility: loading ? "visible" : "hidden" }}
                        />
                    </div>
                    <span style={{ minWidth: "72px", textAlign: "center" }}>
                        {loading ? "Searching" : "Search"}
                    </span>
                </button>
            </div>
        </div>
    )
})

// Highly optimized User Item - no inline styles, no manual DOM manipulation
const UserItem = memo(({ user, sendingRequest, onSendFriendRequest }) => {
    const statusClass = useMemo(() => {
        switch (user.status) {
            case "online":
                return "online"
            case "away":
                return "away"
            case "offline":
                return "offline"
            default:
                return "offline"
        }
    }, [user.status])

    const handleAddFriend = useCallback(() => {
        onSendFriendRequest(user)
    }, [user, onSendFriendRequest])

    return (
        <div className="user-item">
            <div style={{ display: "flex", alignItems: "center", flex: 1 }}>
                <div className="user-avatar">
                    <FaUser size={18} />
                    <div className={`status-indicator status-${statusClass}`} />
                </div>
                <div className="user-info">
                    <div className="user-name">{user.nickname || user.username}</div>
                    <div className="user-username">@{user.username}</div>
                </div>
                <div className={`status-badge ${statusClass}`}>{user.status}</div>
            </div>
            <div>
                {user.friend || user.requestSent ? (
                    <span className="friend-status">{user.requestSent ? "Request Sent" : "Friends"}</span>
                ) : (
                    <button onClick={handleAddFriend} disabled={sendingRequest} className="add-friend-btn">
                        {sendingRequest ? (
                            <>
                                <FaSpinner className="spinner-animation" size={12} />
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
    )
})

// Optimized Results List - virtualization-ready
const ResultsList = memo(({ results, sendingRequests, onSendFriendRequest }) => {
    return (
        <div>
            {results.map((user) => (
                <UserItem
                    key={user.username}
                    user={user}
                    sendingRequest={sendingRequests[user.username]}
                    onSendFriendRequest={onSendFriendRequest}
                />
            ))}
        </div>
    )
})

// Optimized Loading States
const LoadingState = memo(({ type, searchQuery }) => {
    if (type === "initial") {
        return (
            <div className="empty-state">
                <FaSpinner className="spinner-animation" size={32} style={{ marginBottom: "16px", color: "#3B82F6" }} />
                <p style={{ margin: 0, fontSize: "18px", fontWeight: "500" }}>Searching users...</p>
                <p style={{ margin: "8px 0 0 0", fontSize: "14px", color: "#9CA3AF" }}>Please wait a moment</p>
            </div>
        )
    }

    if (type === "scroll") {
        return (
            <div className="loading-overlay">
                <FaSpinner className="spinner-animation" size={24} style={{ marginRight: "12px", color: "#3B82F6" }} />
                <span style={{ fontSize: "16px", fontWeight: "500" }}>Loading users...</span>
            </div>
        )
    }

    if (type === "no-results") {
        return (
            <div className="empty-state">
                <div className="empty-state-icon no-results">
                    <FaUser size={32} style={{ color: "#F87171" }} />
                </div>
                <h3>No users found</h3>
                <p>
                    No users match "<SearchQueryDisplay searchQuery={searchQuery} />
                    ". Try a different username.
                </p>
            </div>
        )
    }

    return (
        <div className="empty-state">
            <div className="empty-state-icon search">
                <FaSearch size={32} style={{ color: "#9CA3AF" }} />
            </div>
            <h3>Ready to search</h3>
            <p>Enter an ID above to find and connect with other users</p>
        </div>
    )
})

// Optimized Results Component - minimal re-renders
const SearchResults = memo(
    ({
        hasSearched,
        isInitialSearch,
        isFetchingMore,
        searchResults,
        lastSearchQuery,
        sendingRequests,
        onSendFriendRequest,
        onScroll,
    }) => {
        // Determine what to show
        const content = useMemo(() => {
            if (!hasSearched) {
                return <LoadingState type="ready" />
            }

            if (isInitialSearch) {
                return <LoadingState type="initial" />
            }

            if (searchResults.length === 0 && !isFetchingMore) {
                return <LoadingState type="no-results" searchQuery={lastSearchQuery} />
            }

            if (searchResults.length === 0 && isFetchingMore) {
                return <LoadingState type="scroll" />
            }

            return (
                <>
                    <ResultsList
                        results={searchResults}
                        sendingRequests={sendingRequests}
                        onSendFriendRequest={onSendFriendRequest}
                    />
                    {isFetchingMore && (
                        <div className="scroll-loading">
                            <div className="scroll-loading-content">
                                <FaSpinner className="spinner-animation" size={18} style={{ marginRight: "10px", color: "#3B82F6" }} />
                                <span style={{ fontSize: "14px", fontWeight: "500" }}>Loading more users...</span>
                            </div>
                        </div>
                    )}
                </>
            )
        }, [
            hasSearched,
            isInitialSearch,
            isFetchingMore,
            searchResults,
            lastSearchQuery,
            sendingRequests,
            onSendFriendRequest,
        ])

        return (
            <div className="results-container" onScroll={onScroll}>
                {content}
            </div>
        )
    },
)

const FindUserModal = ({ onClose, onSendFriendRequest }) => {
    const [currentInputValue, setCurrentInputValue] = useState("")
    const [lastSearchQuery, setLastSearchQuery] = useState("")
    const [searchResults, setSearchResults] = useState([])
    const [loading, setLoading] = useState(false)
    const [hasSearched, setHasSearched] = useState(false)
    const [sendingRequests, setSendingRequests] = useState({})
    const modalRef = useRef(null)

    const [page, setPage] = useState(0)
    const [hasMore, setHasMore] = useState(true)
    const [isFetchingMore, setIsFetchingMore] = useState(false)
    const [isInitialSearch, setIsInitialSearch] = useState(false)

    // Memoized values
    const hasValidQuery = useMemo(() => currentInputValue.trim().length > 0, [currentInputValue])

    // Event handlers
    useEffect(() => {
        const handleClickOutside = (event) => {
            if (modalRef.current && !modalRef.current.contains(event.target)) {
                onClose()
            }
        }

        const handleEscape = (event) => {
            if (event.key === "Escape") {
                onClose()
            }
        }

        document.addEventListener("mousedown", handleClickOutside)
        document.addEventListener("keydown", handleEscape)
        return () => {
            document.removeEventListener("mousedown", handleClickOutside)
            document.removeEventListener("keydown", handleEscape)
        }
    }, [onClose])

    // Debounced search
    useEffect(() => {
        if (!currentInputValue.trim()) {
            setSearchResults([])
            setHasSearched(false)
            setPage(0)
            setHasMore(true)
            setLastSearchQuery("")
            return
        }

        const delayDebounce = setTimeout(() => {
            handleSearch(0, currentInputValue.trim())
            setHasSearched(true)
        }, 150)

        return () => clearTimeout(delayDebounce)
    }, [currentInputValue])

    const handleSearch = useCallback(
        async (targetPage = 0, queryToSearch = null) => {
            const trimmedQuery = queryToSearch || currentInputValue.trim()
            if (!trimmedQuery) return

            if (targetPage === 0) {
                setIsInitialSearch(true)
                setLoading(true)
                setSearchResults([])
                setPage(0)
                setHasMore(true)
                setLastSearchQuery(trimmedQuery)
            } else {
                setIsFetchingMore(true)
            }

            try {
                // 최소 로딩 시간 보장: 500ms
                const delay = new Promise((res) => setTimeout(res, 500))
                const response = await Promise.all([
                    axioxInstance.get("/user/search", {
                        params: {
                            nickname: trimmedQuery,
                            page: targetPage,
                            size: 10,
                        },
                    }),
                    delay,
                ]).then(([res]) => res)

                const content = response.data.content || []
                const isLastPage = response.data.last

                setSearchResults((prev) => (targetPage === 0 ? content : [...prev, ...content]))
                setPage(targetPage)
                setHasMore(!isLastPage)
            } catch (err) {
                console.error("사용자 검색 오류:", err)
                if (targetPage === 0) {
                    setSearchResults([])
                    setHasMore(false)
                }
            } finally {
                if (targetPage === 0) {
                    setIsInitialSearch(false)
                    setLoading(false)
                } else {
                    setIsFetchingMore(false)
                }
            }
        },
        [currentInputValue]
    )

    const handleScroll = useCallback(
        (e) => {
            const { scrollTop, scrollHeight, clientHeight } = e.target
            if (scrollHeight - scrollTop <= clientHeight + 100 && hasMore && !isFetchingMore && !isInitialSearch) {
                handleSearch(page + 1, lastSearchQuery)
            }
        },
        [hasMore, isFetchingMore, isInitialSearch, page, handleSearch, lastSearchQuery],
    )

    const handleSendFriendRequest = useCallback(
    async (user) => {
        setSendingRequests((prev) => ({ ...prev, [user.username]: true }));

        try {
        // ✅ 실제 API 호출
        await axioxInstance.post("/friend/request", {
            targetUsername: user.username
        });

        // ✅ UI 업데이트
        setSearchResults((prev) =>
            prev.map((u) =>
            u.username === user.username
                ? { ...u, isFriend: false, requestSent: true }
                : u
            )
        );

        // ✅ 요청 완료 상태 해제
        setSendingRequests((prev) => ({ ...prev, [user.username]: false }));

        // ✅ 부모 콜백 호출
        if (onSendFriendRequest) {
            onSendFriendRequest(user);
        }

        console.log(`✅ Friend request sent to ${user.username}`);
        } catch (err) {
        console.error("❌ 친구 요청 전송 실패:", err);
        setSendingRequests((prev) => ({ ...prev, [user.username]: false }));
        const msg = err?.response?.data?.message
        alert(msg);
        }
    },
    [onSendFriendRequest]
    );

    // Stable handlers
    const handleSearchChange = useCallback((value) => {
        setCurrentInputValue(value)
    }, [])

    const handleSearchClick = useCallback(() => {
        handleSearch(0, currentInputValue.trim())
    }, [handleSearch, currentInputValue])

    return (
        <div className="modal-overlay">
            <div ref={modalRef} className="modal-container modal-slide-in">
                <ModalHeader onClose={onClose} />
                <SearchSection
                    onSearchChange={handleSearchChange}
                    onSearch={handleSearchClick}
                    loading={loading}
                    hasValidQuery={hasValidQuery}
                />
                <SearchResults
                    hasSearched={hasSearched}
                    isInitialSearch={isInitialSearch}
                    isFetchingMore={isFetchingMore}
                    searchResults={searchResults}
                    lastSearchQuery={lastSearchQuery}
                    sendingRequests={sendingRequests}
                    onSendFriendRequest={handleSendFriendRequest}
                    onScroll={handleScroll}
                />
            </div>
        </div>
    )
}

export default FindUserModal
