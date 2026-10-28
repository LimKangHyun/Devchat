import { renderHook, act } from "@testing-library/react"
import useOutbox, { SEND_TIMEOUT_MS } from "./useOutbox"

const setup = (connected = true) => {
  const sent = []
  let isUp = connected
  const hook = renderHook(() => useOutbox({
    publish: (body, id) => sent.push({ body, id }),
    isConnected: () => isUp,
  }))
  return { hook, sent, setConnected: (v) => { isUp = v } }
}
const items = (hook) => hook.result.current.items

beforeEach(() => jest.useFakeTimers())
afterEach(() => jest.useRealTimers())

it("연결돼 있으면 바로 보내고, 서버가 돌려주면 목록에서 지운다", () => {
  const { hook, sent } = setup()
  let id
  act(() => { id = hook.result.current.enqueue({ content: "안녕" }) })

  expect(sent).toEqual([{ body: { content: "안녕" }, id }])
  expect(items(hook)).toMatchObject([{ clientMessageId: id, status: "sending", waiting: false }])

  act(() => hook.result.current.resolve(id))
  expect(items(hook)).toEqual([])
})

it("응답이 없으면 10초 뒤 실패로 바꾸고, 다시 보내면 같은 id로 보낸다", () => {
  const { hook, sent } = setup()
  let id
  act(() => { id = hook.result.current.enqueue({ content: "안녕" }) })
  act(() => jest.advanceTimersByTime(SEND_TIMEOUT_MS))
  expect(items(hook)[0]).toMatchObject({ status: "failed", error: "서버 응답이 없어요." })

  act(() => hook.result.current.retry(id))
  expect(sent.map((s) => s.id)).toEqual([id, id])
  expect(items(hook)[0].status).toBe("sending")
})

it("서버 오류(예: 전송 횟수 초과)를 받으면 그 메시지만 실패로 표시한다", () => {
  const { hook } = setup()
  let a, b
  act(() => { a = hook.result.current.enqueue({ content: "a" }); b = hook.result.current.enqueue({ content: "b" }) })
  act(() => hook.result.current.fail(a, "메시지 전송 횟수를 초과했어요."))

  expect(items(hook).find((it) => it.clientMessageId === a)).toMatchObject({ status: "failed" })
  expect(items(hook).find((it) => it.clientMessageId === b)).toMatchObject({ status: "sending" })
})

it("끊겨 있을 때 보낸 메시지는 기다렸다가 다시 연결되면 보낸다", () => {
  const { hook, sent, setConnected } = setup(false)
  let id
  act(() => { id = hook.result.current.enqueue({ content: "오프라인" }) })
  expect(sent).toEqual([])
  expect(items(hook)[0]).toMatchObject({ status: "sending", waiting: true })

  act(() => jest.advanceTimersByTime(60000))
  expect(items(hook)[0].status).toBe("sending") // 연결 대기 중에는 실패로 바꾸지 않음

  setConnected(true)
  act(() => hook.result.current.flush())
  expect(sent.map((s) => s.id)).toEqual([id])
})

it("보낸 직후 연결이 끊기면 실패로 치지 않고, 다시 연결되면 같은 id로 다시 보낸다", () => {
  const { hook, sent, setConnected } = setup()
  let id
  act(() => { id = hook.result.current.enqueue({ content: "끊기기 직전" }) })

  setConnected(false)
  act(() => hook.result.current.pause())
  act(() => jest.advanceTimersByTime(SEND_TIMEOUT_MS * 3))
  expect(items(hook)[0].status).toBe("sending")

  setConnected(true)
  act(() => hook.result.current.flush())
  expect(sent.map((s) => s.id)).toEqual([id, id])
})

it("삭제하면 목록에서 빠지고 늦게 온 실패 처리도 무시한다", () => {
  const { hook } = setup()
  let id
  act(() => { id = hook.result.current.enqueue({ content: "x" }) })
  act(() => hook.result.current.discard(id))
  act(() => jest.advanceTimersByTime(SEND_TIMEOUT_MS))
  expect(items(hook)).toEqual([])
})
