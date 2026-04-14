import { createContext, useContext, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"
import { useNavigate } from "react-router-dom"
import { safeRefreshToken } from "../api/refreshManager"

const WebSocketContext = createContext(null)

export const WebSocketProvider = ({ children }) => {
  const stompClientRef = useRef(null)
  const [connected, setConnected] = useState(false)
  const retryCountRef = useRef(0)
  const navigate = useNavigate()

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL),
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 10000,
      reconnectDelay: () => {
        const delay = Math.min(1000 * Math.pow(2, retryCountRef.current), 30000)
        retryCountRef.current += 1
        console.warn(`⏳ ${delay}ms 후 재연결 시도 (${retryCountRef.current}번째)`)
        return delay
      },

      onConnect: async () => {
        try {
          await safeRefreshToken()
        } catch (err) {
          console.error("❌ 토큰 갱신 실패 → 로그인 페이지로 이동")
          navigate("/login")
          client.deactivate()
          return
        }

        console.log("✅ Connected to WebSocket")
        retryCountRef.current = 0
        setConnected(true)
      },

      onDisconnect: () => {
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

  return (
    <WebSocketContext.Provider value={{ stompClientRef, connected }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export const useWebSocketContext = () => useContext(WebSocketContext)