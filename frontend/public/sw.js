// DevChat 서비스 워커 — 휴대폰 알림(웹 푸시) 전용.
// fetch 핸들러는 두지 않는다: 화면을 캐시하지 않아 배포하면 바로 새 화면이 뜬다.

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

// 서버가 보낸 알림 { title, body, url, tag }
self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { body: event.data ? event.data.text() : '' }
  }

  const title = data.title || 'DevChat'
  event.waitUntil(
    self.registration.showNotification(title, {
      body: data.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/badge-96.png', // 안드로이드 상단 상태 표시줄의 작은 아이콘
      tag: data.tag || undefined,    // 같은 대화의 알림은 쌓지 않고 새것으로 바꾼다
      renotify: !!data.tag,
      data: { url: data.url || '/' },
    })
  )
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
