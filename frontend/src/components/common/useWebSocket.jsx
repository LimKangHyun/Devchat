"use client"

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

  // 모든 콜백을 ref로 관리 → 구독 재등록 없이 항상 최신 함수 호출
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

  const subscriptionRef = useRef(null)
  const notificationSubscriptionRef = useRef(null)
  const profileSubscriptionRef = useRef(null)
  const deleteSubscriptionRef = useRef(null)
  const sidebarSubscriptionsRef = useRef(new Map())

  // 채팅방 + 프로필 + 방 삭제 + 알림 구독
  // 콜백은 ref로 관리하므로 의존성에서 제외 → roomId, connected 바뀔 때만 재구독
  useEffect(() => {
    const client = stompClientRef.current
    if (!client?.connected) return

    if (roomId) {
      subscriptionRef.current?.unsubscribe()
      subscriptionRef.current = client.subscribe(`/topic/chat/${roomId}`, (message) => {
        try {
          const received = JSON.parse(message.body)
          received.sendAt = received.sendAt || new Date().toISOString()
          onMessageReceivedRef.current?.(received)
        } catch (e) {
          console.error("📛 Failed to parse chat message", e)
        }
      })

      deleteSubscriptionRef.current?.unsubscribe()
      deleteSubscriptionRef.current = client.subscribe(`/topic/chat/${roomId}/deleted`, (message) => {
        try {
          onRoomDeletedRef.current?.(JSON.parse(message.body))
        } catch (e) {
          console.error("📛 Failed to parse delete message", e)
        }
      })
    }

    if (username) {
      notificationSubscriptionRef.current?.unsubscribe()
      notificationSubscriptionRef.current = client.subscribe(`/topic/notifications/${username}`, (message) => {
        try {
          const notification = JSON.parse(message.body)
          notification.timestamp = new Date().toISOString()
          notification.id = `${Date.now()}-${Math.random()}`
          onNotificationReceivedRef.current?.(notification)
        } catch (e) {
          console.error("📛 Failed to parse notification message", e)
        }
      })
    }

    profileSubscriptionRef.current?.unsubscribe()
    profileSubscriptionRef.current = client.subscribe("/topic/profile-update", (message) => {
      try {
        onProfileUpdateRef.current?.(JSON.parse(message.body))
      } catch (e) {
        console.error("📛 Failed to parse profile update message", e)
      }
    })

    return () => {
      subscriptionRef.current?.unsubscribe()
      subscriptionRef.current = null
      deleteSubscriptionRef.current?.unsubscribe()
      deleteSubscriptionRef.current = null
      notificationSubscriptionRef.current?.unsubscribe()
      notificationSubscriptionRef.current = null
      profileSubscriptionRef.current?.unsubscribe()
      profileSubscriptionRef.current = null
    }
  }, [connected, roomId, username])

  // 사이드바 구독 (현재 방 제외)
  useEffect(() => {
    const client = stompClientRef.current
    if (!client?.connected || !chatRooms.length) return

    sidebarSubscriptionsRef.current.forEach((sub) => sub.unsubscribe())
    sidebarSubscriptionsRef.current.clear()

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

      sidebarSubscriptionsRef.current.set(roomUniqueId, sub)
    })

    return () => {
      sidebarSubscriptionsRef.current.forEach((sub) => sub.unsubscribe())
      sidebarSubscriptionsRef.current.clear()
    }
  }, [connected, chatRooms, currentRoomId])

  // DM 구독
  useEffect(() => {
    const client = stompClientRef.current
    if (!client?.connected || !dmRoomId) return

    const subscription = client.subscribe(`/topic/dm/${dmRoomId}`, (message) => {
      try {
        onDmMessageReceivedRef.current?.(JSON.parse(message.body))
      } catch (e) {
        console.error("📛 Failed to parse DM message:", e)
      }
    })

    return () => subscription.unsubscribe()
  }, [connected, dmRoomId])

  return { stompClientRef, connected }
}

export default useWebSocket