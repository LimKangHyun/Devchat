import { createContext, useContext, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"
import { useNavigate } from "react-router-dom"
import { safeRefreshToken } from "../api/refreshManager"

const WebSocketContext = createContext(null)

export const WebSocketProvider = ({ children }) => {
  const stompClientRef = useRef(null)
  const [connected, setConnected] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL),
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 10000,
      withCredentials: true,

      onConnect: () => {
        console.log("✅ Connected to WebSocket")
        setConnected(true)
      },

      onWebSocketClose: async () => {
        console.warn("🛑 WebSocket 끊김 → 재연결 시도")
        setConnected(false)
        client.deactivate()

        let retryCount = 0
        const maxRetry = 3

        const reconnect = async () => {
          try {
            console.log(`🔄 재연결 시도 (${retryCount + 1}/${maxRetry})`)
            await safeRefreshToken()
            client.activate()
          } catch (err) {
            if (retryCount < maxRetry) {
              retryCount++
              const delay = 1000 * retryCount // 1초, 2초, 3초
              console.warn(`⏳ ${delay}ms 후 재시도`)
              setTimeout(reconnect, delay)
            } else {
              console.error("❌ 재연결 최종 실패 → 로그인 페이지로 이동")
              navigate("/login")
            }
          }
        }

        reconnect()
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

  return (
    <WebSocketContext.Provider value={{ stompClientRef, connected }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export const useWebSocketContext = () => useContext(WebSocketContext)