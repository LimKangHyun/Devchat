import { createContext, useContext, useEffect, useCallback } from "react"
import { useWebSocketContext } from "../components/common/WebSocketContext"
import { useAlarm } from "./AlarmContext"

const ChatSubscriptionContext = createContext(null)
export const useChatSubscription = () => useContext(ChatSubscriptionContext)

export const ChatSubscriptionProvider = ({ children }) => {
  const { stompClientRef, connected } = useWebSocketContext()
  const { alarmRooms, currentRoomIdRef, incrementUnread, bringRoomToTop, getAlarmStatus } = useAlarm()

  const chatRoomIdsKey = alarmRooms.map(r => r.uniqueId).join(',')

  const safeUnsubscribe = useCallback((subs) => {
    const client = stompClientRef.current
    if (!client?.connected) return
    subs.forEach(sub => {
      try { sub?.unsubscribe() } catch (e) {}
    })
  }, [stompClientRef])

  useEffect(() => {
    const client = stompClientRef.current
    if (!connected || !client?.connected || !alarmRooms.length) return

    const subs = alarmRooms
      .filter(room => room.uniqueId)
      .map(room => client.subscribe(`/topic/chat/${room.uniqueId}`, (message) => {
        try {
          const received = JSON.parse(message.body)
          if (received.type === "EVENT") return
          if (Number(currentRoomIdRef.current) === Number(room.uniqueId)) {
            return
          }
          incrementUnread(room.uniqueId)
          bringRoomToTop(room.uniqueId)

          const isAlarmEnabled = getAlarmStatus(room.uniqueId)
          if (isAlarmEnabled === false) return

          window.dispatchEvent(
            new CustomEvent("chat:notify", {
              detail: {
                senderNickname: received.senderName,
                body: received.content,
                senderImg: received.profileImageUrl,
                url: room?.inviteCode ? `/chat/${room.inviteCode}` : undefined,
                tag: `room-${Date.now()}`,
                silent: false,
                roomName: room?.roomName || `Room ${room.uniqueId}`
              },
            })
          )
        } catch (e) {
          console.error("📛 Failed to parse sidebar message", e)
        }
      }))

    return () => safeUnsubscribe(subs)
  }, [connected, chatRoomIdsKey, safeUnsubscribe])

  return (
    <ChatSubscriptionContext.Provider value={{}}>
      {children}
    </ChatSubscriptionContext.Provider>
  )
}