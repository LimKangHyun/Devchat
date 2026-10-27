import { createContext, useContext, useEffect, useRef, useState, useCallback } from "react"
import { Client, TickerStrategy } from "@stomp/stompjs"
import { safeRefreshToken } from "../api/refreshManager"
import { loginUrlFor } from "../../utils/postLoginRedirect"
import { useUser } from "../../context/UserContext"

const WebSocketContext = createContext(null)

// 토큰 갱신이 401이면 refresh 토큰까지 만료된 것. 네트워크 오류 등은 재시도한다
const isSessionExpired = (err) => err?.response?.status === 401

export const WebSocketProvider = ({ children }) => {
  const stompClientRef = useRef(null)
  const [connected, setConnected] = useState(false)

  // 로그인했던 사용자만 세션 만료 시 로그인 화면으로 보낸다 (비로그인 화면은 연결만 멈춤)
  const { currentUser } = useUser() || {}
  const loggedInRef = useRef(false)
  useEffect(() => { loggedInRef.current = !!currentUser }, [currentUser])

  const createClient = useCallback(() => {
    const client = new Client({
      webSocketFactory: () => new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL),
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 10000,
      // 백그라운드 탭에서 타이머가 늦춰져 heartbeat가 밀리지 않도록 Worker로 보낸다
      heartbeatStrategy: TickerStrategy.Worker,
      // 응답 없는 연결 시도에 오래 묶이지 않도록
      connectionTimeout: 10000,
      reconnectDelay: 3000,
      beforeConnect: async () => {
        try {
          await safeRefreshToken()
        } catch (e) {
          if (!isSessionExpired(e)) {
            console.warn("⚠️ 토큰 갱신 실패 - 재시도 예정", e?.message)
            return
          }
          // 계속 재시도해도 핸드셰이크가 거절되므로 재연결을 멈춘다
          console.warn("⚠️ 세션 만료 - 재연결 중단")
          client.deactivate()
          if (loggedInRef.current) {
            window.location.replace(loginUrlFor(window.location.pathname + window.location.search))
          }
        }
      },
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
    return client
  }, [])

  useEffect(() => {
    const client = createClient()
    client.activate()
    stompClientRef.current = client
    window.__stompClient = client

    return () => {
      setConnected(false)
      if (client.active) client.deactivate()
    }
  }, [createClient])

  const reconnect = useCallback(async () => {
    const old = stompClientRef.current
    if (old?.active) await old.deactivate()

    const client = createClient()
    client.activate()
    stompClientRef.current = client
    window.__stompClient = client
  }, [createClient])

  // 탭으로 돌아오거나 네트워크가 복구됐는데 끊겨 있으면 재연결 대기 없이 바로 다시 연결한다
  useEffect(() => {
    const reconnectIfDropped = () => {
      if (document.visibilityState !== "visible") return
      const client = stompClientRef.current
      if (client?.active && !client.connected) reconnect()
    }
    document.addEventListener("visibilitychange", reconnectIfDropped)
    window.addEventListener("online", reconnectIfDropped)
    return () => {
      document.removeEventListener("visibilitychange", reconnectIfDropped)
      window.removeEventListener("online", reconnectIfDropped)
    }
  }, [reconnect])

  return (
    <WebSocketContext.Provider value={{ stompClientRef, connected, reconnect }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export const useWebSocketContext = () => useContext(WebSocketContext)
