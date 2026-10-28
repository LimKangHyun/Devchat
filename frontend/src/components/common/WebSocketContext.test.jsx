import { render, act } from "@testing-library/react"
import { WebSocketProvider, useWebSocketContext } from "./WebSocketContext"

// stompjs Client 대신 이벤트를 직접 일으킬 수 있는 가짜.
// CRA는 테스트마다 jest.fn 구현을 초기화(resetMocks)하므로 일반 함수로 만든다
const mockClients = []
const clients = mockClients
jest.mock("@stomp/stompjs", () => ({
  TickerStrategy: { Worker: "worker" },
  Client: function MockClient(config) {
    const c = {
      config,
      active: false,
      connected: false,
      activate: () => { c.active = true },
      deactivate: async () => { c.active = false; c.connected = false },
      // 테스트용
      open: async () => { await config.beforeConnect?.(); c.connected = true; config.onConnect?.() },
      drop: () => { c.connected = false; config.onWebSocketClose?.({ code: 1006, reason: "" }) },
    }
    mockClients.push(c)
    return c
  },
}))

let mockRefreshResult = () => Promise.resolve({})
jest.mock("../api/refreshManager", () => ({ safeRefreshToken: () => mockRefreshResult() }))
jest.mock("../../context/UserContext", () => ({ useUser: () => ({ currentUser: { username: "alice" } }) }))

let ctx
const Probe = () => { ctx = useWebSocketContext(); return null }
const latest = () => clients[clients.length - 1]
const flush = () => act(async () => { await Promise.resolve(); await Promise.resolve() })

beforeEach(() => {
  clients.length = 0
  jest.useFakeTimers()
  jest.spyOn(Math, "random").mockReturnValue(1) // 대기 시간을 최대값으로 고정
  jest.spyOn(console, "warn").mockImplementation(() => {})
  jest.spyOn(console, "log").mockImplementation(() => {})
  mockRefreshResult = () => Promise.resolve({})
})
afterEach(() => { jest.useRealTimers(); jest.restoreAllMocks() })

const mount = async () => {
  render(<WebSocketProvider><Probe /></WebSocketProvider>)
  await flush()
}

it("끊기면 1초, 2초, 4초… 간격으로 다시 연결한다", async () => {
  await mount()
  expect(clients).toHaveLength(1)

  for (const [i, wait] of [[1, 1000], [2, 2000], [3, 4000]]) {
    act(() => latest().drop())
    act(() => jest.advanceTimersByTime(wait - 1))
    await flush()
    expect(clients).toHaveLength(i) // 아직 대기 중
    act(() => jest.advanceTimersByTime(1))
    await flush()
    expect(clients).toHaveLength(i + 1)
  }
})

it("연결에 성공하면 대기 시간을 처음(1초)으로 되돌린다", async () => {
  await mount()
  for (let i = 0; i < 3; i++) { // 1s, 2s, 4s 실패
    act(() => latest().drop())
    act(() => jest.advanceTimersByTime(30000))
    await flush()
  }
  await act(async () => { await latest().open() })
  expect(ctx.connected).toBe(true)

  const before = clients.length
  act(() => latest().drop())
  act(() => jest.advanceTimersByTime(1000))
  await flush()
  expect(clients).toHaveLength(before + 1)
})

it("교체된 이전 연결의 늦은 이벤트는 상태를 바꾸지 않는다", async () => {
  await mount()
  const old = latest()
  await act(async () => { await ctx.reconnect() })
  await act(async () => { await latest().open() })
  expect(ctx.connected).toBe(true)

  act(() => old.config.onWebSocketClose({ code: 1006 }))
  expect(ctx.connected).toBe(true)
  act(() => jest.advanceTimersByTime(60000))
  await flush()
  expect(clients).toHaveLength(2) // 재연결을 새로 예약하지 않음
})

it("세션이 만료되면(토큰 갱신 401) 더 이상 재연결하지 않는다", async () => {
  delete window.location
  window.location = { pathname: "/chat/x", search: "", replace: jest.fn() }
  await mount()
  mockRefreshResult = () => Promise.reject({ response: { status: 401 } })

  await act(async () => { await latest().config.beforeConnect() })
  act(() => latest().drop())
  act(() => jest.advanceTimersByTime(60000))
  await flush()

  expect(clients).toHaveLength(1)
  expect(window.location.replace).toHaveBeenCalled()
})
