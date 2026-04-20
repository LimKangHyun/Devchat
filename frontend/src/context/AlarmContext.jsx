import { createContext, useContext, useState, useCallback } from "react"

const AlarmContext = createContext()

export const useAlarm = () => useContext(AlarmContext)

export const AlarmProvider = ({ children }) => {
  const [alarmStatusMap, setAlarmStatusMap] = useState({})
  const [currentRoomId, setCurrentRoomId] = useState(null)
  const [alarmRooms, setAlarmRooms] = useState(() => {
    try {
      const cached = localStorage.getItem('sidebar_rooms_cache')
      return cached ? JSON.parse(cached) : []
    } catch {
      return []
    }
  })

  const updateAlarm = useCallback((roomId, enabled) => {
    setAlarmStatusMap(prev => ({ ...prev, [roomId]: enabled }))
  }, [])

  const getAlarmStatus = useCallback((roomId) => {
    return alarmStatusMap[roomId]
  }, [alarmStatusMap])

  const updateRooms = useCallback((rooms) => {
    setAlarmRooms(prev => {
      const prevIds = prev.map(r => r.uniqueId).join(',')
      const nextIds = rooms.map(r => r.uniqueId).join(',')
      if (prevIds === nextIds) {
        return rooms.map(r => {
          const existing = prev.find(p => p.uniqueId === r.uniqueId)
          return existing ? { ...r, unreadCount: existing.unreadCount } : r
        })
      }
      return rooms
    })
    localStorage.setItem('sidebar_rooms_cache', JSON.stringify(rooms))
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
    setCurrentRoomId(roomId)
  }, [])

  const leaveRoom = useCallback(() => {
    setCurrentRoomId(null)
  }, [])

  return (
    <AlarmContext.Provider value={{
      alarmStatusMap,
      alarmRooms,
      currentRoomId,
      updateAlarm,
      getAlarmStatus,
      updateRooms,
      incrementUnread,
      clearUnread,
      bringRoomToTop,
      enterRoom,
      leaveRoom,
    }}>
      {children}
    </AlarmContext.Provider>
  )
}