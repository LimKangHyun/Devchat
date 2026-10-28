// 휴대폰 알림(웹 푸시) 구독 도우미.
// 아이폰은 iOS 16.4 이상에서 "홈 화면에 추가"로 설치한 앱에서만 푸시를 받을 수 있다.
import axiosInstance from "../components/api/axiosInstance"
import { isIOS, isStandalone } from "./pwaInstall"

export const pushSupported = () =>
  typeof window !== "undefined" &&
  "serviceWorker" in navigator && "PushManager" in window && "Notification" in window

export function registerServiceWorker() {
  if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return
  window.addEventListener("load", () => {
    // updateViaCache: 'none' → sw.js가 바뀌면 HTTP 캐시와 상관없이 바로 새로 받는다
    navigator.serviceWorker.register("/sw.js", { updateViaCache: "none" })
      .catch((e) => console.warn("서비스 워커 등록 실패", e))
  })
}

export function urlBase64ToUint8Array(base64) {
  const padded = (base64 + "=".repeat((4 - (base64.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/")
  const raw = atob(padded)
  return Uint8Array.from(raw, (c) => c.charCodeAt(0))
}

const sameKey = (subscription, publicKey) => {
  const current = subscription.options?.applicationServerKey
  if (!current) return true // 확인할 수 없으면 그대로 쓴다
  const a = new Uint8Array(current)
  const b = urlBase64ToUint8Array(publicKey)
  return a.length === b.length && a.every((v, i) => v === b[i])
}

/**
 * 지금 기기의 알림 상태
 * unsupported: 이 브라우저는 푸시를 못 받음
 * needs-install: 아이폰인데 아직 홈 화면 앱이 아님
 * denied: 브라우저 설정에서 알림이 차단됨
 * off / on
 */
export async function getPushState() {
  if (isIOS() && !isStandalone()) return "needs-install"
  if (!pushSupported()) return "unsupported"
  if (Notification.permission === "denied") return "denied"
  if (Notification.permission !== "granted") return "off"
  try {
    const reg = await navigator.serviceWorker.getRegistration()
    const sub = await reg?.pushManager.getSubscription()
    return sub ? "on" : "off"
  } catch {
    return "off"
  }
}

async function subscribeThisDevice() {
  const { data } = await axiosInstance.get("/push/public-key")
  if (!data?.enabled || !data.publicKey) return "server-off"

  const reg = await navigator.serviceWorker.ready
  let sub = await reg.pushManager.getSubscription()
  if (sub && !sameKey(sub, data.publicKey)) { // 서버 키가 바뀌었으면 새로 구독
    await sub.unsubscribe()
    sub = null
  }
  if (!sub) {
    sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(data.publicKey),
    })
  }
  await axiosInstance.post("/push/subscriptions", sub.toJSON())
  return "on"
}

/** 버튼을 눌렀을 때 부른다 (아이폰은 사용자가 누른 동작 안에서만 권한을 물을 수 있다) */
export async function enablePush() {
  if (isIOS() && !isStandalone()) return "needs-install"
  if (!pushSupported()) return "unsupported"
  const permission = await Notification.requestPermission()
  if (permission !== "granted") return permission === "denied" ? "denied" : "off"
  return subscribeThisDevice()
}

/** 로그인했을 때: 이미 허용한 기기면 조용히 이 계정으로 다시 구독한다 */
export async function syncPushSubscription() {
  try {
    if (!pushSupported() || Notification.permission !== "granted") return
    if (isIOS() && !isStandalone()) return
    await subscribeThisDevice()
  } catch (e) {
    console.warn("푸시 구독 동기화 실패", e)
  }
}

/** 알림 끄기·로그아웃: 서버에서 지우고 브라우저 구독도 해지한다 */
export async function disablePush() {
  if (!pushSupported()) return "unsupported"
  try {
    const reg = await navigator.serviceWorker.getRegistration()
    const sub = await reg?.pushManager.getSubscription()
    if (!sub) return "off"
    await axiosInstance.delete("/push/subscriptions", { data: { endpoint: sub.endpoint } }).catch(() => {})
    await sub.unsubscribe()
  } catch (e) {
    console.warn("푸시 구독 해지 실패", e)
  }
  return "off"
}
