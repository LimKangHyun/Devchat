import { createContext, useContext, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"
import { useNavigate } from "react-router-dom"

const WebSocketContext = createContext(null)

export const WebSocketProvider = ({ children }) => {
  const stompClientRef = useRef(null)
  const retryCountRef = useRef(0)
  const [connected, setConnected] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => {
        console.log("🔌 webSocketFactory 호출")
        return new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL)
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
      },
      onWebSocketClose: (event) => {
        console.warn("🛑 WebSocket 끊김", "code:", event.code, "reason:", event.reason, "wasClean:", event.wasClean)
        setConnected(false)
      },
      onDisconnect: () => {
        console.warn("🛑 STOMP Disconnect")
      },
      onStompError: (frame) => {
        console.error("💥 STOMP error:", frame.headers["message"])
      },
    })

    console.log("🚀 client.activate() 호출")
    client.activate()
    stompClientRef.current = client

    return () => {
      console.log("🧹 cleanup - deactivate")
      setConnected(false)
      if (client.active) client.deactivate()
    }
  }, [])

  return (
    <WebSocketContext.Provider value={{ stompClientRef, connected }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export const useWebSocketContext = () => useContext(WebSocketContext)