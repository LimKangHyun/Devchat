// 앱 안에서 OS 알림을 띄운다.
// 휴대폰(안드로이드·아이폰 설치 앱)은 new Notification()을 막으므로 서비스 워커로 띄운다.
// 알림을 누르면 서비스 워커(sw.js)가 data.url 화면을 연다.
import { isPushOnHere } from "./pushNotifications"

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
        window.location.href = url
        n.close()
      }
    }
    return true
  } catch {
    return false
  }
}
