import { renderHook, act } from "@testing-library/react"
import { AlarmProvider, useAlarm, ROOMS_CACHE_KEY, readRoomsCache } from "./AlarmContext"

const ROOM_A = { uniqueId: 1, roomName: "A의 방", inviteCode: "aaa" }
const ROOM_B = { uniqueId: 2, roomName: "B의 방", inviteCode: "bbb" }

const renderAlarm = () => renderHook(() => useAlarm(), { wrapper: AlarmProvider })

describe("방 목록 캐시의 계정 구분", () => {
  beforeEach(() => localStorage.clear())

  it("다른 계정이 남긴 캐시는 비우고 믿지 않는다", () => {
    localStorage.setItem(ROOMS_CACHE_KEY, JSON.stringify({ owner: "alice", rooms: [ROOM_A] }))
    const { result } = renderAlarm()

    expect(result.current.isRoomsTrusted("bob")).toBe(false)
    act(() => result.current.bindRoomsToUser("bob"))

    expect(result.current.alarmRooms).toEqual([])
  })

  it("내 계정의 캐시는 서버 응답 전에도 바로 쓴다", () => {
    localStorage.setItem(ROOMS_CACHE_KEY, JSON.stringify({ owner: "alice", rooms: [ROOM_A] }))
    const { result } = renderAlarm()

    act(() => result.current.bindRoomsToUser("alice"))

    expect(result.current.isRoomsTrusted("alice")).toBe(true)
    expect(result.current.alarmRooms).toHaveLength(1)
  })

  it("예전 형식(주인 없는 배열) 캐시는 서버 목록이 올 때까지 믿지 않는다", () => {
    localStorage.setItem(ROOMS_CACHE_KEY, JSON.stringify([ROOM_A]))
    const { result } = renderAlarm()

    expect(result.current.isRoomsTrusted("alice")).toBe(false)
    act(() => result.current.updateRooms([ROOM_B]))
    expect(result.current.isRoomsTrusted("alice")).toBe(true)
  })

  it("서버 목록이 먼저 오고 계정을 나중에 알아도 목록을 지우지 않고 주인을 기록한다", () => {
    localStorage.setItem(ROOMS_CACHE_KEY, JSON.stringify({ owner: "alice", rooms: [ROOM_A] }))
    const { result } = renderAlarm()

    act(() => result.current.updateRooms([ROOM_B])) // bob 쿠키로 받은 목록
    act(() => result.current.bindRoomsToUser("bob"))

    expect(result.current.alarmRooms.map((r) => r.uniqueId)).toEqual([2])
    expect(readRoomsCache()).toEqual({ owner: "bob", rooms: [ROOM_B] })
  })
})
