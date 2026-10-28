import { closeSeenNotifications } from "./showNotification"

jest.mock("./pushNotifications", () => ({ isPushOnHere: () => false }))

const notice = (tag, timestamp) => ({ tag, timestamp, close: jest.fn() })

const withNotifications = (list) => {
  Object.defineProperty(navigator, "serviceWorker", {
    configurable: true,
    value: { getRegistration: async () => ({ getNotifications: async () => list }) },
  })
}

it("본 시각보다 먼저 뜬 그 방 알림(메시지·멘션)만 지우고, 다른 방과 본 뒤에 온 알림은 남긴다", async () => {
  const old = notice("room-5", 1000)
  const oldMention = notice("MENTION-5", 1500)
  const afterSeen = notice("room-5", 3000)
  const otherRoom = notice("room-6", 1000)
  const friend = notice("FRIEND_REQUESTED-2", 1000)
  withNotifications([old, oldMention, afterSeen, otherRoom, friend])

  await closeSeenNotifications([{ roomId: 5, at: 2000 }])

  expect(old.close).toHaveBeenCalled()
  expect(oldMention.close).toHaveBeenCalled()
  expect(afterSeen.close).not.toHaveBeenCalled()
  expect(otherRoom.close).not.toHaveBeenCalled()
  expect(friend.close).not.toHaveBeenCalled()
})

it("본 방 목록이 비어 있으면 아무것도 하지 않는다", async () => {
  const n = notice("room-5", 1000)
  withNotifications([n])
  await closeSeenNotifications([])
  expect(n.close).not.toHaveBeenCalled()
})
