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
        console.warn("🛑 WebSocket 끊김 → 토큰 갱신 시도")
        client.deactivate()
        try {
          await safeRefreshToken()
          client.activate()
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