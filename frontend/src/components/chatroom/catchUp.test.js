import { fetchMissedMessages, mergeMessages, maxMessageId, CATCH_UP_MAX_PAGES } from "./catchUp"

const msg = (id, content = `m${id}`, status = "NO_CHANGE") => ({ messageId: id, content, status, createdAt: "2026-10-28T10:00:00" })

describe("fetchMissedMessages", () => {
  it("남은 게 없을 때까지 마지막 id로 이어서 받는다", async () => {
    const pages = { 10: { messages: [msg(11), msg(12)], hasMore: true }, 12: { messages: [msg(13)], hasMore: false } }
    const calls = []
    const result = await fetchMissedMessages(async (after) => { calls.push(after); return pages[after] }, 10)

    expect(calls).toEqual([10, 12])
    expect(result.complete).toBe(true)
    expect(result.messages.map((m) => m.messageId)).toEqual([11, 12, 13])
  })

  it("놓친 게 없으면 한 번만 묻는다", async () => {
    const calls = []
    const result = await fetchMissedMessages(async (after) => { calls.push(after); return { messages: [], hasMore: false } }, 42)
    expect(calls).toEqual([42])
    expect(result).toEqual({ messages: [], complete: true })
  })

  it("너무 많이 놓쳤으면 정해진 횟수까지만 받고 complete=false", async () => {
    let next = 0
    const fetchAfter = jest.fn(async () => { next += 1; return { messages: [msg(next)], hasMore: true } })
    const result = await fetchMissedMessages(fetchAfter, 0)

    expect(fetchAfter).toHaveBeenCalledTimes(CATCH_UP_MAX_PAGES)
    expect(result.complete).toBe(false)
  })
})

describe("mergeMessages", () => {
  it("놓친 메시지를 더하고, 최신 페이지로 수정·삭제를 반영하며, id 순서로 정렬한다", () => {
    const prev = [msg(1, "원래"), msg(2, "지워질 메시지")]
    const missed = [msg(4), msg(3)]
    const latest = [msg(2, "", "DELETED"), msg(3), msg(4)]

    const merged = mergeMessages(prev, missed, latest)

    expect(merged.map((m) => m.messageId)).toEqual([1, 2, 3, 4])
    expect(merged.find((m) => m.messageId === 2).status).toBe("DELETED")
    expect(merged.find((m) => m.messageId === 1).content).toBe("원래")
  })
})

it("maxMessageId: 가장 큰 id, 비어 있으면 null", () => {
  expect(maxMessageId([msg(3), msg(9), msg(5)])).toBe(9)
  expect(maxMessageId([])).toBeNull()
})
