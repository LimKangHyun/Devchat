import { createContext, useContext, useState, useRef, useCallback } from "react"

const AlarmContext = createContext()

export const ROOMS_CACHE_KEY = 'sidebar_rooms_cache'

// { owner, rooms } 형식. 예전 형식(배열만)은 주인을 모르니 owner 없이 읽는다
export const readRoomsCache = () => {
  try {
    const parsed = JSON.parse(localStorage.getItem(ROOMS_CACHE_KEY))
    if (Array.isArray(parsed)) return { owner: null, rooms: parsed }
    if (parsed && Array.isArray(parsed.rooms)) return { owner: parsed.owner ?? null, rooms: parsed.rooms }
  } catch { /* 깨진 캐시는 무시 */ }
  return { owner: null, rooms: [] }
}

export const clearRoomsCache = () => {
  try { localStorage.removeItem(ROOMS_CACHE_KEY) } catch { /* 무시 */ }
}

export const useAlarm = () => useContext(AlarmContext)

export const AlarmProvider = ({ children }) => {
  const [alarmStatusMap, setAlarmStatusMap] = useState({})
  const [currentRoomId, setCurrentRoomId] = useState(null)
  const currentRoomIdRef = useRef(null)

  // 방 목록 캐시는 브라우저 단위라, 어느 계정의 목록인지(owner) 같이 저장한다.
  // 다른 계정의 캐시로 방을 구독하면 남의 방 이름이 보이고 구독이 거절된다.
  const [cache] = useState(() => readRoomsCache())
  const [alarmRooms, setAlarmRooms] = useState(cache.rooms)
  const ownerRef = useRef(null)            // 지금 로그인한 계정
  const cachedOwnerRef = useRef(cache.owner)
  const [cachedOwner, setCachedOwner] = useState(cache.owner)
  const roomsVerifiedRef = useRef(false)
  const [roomsVerified, setRoomsVerified] = useState(false) // 서버에서 받은 목록인지

  /** 로그인한 계정을 알려준다. 다른 계정의 캐시면 비운다 */
  const bindRoomsToUser = useCallback((username) => {
    ownerRef.current = username || null
    if (!username || cachedOwnerRef.current === username) return

    if (roomsVerifiedRef.current) {
      // 이미 서버에서 받은 목록: 저장된 캐시에 주인만 적어 둔다
      const { rooms } = readRoomsCache()
      try { localStorage.setItem(ROOMS_CACHE_KEY, JSON.stringify({ owner: username, rooms })) } catch { /* 무시 */ }
    } else {
      setAlarmRooms([])
    }
    cachedOwnerRef.current = username
    setCachedOwner(username)
  }, [])

  /** 구독에 써도 되는 목록인지: 서버에서 받았거나, 지금 계정의 캐시 */
  const isRoomsTrusted = useCallback(
    (username) => roomsVerified || (!!username && cachedOwner === username),
    [roomsVerified, cachedOwner]
  )

  const updateAlarm = useCallback((roomId, enabled) => {
    setAlarmStatusMap(prev => ({ ...prev, [roomId]: enabled }))
  }, [])

  const getAlarmStatus = useCallback((roomId) => {
    return alarmStatusMap[roomId]
  }, [alarmStatusMap])

  const updateRooms = useCallback((rooms) => {
    setAlarmRooms(prev => {
      return rooms.map(r => {
        const existing = prev.find(p => Number(p.uniqueId) === Number(r.uniqueId))
        return {
          ...r,
          unreadCount: r.unreadCount ?? existing?.unreadCount ?? 0
        }
      })
    })
    roomsVerifiedRef.current = true
    setRoomsVerified(true)
    try {
      localStorage.setItem(ROOMS_CACHE_KEY, JSON.stringify({ owner: ownerRef.current, rooms }))
    } catch { /* 저장소를 못 쓰면 캐시 없이 동작 */ }
  }, [])

  const incrementUnread = useCallback((roomUniqueId) => {
    setAlarmRooms(prev => prev.map(r =>
      Number(r.uniqueId) === Number(roomUniqueId)
        ? { ...r, unreadCount: (r.unreadCount ?? 0) + 1 }
        : r
    ))
  }, [])

  const clearUnread = useCallback((roomUniqueId) => {
    setAlarmRooms(prev => prev.map(r =>
      Number(r.uniqueId) === Number(roomUniqueId)
        ? { ...r, unreadCount: 0 }
        : r
    ))
  }, [])

  const bringRoomToTop = useCallback((roomUniqueId) => {
    setAlarmRooms(prev => {
      const room = prev.find(r => r.uniqueId === roomUniqueId)
      if (!room) return prev
      return [room, ...prev.filter(r => r.uniqueId !== roomUniqueId)]
    })
  }, [])

  const enterRoom = useCallback((roomId) => {
    currentRoomIdRef.current = roomId  // 즉시 동기 업데이트
    setCurrentRoomId(roomId)
  }, [])

  const leaveRoom = useCallback(() => {
    currentRoomIdRef.current = null  // 즉시 동기 업데이트
    setCurrentRoomId(null)
  }, [])

  return (
    <AlarmContext.Provider value={{
      alarmStatusMap,
      alarmRooms,
      currentRoomId,
      currentRoomIdRef,
      updateAlarm,
      getAlarmStatus,
      updateRooms,
      incrementUnread,
      clearUnread,
      bringRoomToTop,
      enterRoom,
      leaveRoom,
      bindRoomsToUser,
      isRoomsTrusted,
    }}>
      {children}
    </AlarmContext.Provider>
  )
}