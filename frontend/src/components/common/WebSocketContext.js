import { createContext, useContext, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"

const WebSocketContext = createContext(null)

export const WebSocketProvider = ({ children }) => {
  const stompClientRef = useRef(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL),
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log("✅ WebSocket Connected")
        setConnected(true)
      },
      onWebSocketClose: (event) => {
        console.warn("🛑 WebSocket 끊김", event.code, event.reason)
        setConnected(false)
      },
      onDisconnect: () => {
        console.warn("🛑 STOMP Disconnect")
        setConnected(false)
      },
      onStompError: (frame) => {
        console.error("💥 STOMP error:", frame.headers["message"])
      },
    })

    client.activate()
    stompClientRef.current = client
    window.__stompClient = client

    return () => {
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