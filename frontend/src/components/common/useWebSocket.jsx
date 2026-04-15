import { useEffect, useRef } from "react"
import { useWebSocketContext } from "./WebSocketContext"

const useWebSocket = ({
  roomId,
  username,
  onMessageReceived,
  onNotificationReceived,
  chatRooms = [],
  currentRoomId,
  onSidebarMessage,
  onProfileUpdate,
  onRoomDeleted,
  dmRoomId,
  onDmMessageReceived,
}) => {
  const { stompClientRef, connected } = useWebSocketContext()

  const onMessageReceivedRef = useRef(onMessageReceived)
  const onNotificationReceivedRef = useRef(onNotificationReceived)
  const onProfileUpdateRef = useRef(onProfileUpdate)
  const onRoomDeletedRef = useRef(onRoomDeleted)
  const onSidebarMessageRef = useRef(onSidebarMessage)
  const onDmMessageReceivedRef = useRef(onDmMessageReceived)
  const currentRoomIdRef = useRef(currentRoomId)

  useEffect(() => { onMessageReceivedRef.current = onMessageReceived }, [onMessageReceived])
  useEffect(() => { onNotificationReceivedRef.current = onNotificationReceived }, [onNotificationReceived])
  useEffect(() => { onProfileUpdateRef.current = onProfileUpdate }, [onProfileUpdate])
  useEffect(() => { onRoomDeletedRef.current = onRoomDeleted }, [onRoomDeleted])
  useEffect(() => { onSidebarMessageRef.current = onSidebarMessage }, [onSidebarMessage])
  useEffect(() => { onDmMessageReceivedRef.current = onDmMessageReceived }, [onDmMessageReceived])
  useEffect(() => { currentRoomIdRef.current = currentRoomId }, [currentRoomId])

  // 채팅방 메시지 + 방 삭제 구독
  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !roomId) return

    const subs = []

    subs.push(client.subscribe(`/topic/chat/${roomId}`, (message) => {
      try {
        const received = JSON.parse(message.body)
        received.sendAt = received.sendAt || new Date().toISOString()
        onMessageReceivedRef.current?.(received)
      } catch (e) {
        console.error("📛 Failed to parse chat message", e)
      }
    }))

    subs.push(client.subscribe(`/topic/chat/${roomId}/deleted`, (message) => {
      try {
        onRoomDeletedRef.current?.(JSON.parse(message.body))
      } catch (e) {
        console.error("📛 Failed to parse delete message", e)
      }
    }))

    return () => subs.forEach((sub) => sub.unsubscribe())
  }, [connected, roomId])

  // 프로필 업데이트 구독
  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !onProfileUpdate) return

    const sub = client.subscribe("/topic/profile-update", (message) => {
      try {
        onProfileUpdateRef.current?.(JSON.parse(message.body))
      } catch (e) {
        console.error("📛 Failed to parse profile update message", e)
      }
    })

    return () => sub.unsubscribe()
  }, [connected, onProfileUpdate])

  // 알림 구독
  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !username) return

    const sub = client.subscribe(`/topic/notifications/${username}`, (message) => {
      try {
        const notification = JSON.parse(message.body)
        notification.timestamp = new Date().toISOString()
        notification.id = `${Date.now()}-${Math.random()}`
        onNotificationReceivedRef.current?.(notification)
      } catch (e) {
        console.error("📛 Failed to parse notification message", e)
      }
    })

    return () => sub.unsubscribe()
  }, [connected, username])

  // 사이드바 구독
  const chatRoomIdsKey = chatRooms.map(r => r.uniqueId).join(',')

  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !chatRooms.length) return

    const subs = []

    chatRooms.forEach((room) => {
      const roomUniqueId = room.uniqueId
      if (!roomUniqueId) return
      if (Number(currentRoomIdRef.current) === Number(roomUniqueId)) return

      const sub = client.subscribe(`/topic/chat/${roomUniqueId}`, (message) => {
        try {
          const received = JSON.parse(message.body)
          if (received.type !== "EVENT" && Number(currentRoomIdRef.current) !== Number(roomUniqueId)) {
            onSidebarMessageRef.current?.(roomUniqueId, received)
          }
        } catch (e) {
          console.error("📛 Failed to parse sidebar message", e)
        }
      })

      subs.push(sub)
    })

    return () => subs.forEach((sub) => sub.unsubscribe())
  }, [connected, chatRoomIdsKey])

  // DM 구독
  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !dmRoomId) return

    const sub = client.subscribe(`/topic/dm/${dmRoomId}`, (message) => {
      try {
        onDmMessageReceivedRef.current?.(JSON.parse(message.body))
      } catch (e) {
        console.error("📛 Failed to parse DM message:", e)
      }
    })

    return () => sub.unsubscribe()
  }, [connected, dmRoomId])

  return { stompClientRef, connected }
}

export default useWebSocket