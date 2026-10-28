import { createContext, useContext, useEffect, useCallback } from "react"
import { useWebSocketContext } from "../components/common/WebSocketContext"
import { useAlarm } from "./AlarmContext"
import { useUser } from "./UserContext"

const ChatSubscriptionContext = createContext(null)
export const useChatSubscription = () => useContext(ChatSubscriptionContext)

export const ChatSubscriptionProvider = ({ children }) => {
  const { stompClientRef, connected } = useWebSocketContext()
  const { alarmRooms, currentRoomIdRef, incrementUnread, bringRoomToTop, getAlarmStatus, bindRoomsToUser, isRoomsTrusted } = useAlarm()
  const { currentUser } = useUser() || {}
  const username = currentUser?.username

  // 다른 계정이 남긴 방 목록 캐시를 쓰지 않도록 지금 계정을 알려준다
  useEffect(() => { bindRoomsToUser(username) }, [username, bindRoomsToUser])
  const roomsTrusted = isRoomsTrusted(username)

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
    if (!connected || !client?.connected || !roomsTrusted || !alarmRooms.length) return

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
                body: received.type === "IMAGE" ? "[이미지]" : received.type === "CODE" ? "[코드]" : received.content,
                senderImg: received.profileImageUrl,
                url: room?.inviteCode ? `/chat/${room.inviteCode}` : undefined,
                roomId: room.uniqueId, // 알림 tag(room-{id})로 같은 방 알림을 하나로 합친다
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
  }, [connected, chatRoomIdsKey, roomsTrusted, safeUnsubscribe])

  return (
    <ChatSubscriptionContext.Provider value={{}}>
      {children}
    </ChatSubscriptionContext.Provider>
  )
}