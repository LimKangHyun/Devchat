// "앱으로 설치"(PWA) 도우미.
// 안드로이드·PC 크롬/엣지는 beforeinstallprompt를 받아 두었다가 버튼에서 띄운다.
// 아이폰(사파리)은 이 이벤트가 없어 "공유 → 홈 화면에 추가" 안내를 보여줘야 한다.
import { useEffect, useState } from "react"

let deferredPrompt = null
const listeners = new Set()
const notify = () => listeners.forEach((fn) => fn())

/** 앱 진입점에서 한 번 부른다. 이벤트가 페이지 로드 직후에 오기 때문 */
export function listenForInstallPrompt() {
  if (typeof window === "undefined") return
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault() // 브라우저 기본 배너 대신 우리 버튼으로 띄운다
    deferredPrompt = e
    notify()
  })
  window.addEventListener("appinstalled", () => {
    deferredPrompt = null
    notify()
  })
}

/** 홈 화면 앱으로 열려 있는지 (주소창 없는 화면) */
export const isStandalone = () =>
  (typeof window !== "undefined" && window.matchMedia?.("(display-mode: standalone)").matches) ||
  (typeof navigator !== "undefined" && navigator.standalone === true)

/** 아이폰·아이패드 (아이패드는 PC용 사파리처럼 보이므로 터치 지원으로 구분) */
export const isIOS = () => {
  if (typeof navigator === "undefined") return false
  return /iphone|ipad|ipod/i.test(navigator.userAgent) ||
    (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
}

export function usePwaInstall() {
  const [, rerender] = useState(0)
  useEffect(() => {
    const fn = () => rerender((n) => n + 1)
    listeners.add(fn)
    return () => listeners.delete(fn)
  }, [])

  const installed = isStandalone()
  return {
    installed,
    canPrompt: !installed && !!deferredPrompt,
    needsIOSGuide: !installed && isIOS(),
    /** 설치 창을 띄운다. 설치했으면 true */
    promptInstall: async () => {
      const prompt = deferredPrompt
      if (!prompt) return false
      prompt.prompt()
      const { outcome } = await prompt.userChoice
      deferredPrompt = null // 한 번 쓴 이벤트는 다시 쓸 수 없다
      notify()
      return outcome === "accepted"
    },
  }
}
