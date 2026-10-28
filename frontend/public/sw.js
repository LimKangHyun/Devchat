// DevChat 서비스 워커 — 휴대폰 알림(웹 푸시) 전용.
// fetch 핸들러는 두지 않는다: 화면을 캐시하지 않아 배포하면 바로 새 화면이 뜬다.

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

// 다른 기기에서 이미 본 방의 알림을 지운다. seen: [{ roomId, at }]
// 본 시각보다 먼저 뜬 그 방 알림만 지운다 (앱 쪽 utils/showNotification.js와 같은 규칙)
async function closeSeen(seen) {
  if (!Array.isArray(seen) || !seen.length) return
  const seenAt = new Map(seen.map((s) => [String(s.roomId), s.at]))
  const shown = await self.registration.getNotifications()
  shown.forEach((n) => {
    const m = /^(?:room|MENTION)-(\d+)$/.exec(n.tag || '')
    if (!m || !seenAt.has(m[1])) return
    if (!n.timestamp || n.timestamp <= seenAt.get(m[1])) n.close()
  })
}

// 서버가 보낸 알림 { title, body, url, tag, seen }
// 웹 푸시는 받을 때마다 알림을 꼭 띄워야 해서(아이폰은 어기면 구독이 끊긴다) 정리만 하는 푸시는 없다.
// 대신 새 알림이 올 때 함께 온 seen으로 이미 본 방의 알림을 먼저 지운다.
self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { body: event.data ? event.data.text() : '' }
  }

  const title = data.title || 'DevChat'
  event.waitUntil((async () => {
    try { await closeSeen(data.seen) } catch { /* 정리에 실패해도 새 알림은 띄운다 */ }
    // 앱 아이콘 숫자 배지 = 안 읽은 채팅 수 (서버가 계산해 보낸다)
    try {
      if (typeof data.badge === 'number' && self.navigator.setAppBadge) {
        if (data.badge > 0) await self.navigator.setAppBadge(data.badge)
        else await self.navigator.clearAppBadge()
      }
    } catch { /* 지원하지 않는 기기는 무시 */ }
    await self.registration.showNotification(title, {
      body: data.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/badge-96.png', // 안드로이드 상단 상태 표시줄의 작은 아이콘
      tag: data.tag || undefined,    // 같은 대화의 알림은 쌓지 않고 새것으로 바꾼다
      renotify: !!data.tag,
      data: { url: data.url || '/' },
    })
  })())
})

// 알림을 누르면 열려 있는 DevChat 창으로 가서 해당 화면을 연다. 창이 없으면 새로 연다
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const target = new URL(event.notification.data?.url || '/', self.location.origin).href

  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const client of windows) {
      if (new URL(client.url).origin !== self.location.origin) continue
      await client.focus()
      if ('navigate' in client) {
        try { await client.navigate(target) } catch { /* 제어하지 않는 창이면 포커스만 */ }
      }
      return
    }
    await self.clients.openWindow(target)
  })())
})
