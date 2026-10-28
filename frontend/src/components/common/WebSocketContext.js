import { createContext, useContext, useEffect, useRef, useState, useCallback } from "react"
import { Client, TickerStrategy } from "@stomp/stompjs"
import { safeRefreshToken } from "../api/refreshManager"
import { loginUrlFor } from "../../utils/postLoginRedirect"
import { useUser } from "../../context/UserContext"
import { reconnectDelay } from "./reconnectBackoff"

const WebSocketContext = createContext(null)

// 토큰 갱신이 401이면 refresh 토큰까지 만료된 것. 네트워크 오류 등은 재시도한다
const isSessionExpired = (err) => err?.response?.status === 401

/**
 * 앱 전체가 쓰는 웹소켓(STOMP) 연결 하나.
 *
 * 재연결은 stompjs의 자동 재연결 대신 직접 관리한다. stompjs 7.1의 지수 백오프는
 * 연결에 성공해도 대기 시간을 되돌리지 않아, 한 번 길게 끊긴 뒤로는 잠깐 끊겨도
 * 최대 대기 시간을 기다리게 된다. 여기서는 끊길 때마다 새 Client를 만들고,
 * 연결에 성공하면 대기 시간을 처음으로 되돌린다.
 */
export const WebSocketProvider = ({ children }) => {
  const stompClientRef = useRef(null)
  const [connected, setConnected] = useState(false)

  const attemptRef = useRef(0)        // 연속 실패 횟수
  const retryTimerRef = useRef(null)  // 예약된 재연결
  const stoppedRef = useRef(false)    // 세션 만료·화면 종료로 재연결을 멈춘 상태
  const reconnectRef = useRef(null)

  // 로그인했던 사용자만 세션 만료 시 로그인 화면으로 보낸다 (비로그인 화면은 연결만 멈춤)
  const { currentUser } = useUser() || {}
  const loggedInRef = useRef(false)
  useEffect(() => { loggedInRef.current = !!currentUser }, [currentUser])

  const clearRetry = () => {
    if (retryTimerRef.current) clearTimeout(retryTimerRef.current)
    retryTimerRef.current = null
  }

  const scheduleReconnect = useCallback(() => {
    if (stoppedRef.current || retryTimerRef.current) return
    const delay = reconnectDelay(attemptRef.current)
    attemptRef.current += 1
    console.warn(`🔁 ${Math.round(delay / 100) / 10}초 뒤 재연결 (${attemptRef.current}번째)`)
    retryTimerRef.current = setTimeout(() => {
      retryTimerRef.current = null
      reconnectRef.current?.()
    }, delay)
  }, [])

  const createClient = useCallback(() => {
    // 이 Client가 지금 쓰는 연결일 때만 상태를 바꾼다 (교체된 이전 연결의 늦은 이벤트 무시)
    const isCurrent = () => stompClientRef.current === client

    const client = new Client({
      webSocketFactory: () => new WebSocket(process.env.REACT_APP_WEB_SOCKET_URL),
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 10000,
      // 백그라운드 탭에서 타이머가 늦춰져 heartbeat가 밀리지 않도록 Worker로 보낸다
      heartbeatStrategy: TickerStrategy.Worker,
      // 응답 없는 연결 시도에 오래 묶이지 않도록
      connectionTimeout: 10000,
      reconnectDelay: 0, // 재연결은 scheduleReconnect가 맡는다
      beforeConnect: async () => {
        try {
          await safeRefreshToken()
        } catch (e) {
          if (!isSessionExpired(e)) {
            console.warn("⚠️ 토큰 갱신 실패 - 그대로 연결 시도", e?.message)
            return
          }
          // 계속 재시도해도 핸드셰이크가 거절되므로 재연결을 멈춘다
          console.warn("⚠️ 세션 만료 - 재연결 중단")
          stoppedRef.current = true
          clearRetry()
          client.deactivate()
          if (loggedInRef.current) {
            window.location.replace(loginUrlFor(window.location.pathname + window.location.search))
          }
        }
      },
      onConnect: () => {
        if (!isCurrent()) return
        console.log("✅ WebSocket Connected")
        attemptRef.current = 0
        clearRetry()
        setConnected(true)
      },
      onWebSocketClose: (event) => {
        if (!isCurrent()) return
        console.warn("🛑 WebSocket 끊김", event.code, event.reason)
        setConnected(false)
        scheduleReconnect()
      },
      onDisconnect: () => {
        if (!isCurrent()) return
        setConnected(false)
      },
      onStompError: (frame) => {
        console.error("💥 STOMP error:", frame.headers["message"])
      },
    })
    return client
  }, [scheduleReconnect])

  /** 지금 연결을 버리고 바로 새로 연결한다 (예약된 재연결이 있으면 앞당긴다) */
  const inFlightRef = useRef(null)
  const reconnect = useCallback(() => {
    if (stoppedRef.current) return Promise.resolve()
    // 이전 연결을 닫는 중에 또 불리면 같은 작업을 기다린다 (연결이 두 개 생기지 않게)
    if (inFlightRef.current) return inFlightRef.current

    inFlightRef.current = (async () => {
      clearRetry()
      const old = stompClientRef.current
      stompClientRef.current = null // 이전 연결의 끊김 이벤트가 재연결을 또 예약하지 않게
      setConnected(false)
      if (old?.active) await old.deactivate()
      if (stoppedRef.current) return

      const client = createClient()
      stompClientRef.current = client
      window.__stompClient = client
      client.activate()
    })().finally(() => { inFlightRef.current = null })
    return inFlightRef.current
  }, [createClient])
  reconnectRef.current = reconnect

  useEffect(() => {
    stoppedRef.current = false
    reconnect()
    return () => {
      stoppedRef.current = true
      clearRetry()
      const client = stompClientRef.current
      stompClientRef.current = null
      setConnected(false)
      if (client?.active) client.deactivate()
    }
  }, [reconnect])

  // 탭으로 돌아오거나 네트워크가 복구됐는데 끊겨 있으면 대기 없이 바로 다시 연결한다
  useEffect(() => {
    const reconnectIfDropped = () => {
      if (document.visibilityState !== "visible") return
      const client = stompClientRef.current
      if (client?.connected) return
      // 재연결을 기다리는 중이거나 연결이 없을 때만. 연결을 시도하는 중이면 그대로 둔다
      if (retryTimerRef.current || !client) reconnect()
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
