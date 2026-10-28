// 앱 아이콘 숫자 배지 (안 읽은 채팅 수). 웹 표준 Badging API.
// 아이폰(iOS 16.4+, 홈 화면 앱)·PC에 설치한 크롬/엣지 앱에서 보인다. 안드로이드 크롬은 지원하지 않는다.
export function setAppBadgeCount(count) {
  try {
    if (typeof navigator === "undefined" || !navigator.setAppBadge) return
    if (count > 0) navigator.setAppBadge(count).catch(() => {})
    else navigator.clearAppBadge?.().catch(() => {})
  } catch { /* 지원하지 않거나 권한이 없으면 무시 */ }
}

export const clearAppBadge = () => setAppBadgeCount(0)
