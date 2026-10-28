"use client"

import { useState, useEffect, useRef, memo, useCallback, useMemo } from "react"
import { FaSearch, FaTimes, FaUserPlus, FaUser, FaSpinner } from "react-icons/fa"
import axioxInstance from "../api/axiosInstance"
import "./FindUserModal.css"

// Memoized Header Component - never re-renders
const ModalHeader = memo(({ onClose }) => (
    <div className="dc-modal-header fum-header">
        <div>
            <h2 className="dc-modal-title">친구 찾기</h2>
            <p className="fum-subtitle">아이디로 검색해서 친구 요청을 보내세요.</p>
        </div>
        <button onClick={onClose} className="dc-icon-btn" aria-label="닫기" title="닫기">
            <FaTimes size={16} />
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
        <div className="fum-search">
            <div className="fum-search-field">
                <FaSearch className="fum-search-icon" size={14} />
                <input
                    ref={searchInputRef}
                    type="text"
                    placeholder="아이디를 입력하세요"
                    value={inputValue}
                    onChange={handleInputChange}
                    onKeyPress={handleKeyPress}
                    className="dc-input fum-search-input"
                />
                {loading && <FaSpinner className="spinner-animation fum-search-spinner" size={14} />}
            </div>
            <button
                onClick={onSearch}
                disabled={!hasValidQuery || loading}
                className="dc-btn dc-btn-primary fum-search-btn"
            >
                {loading ? "검색 중" : "검색"}
            </button>
        </div>
    )
})

// Highly optimized User Item - no inline styles, no manual DOM manipulation
// 친구가 아닌 사람의 접속 상태는 보여주지 않는다
const UserItem = memo(({ user, sendingRequest, onSendFriendRequest }) => {
    const handleAddFriend = useCallback(() => {
        onSendFriendRequest(user)
    }, [user, onSendFriendRequest])

    return (
        <div className="user-item">
            <div className="user-avatar">
                <FaUser size={16} />
            </div>
            <div className="user-info">
                <div className="user-name-line">
                    <span className="user-name">{user.nickname || user.username}</span>
                </div>
                <div className="user-username">@{user.username}</div>
            </div>
            <div className="user-action">
                {user.friend || user.requestSent ? (
                    <span className="friend-status">{user.requestSent ? "요청 보냄" : "친구"}</span>
                ) : (
                    <button onClick={handleAddFriend} disabled={sendingRequest} className="dc-btn add-friend-btn">
                        {sendingRequest ? (
                            <>
                                <FaSpinner className="spinner-animation" size={12} />
                                보내는 중...
                            </>
                        ) : (
                            <>
                                <FaUserPlus size={12} />
                                친구 추가
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
        <div className="user-list">
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
                <FaSpinner className="spinner-animation empty-state-spinner" size={20} />
                <p>검색 중...</p>
            </div>
        )
    }

    if (type === "scroll") {
        return (
            <div className="loading-overlay">
                <FaSpinner className="spinner-animation" size={14} />
                <span>불러오는 중...</span>
            </div>
        )
    }

    if (type === "no-results") {
        return (
            <div className="empty-state">
                <h3>검색 결과가 없어요</h3>
                <p>
                    "<SearchQueryDisplay searchQuery={searchQuery} />
                    "와 일치하는 사용자가 없어요. 다른 아이디로 검색해보세요.
                </p>
            </div>
        )
    }

    return (
        <div className="empty-state">
            <div className="empty-state-icon">
                <FaSearch size={18} />
            </div>
            <h3>친구를 찾아보세요</h3>
            <p>위에 아이디를 입력하면 검색 결과가 여기에 나와요</p>
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
                            <FaSpinner className="spinner-animation" size={14} />
                            <span>더 불러오는 중...</span>
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
            <div className="results-container dc-scroll" onScroll={onScroll}>
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
        <div className="dc-modal-backdrop modal-overlay">
            <div ref={modalRef} className="dc-modal modal-container">
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
