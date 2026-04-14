import { createContext, useContext, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"
import { useNavigate } from "react-router-dom"
import { safeRefreshToken } from "../api/refreshManager"

const WebSocketContext = createContext(null)

export const WebSocketProvider = ({ children }) => {
  const stompClientRef = useRef(null)
  const subscriptionsRef = useRef([])
  const retryCountRef = useRef(0)
  const [connected, setConnected] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    const client = new Client({
      webSocketFactory: async () => {
        try {
          await safeRefreshToken()
          return new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL)
        } catch (err) {
          console.error("❌ 토큰 갱신 실패 → 로그인 이동")
          navigate("/login")
          client.configure({ reconnectDelay: 0 }) // 재연결 중단
          throw err
        }
      },
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 10000,
      reconnectDelay: () => {
        const delay = Math.min(1000 * 2 ** retryCountRef.current, 30000)
        retryCountRef.current += 1
        console.warn(`⏳ ${delay}ms 후 재연결 (${retryCountRef.current})`)
        return delay
      },
      onConnect: () => {
        console.log("✅ Connected")
        retryCountRef.current = 0
        setConnected(true)

        // 기존 구독 정리 후 재구독
        subscriptionsRef.current.forEach((sub) => {
          sub.subscription?.unsubscribe()
        })
        subscriptionsRef.current = subscriptionsRef.current.map(({ destination, callback }) => {
          const newSub = client.subscribe(destination, callback)
          return { destination, callback, subscription: newSub }
        })
      },
      onWebSocketClose: () => {
        console.warn("🛑 WebSocket 끊김")
        setConnected(false)
      },
      onStompError: (frame) => {
        console.error("💥 STOMP error:", frame.headers["message"])
      },
    })

    client.activate()
    stompClientRef.current = client

    return () => {
      setConnected(false)
      if (client.active) client.deactivate()
    }
  }, [navigate])

  const subscribe = (destination, callback) => {
    const client = stompClientRef.current

    const exists = subscriptionsRef.current.find(
      (sub) => sub.destination === destination
    )
    if (!exists) {
      subscriptionsRef.current.push({ destination, callback, subscription: null })
    }

    if (client && client.connected) {
      const subscription = client.subscribe(destination, callback)
      const target = subscriptionsRef.current.find(
        (sub) => sub.destination === destination
      )
      if (target) target.subscription = subscription
      return subscription
    }
  }

  const unsubscribe = (destination) => {
    const target = subscriptionsRef.current.find(
      (sub) => sub.destination === destination
    )
    if (target) {
      target.subscription?.unsubscribe()
    }
    subscriptionsRef.current = subscriptionsRef.current.filter(
      (sub) => sub.destination !== destination
    )
  }

  return (
    <WebSocketContext.Provider
      value={{
        stompClientRef,
        connected,
        subscribe,
        unsubscribe,
      }}
    >
      {children}
    </WebSocketContext.Provider>
  )
}

export const useWebSocketContext = () => useContext(WebSocketContext)