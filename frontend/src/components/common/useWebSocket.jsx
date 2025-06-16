"use client"

import { useEffect, useRef } from "react"
import { Client } from "@stomp/stompjs"
import { useNavigate } from "react-router-dom"
import { safeRefreshToken } from "../api/refreshManager"

const useWebSocketNotifications = ({
  roomId,
  username,
  onMessageReceived,
  onNotificationReceived,
  chatRooms = [],
  currentRoomId,
  onSidebarMessage,
  onProfileUpdate,
  onRoomDeleted,
}) => {
  const stompClientRef = useRef(null)
  const subscriptionRef = useRef(null)
  const notificationSubscriptionRef = useRef(null) // New ref for notification subscription
  const profileSubscriptionRef = useRef(null)
  const hasConnectedRef = useRef(false)
  const sidebarSubscriptionsRef = useRef(new Map())
  const keepAliveIntervalRef = useRef(null)
  const deleteSubscriptionRef = useRef(null)

  const navigate = useNavigate()

  useEffect(() => {
    if (!roomId && !username) {
      console.log("⏳ roomId와 username이 없어서 웹소켓 연결을 대기합니다.")
      return
    }

    const client = new Client({
      webSocketFactory: () => new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL),
      reconnectDelay: 1000,
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 10000,
      withCredentials: true,
      debug: (str) => console.log(`[STOMP] ${str}`),

      onConnect: () => {
        console.log("✅ Connected to WebSocket")
        hasConnectedRef.current = true

        // Chat room subscription (existing functionality)
        if (roomId && onMessageReceived) {
          if (subscriptionRef.current) {
            subscriptionRef.current.unsubscribe()
            console.log("🔁 Previous chat subscription cleared.")
          }

          subscriptionRef.current = client.subscribe(`/topic/chat/${roomId}`, (message) => {
            try {
              const received = JSON.parse(message.body)
              received.sendAt = received.sendAt || new Date().toISOString()
              onMessageReceived(received)
            } catch (e) {
              console.error("📛 Failed to parse incoming chat message", e)
            }
          })
        }

        // Notification subscription (new functionality)
        if (username && onNotificationReceived) {
          if (notificationSubscriptionRef.current) {
            notificationSubscriptionRef.current.unsubscribe()
            console.log("🔁 Previous notification subscription cleared.")
          }

          notificationSubscriptionRef.current = client.subscribe(`/topic/notifications/${username}`, (message) => {
            try {
              const notification = JSON.parse(message.body)
              notification.timestamp = new Date().toISOString()
              notification.id = `${Date.now()}-${Math.random()}`

              console.log("🔔 New notification received:", notification)
                
              onNotificationReceived(notification)
            } catch (e) {
              console.error("📛 Failed to parse notification message", e)
            }
          })

          console.log(`🔔 Subscribed to notifications for user: ${username}`)
        }

        // Sidebar subscriptions (existing functionality)
        if (chatRooms.length > 0 && onSidebarMessage) {
          sidebarSubscriptionsRef.current.forEach((subscription, roomId) => {
            subscription.unsubscribe()
            console.log(`🔁 Previous sidebar subscription for room ${roomId} cleared.`)
          })
          sidebarSubscriptionsRef.current.clear()

          chatRooms.forEach((room) => {
            const roomUniqueId = room.uniqueId
            if (roomUniqueId) {
              const subscription = client.subscribe(`/topic/chat/${roomUniqueId}`, (message) => {
                try {
                  const received = JSON.parse(message.body)

                  if (Number(currentRoomId) !== Number(roomUniqueId)) {
                    onSidebarMessage(roomUniqueId, received)
                    console.log(`📨 New message in room ${roomUniqueId}`)
                  }
                } catch (e) {
                  console.error("📛 Failed to parse sidebar message", e)
                }
              })

              sidebarSubscriptionsRef.current.set(roomUniqueId, subscription)
              console.log(`📡 Subscribed to sidebar room: ${roomUniqueId}`)
            }
          })
        }

        // Profile update subscription (existing functionality)
        if (onProfileUpdate) {
          if (profileSubscriptionRef.current) {
            profileSubscriptionRef.current.unsubscribe()
            console.log("🔁 Previous profile subscription cleared.")
          }

          profileSubscriptionRef.current = client.subscribe("/topic/profile-update", (message) => {
            try {
              const profileUpdate = JSON.parse(message.body)
              console.log("🔥 프로필 업데이트 수신:", profileUpdate)
              onProfileUpdate(profileUpdate)
            } catch (e) {
              console.error("📛 Failed to parse profile update message", e)
            }
          })

          console.log("👤 프로필 업데이트 구독 완료")
        }

        // Room deletion subscription (existing functionality)
        if (roomId && onRoomDeleted) {
          deleteSubscriptionRef.current = client.subscribe(`/topic/chat/${roomId}/deleted`, (message) => {
            try {
              const deleteData = JSON.parse(message.body)
              console.log("🗑️ Room deletion received:", deleteData)

              if (onRoomDeleted && typeof onRoomDeleted === "function") {
                onRoomDeleted(deleteData)
              }
            } catch (e) {
              console.error("📛 Failed to parse delete message", e)
            }
          })
        }

        // Keep alive (existing functionality)
        if (keepAliveIntervalRef.current) clearInterval(keepAliveIntervalRef.current)

        keepAliveIntervalRef.current = setInterval(() => {
          if (client && client.connected) {
            client.publish({
              destination: "/app/ping",
              body: "p",
            })
            console.log("📡 Sent keep-alive ping")
          }
        }, 15000)
      },

      onWebSocketClose: async () => {
        console.warn("🛑 WebSocket 끊김 → 토큰 갱신 시도")
        try {
          await safeRefreshToken()
        } catch (err) {
          console.error("❌ 토큰 갱신 실패 → 로그인 페이지로 이동")
          navigate("/login")
        }
      },

      onStompError: (frame) => {
        console.error("💥 STOMP error:", frame.headers["message"])
      },
    })

    client.activate()
    stompClientRef.current = client

    return () => {
      console.log("🧹 Cleaning up WebSocket...")

      if (keepAliveIntervalRef.current) {
        clearInterval(keepAliveIntervalRef.current)
        keepAliveIntervalRef.current = null
        console.log("🔕 Stopped keep-alive ping")
      }

      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe()
        subscriptionRef.current = null
        console.log("🔌 Chat subscription unsubscribed.")
      }

      if (notificationSubscriptionRef.current) {
        notificationSubscriptionRef.current.unsubscribe()
        notificationSubscriptionRef.current = null
        console.log("🔔 Notification subscription unsubscribed.")
      }

      if (profileSubscriptionRef.current) {
        profileSubscriptionRef.current.unsubscribe()
        profileSubscriptionRef.current = null
        console.log("👤 Profile subscription unsubscribed.")
      }

      if (deleteSubscriptionRef.current) {
        deleteSubscriptionRef.current.unsubscribe()
        deleteSubscriptionRef.current = null
        console.log("🗑️ Delete subscription unsubscribed.")
      }

      sidebarSubscriptionsRef.current.forEach((subscription) => {
        subscription.unsubscribe()
      })
      sidebarSubscriptionsRef.current.clear()

      if (client && client.active) {
        client.deactivate().then(() => {
          console.log("🛑 Disconnected from WebSocket")
        })
      }
    }
  }, [currentRoomId, navigate, onProfileUpdate, roomId, username, onNotificationReceived])

  return stompClientRef
}

export default useWebSocketNotifications
