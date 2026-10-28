import { render, act } from "@testing-library/react"
import { useEffect } from "react"
import { AlarmProvider, useAlarm, ROOMS_CACHE_KEY } from "./AlarmContext"
import { ChatSubscriptionProvider } from "./ChatSubscriptionProvider"

// 운영과 같은 조건: 웹소켓 연결됨, 로그인 사용자 alice
const mockSubscribed = []
const mockClient = {
  connected: true,
  subscribe: (dest) => { mockSubscribed.push(dest); return { unsubscribe: () => {} } },
}
jest.mock("../components/common/WebSocketContext", () => ({
  useWebSocketContext: () => ({ stompClientRef: { current: mockClient }, connected: true }),
}))
let mockUser = null
jest.mock("./UserContext", () => ({ useUser: () => ({ currentUser: mockUser }) }))

const ROOMS = [{ uniqueId: 1, roomName: "A", inviteCode: "a" }, { uniqueId: 2, roomName: "B", inviteCode: "b" }]

// 사이드바처럼 서버에서 방 목록을 받아 updateRooms를 부른다
const FakeSidebar = ({ loadAfterMs }) => {
  const { updateRooms } = useAlarm()
  useEffect(() => { const t = setTimeout(() => updateRooms(ROOMS), loadAfterMs); return () => clearTimeout(t) }, [updateRooms, loadAfterMs])
  return null
}

const mount = (loadAfterMs) => render(
  <AlarmProvider>
    <ChatSubscriptionProvider>
      <FakeSidebar loadAfterMs={loadAfterMs} />
    </ChatSubscriptionProvider>
  </AlarmProvider>
)

beforeEach(() => { mockSubscribed.length = 0; localStorage.clear(); jest.useFakeTimers() })
afterEach(() => jest.useRealTimers())

const chatTopics = () => [...new Set(mockSubscribed.filter((d) => d.startsWith("/topic/chat/")))].sort()

it.each([
  ["사용자 정보가 먼저 오고 방 목록이 나중에", true],
  ["방 목록이 먼저 오고 사용자 정보가 나중에", false],
])("예전 형식 캐시가 있을 때 (%s) 모든 방을 구독한다", (_, userFirst) => {
  localStorage.setItem(ROOMS_CACHE_KEY, JSON.stringify(ROOMS)) // 운영 사용자 브라우저의 예전 캐시
  mockUser = userFirst ? { username: "alice" } : null
  const view = mount(50)

  if (!userFirst) {
    act(() => jest.advanceTimersByTime(60)) // 방 목록 도착
    mockUser = { username: "alice" }
    view.rerender(
      <AlarmProvider><ChatSubscriptionProvider><FakeSidebar loadAfterMs={50} /></ChatSubscriptionProvider></AlarmProvider>
    )
  }
  act(() => jest.advanceTimersByTime(60))

  expect(chatTopics()).toEqual(["/topic/chat/1", "/topic/chat/2"])
})
