// 앱 안에서 OS 알림을 띄운다.
// 휴대폰(안드로이드·아이폰 설치 앱)은 new Notification()을 막으므로 서비스 워커로 띄운다.
// 알림을 누르면 서비스 워커(sw.js)가 data.url 화면을 연다.
import { isPushOnHere } from "./pushNotifications"

/** 이 기기 알림 센터에 떠 있는 알림 중 tag가 맞는 것을 닫는다 (방을 열어 이미 본 알림 정리) */
export async function closeNotifications(tags) {
  try {
    const reg = await navigator.serviceWorker?.getRegistration?.()
    if (!reg?.getNotifications) return
    const shown = await reg.getNotifications()
    shown.filter((n) => tags.includes(n.tag)).forEach((n) => n.close())
  } catch { /* 지원하지 않는 브라우저는 그대로 둔다 */ }
}

/**
 * 다른 기기에서 이미 본 방의 알림을 이 기기 알림 센터에서 지운다.
 * seen: [{ roomId, at }] — at(본 시각)보다 먼저 뜬 그 방 알림만 지운다 (본 뒤에 온 새 알림은 남긴다)
 * 같은 규칙을 서비스 워커(sw.js)도 쓴다.
 */
export async function closeSeenNotifications(seen) {
  if (!seen?.length) return
  try {
    const reg = await navigator.serviceWorker?.getRegistration?.()
    if (!reg?.getNotifications) return
    const seenAt = new Map(seen.map((s) => [String(s.roomId), s.at]))
    const shown = await reg.getNotifications()
    shown.forEach((n) => {
      const m = /^(?:room|MENTION)-(\d+)$/.exec(n.tag || "")
      if (!m || !seenAt.has(m[1])) return
      if (!n.timestamp || n.timestamp <= seenAt.get(m[1])) n.close()
    })
  } catch { /* 지원하지 않는 브라우저는 그대로 둔다 */ }
}

export async function showAppNotification({ title, body, icon, tag, url }) {
  if (typeof window === "undefined" || !("Notification" in window)) return false
  if (Notification.permission !== "granted") return false

  // 화면을 보고 있지 않고 이 기기에서 푸시를 켜 두었으면, 서버가 같은 알림을 푸시로 보낸다
  if (document.visibilityState !== "visible" && isPushOnHere()) return false

  try {
    const reg = await navigator.serviceWorker?.getRegistration?.()
    if (reg) {
      await reg.showNotification(title, {
        body,
        icon,
        badge: "/icons/badge-96.png",
        tag,                       // 서버 푸시와 같은 tag라 같은 대화의 알림은 하나로 합쳐진다
        data: { url: url || "/" },
      })
      return true
    }
  } catch (e) {
    console.warn("서비스 워커 알림 실패, 기본 방식으로 시도", e)
  }

  try { // 서비스 워커가 없는 환경(PC 일부)
    const n = new Notification(title, { body, icon, tag })
    if (url) {
      n.onclick = () => {
        try { window.focus() } catch {}
        // 앱 밖 주소(예: 피드백 GitHub 이슈)는 새 창으로 연다
        if (new URL(url, window.location.origin).origin !== window.location.origin) window.open(url, "_blank", "noopener,noreferrer")
        else window.location.href = url
        n.close()
      }
    }
    return true
  } catch {
    return false
  }
}
