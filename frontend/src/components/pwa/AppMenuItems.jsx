import { useEffect, useState } from "react"
import { Download, BellRing, BellOff } from "lucide-react"
import { usePwaInstall } from "../../utils/pwaInstall"
import { disablePush, enablePush, getPushState } from "../../utils/pushNotifications"

const MESSAGES = {
  denied: "알림이 차단돼 있어요. 브라우저 설정에서 이 사이트의 알림을 허용해 주세요.",
  "server-off": "아직 서버에서 푸시 알림을 보낼 수 없어요.",
  unsupported: "이 브라우저는 푸시 알림을 지원하지 않아요.",
}

/**
 * 프로필 메뉴에 들어가는 "앱으로 설치", "푸시 알림 켜기/끄기".
 * 아이폰 설치 안내 창은 메뉴가 닫혀도 남아야 하므로 부모가 그린다(onShowIOSGuide).
 */
export default function AppMenuItems({ itemClassName, noteClassName, onShowIOSGuide, onClose }) {
  const { canPrompt, needsIOSGuide, promptInstall } = usePwaInstall()
  const [pushState, setPushState] = useState(null)
  const [busy, setBusy] = useState(false)
  const [note, setNote] = useState("")

  useEffect(() => {
    let alive = true
    getPushState().then((s) => { if (alive) setPushState(s) })
    return () => { alive = false }
  }, [])

  const togglePush = async (turnOn) => {
    setBusy(true)
    setNote("")
    try {
      const next = turnOn ? await enablePush() : await disablePush()
      setPushState(next === "server-off" ? "off" : next)
      if (MESSAGES[next]) setNote(MESSAGES[next])
      else if (next === "on") setNote("앱을 닫아도 멘션·DM 알림이 와요.")
    } catch (e) {
      console.error(e)
      setNote("알림 설정을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {canPrompt && (
        <button type="button" className={itemClassName} onClick={async () => { await promptInstall(); onClose?.() }}>
          <Download size={16} />
          앱으로 설치
        </button>
      )}
      {needsIOSGuide && (
        <button type="button" className={itemClassName} onClick={() => { onShowIOSGuide?.(); onClose?.() }}>
          <Download size={16} />
          앱으로 설치
        </button>
      )}
      {pushState === "off" && (
        <button type="button" className={itemClassName} disabled={busy} onClick={() => togglePush(true)}>
          <BellRing size={16} />
          푸시 알림 켜기
        </button>
      )}
      {pushState === "on" && (
        <button type="button" className={itemClassName} disabled={busy} onClick={() => togglePush(false)}>
          <BellOff size={16} />
          푸시 알림 끄기
        </button>
      )}
      {pushState === "denied" && !note && <p className={noteClassName}>{MESSAGES.denied}</p>}
      {note && <p className={noteClassName} role="status">{note}</p>}
    </>
  )
}
