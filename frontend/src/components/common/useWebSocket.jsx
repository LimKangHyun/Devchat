import { useEffect, useRef, useCallback } from "react"
import { useWebSocketContext } from "./WebSocketContext"

const useWebSocket = ({
  roomId,
  username,
  onMessageReceived,
  onNotificationReceived,
  onProfileUpdate,
  onRoomDeleted,
  dmRoomId,
  onDmMessageReceived,
  onError,
}) => {
  const { stompClientRef, connected } = useWebSocketContext()

  const onMessageReceivedRef = useRef(onMessageReceived)
  const onNotificationReceivedRef = useRef(onNotificationReceived)
  const onProfileUpdateRef = useRef(onProfileUpdate)
  const onRoomDeletedRef = useRef(onRoomDeleted)
  const onDmMessageReceivedRef = useRef(onDmMessageReceived)
  const onErrorRef = useRef(onError)

  useEffect(() => { onMessageReceivedRef.current = onMessageReceived }, [onMessageReceived])
  useEffect(() => { onNotificationReceivedRef.current = onNotificationReceived }, [onNotificationReceived])
  useEffect(() => { onProfileUpdateRef.current = onProfileUpdate }, [onProfileUpdate])
  useEffect(() => { onRoomDeletedRef.current = onRoomDeleted }, [onRoomDeleted])
  useEffect(() => { onDmMessageReceivedRef.current = onDmMessageReceived }, [onDmMessageReceived])
  useEffect(() => { onErrorRef.current = onError }, [onError])

  const safeUnsubscribe = useCallback((subs) => {
    subs.forEach((sub) => {
      try { sub?.unsubscribe() } catch (e) {}
    })
  }, [])

  // 채팅방 메시지 + 방 삭제 구독
  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !roomId) return

    const subs = [
      client.subscribe(`/topic/chat/${roomId}`, (message) => {
        try {
          const received = JSON.parse(message.body)
          received.sendAt = received.sendAt || new Date().toISOString()
          onMessageReceivedRef.current?.(received)
        } catch (e) {
          console.error("📛 Failed to parse chat message", e)
        }
      }),
      client.subscribe(`/topic/chat/${roomId}/deleted`, (message) => {
        try {
          onRoomDeletedRef.current?.(JSON.parse(message.body))
        } catch (e) {
          console.error("📛 Failed to parse delete message", e)
        }
      }),
    ]

    return () => safeUnsubscribe(subs)
  }, [connected, roomId, safeUnsubscribe])

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

    return () => safeUnsubscribe([sub])
  }, [connected, onProfileUpdate, safeUnsubscribe])

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

    return () => safeUnsubscribe([sub])
  }, [connected, username, safeUnsubscribe])

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

    return () => safeUnsubscribe([sub])
  }, [connected, dmRoomId, safeUnsubscribe])

  // 에러 구독 - onError 있을 때만
  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !onErrorRef.current) return

    const sub = client.subscribe(`/user/queue/errors`, (message) => {
      try {
        const error = JSON.parse(message.body)
        onErrorRef.current?.(error.message)
      } catch (e) {
        console.error("📛 Failed to parse error message", e)
      }
    })

    return () => safeUnsubscribe([sub])
  }, [connected, safeUnsubscribe])

  return { stompClientRef, connected }
}

export default useWebSocket