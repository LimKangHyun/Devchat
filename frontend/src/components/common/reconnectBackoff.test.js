import { reconnectDelay, RECONNECT_MAX_MS } from "./reconnectBackoff"

describe("reconnectDelay", () => {
  it("실패할수록 두 배씩 늘어난다 (무작위 최대값 기준)", () => {
    const max = () => 1
    expect([0, 1, 2, 3, 4].map((n) => reconnectDelay(n, max))).toEqual([1000, 2000, 4000, 8000, 16000])
  })

  it("30초를 넘지 않는다", () => {
    expect(reconnectDelay(10, () => 1)).toBe(RECONNECT_MAX_MS)
    expect(reconnectDelay(50, () => 1)).toBe(RECONNECT_MAX_MS)
  })

  it("한꺼번에 몰리지 않도록 절반~전체 사이에서 고른다", () => {
    expect(reconnectDelay(3, () => 0)).toBe(4000)
    expect(reconnectDelay(3, () => 0.5)).toBe(6000)
    expect(reconnectDelay(3, () => 1)).toBe(8000)
  })
})
