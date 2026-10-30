import { useEffect } from "react"
import { Share, SquarePlus, X } from "lucide-react"
import styles from "./IOSInstallGuide.module.css"
import useBackClose from "../../hooks/useBackClose"

/** 아이폰은 설치 버튼을 띄울 수 없어서 사파리 "홈 화면에 추가" 방법을 안내한다 */
export default function IOSInstallGuide({ onClose }) {
  useBackClose(true, onClose)
  useEffect(() => {
    const onKey = (e) => { if (e.key === "Escape") onClose() }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [onClose])

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div
        className={styles.sheet}
        role="dialog"
        aria-modal="true"
        aria-labelledby="ios-install-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.head}>
          <img src="/icons/icon-192.png" alt="" className={styles.appIcon} />
          <div>
            <h2 id="ios-install-title" className={styles.title}>DevChat을 홈 화면에 추가</h2>
            <p className={styles.sub}>앱처럼 열리고, 앱을 닫아도 멘션·DM 알림을 받을 수 있어요.</p>
          </div>
          <button type="button" className={styles.close} onClick={onClose} aria-label="닫기">
            <X size={18} />
          </button>
        </div>

        <ol className={styles.steps}>
          <li>
            <span className={styles.stepIcon}><Share size={18} /></span>
            사파리 아래쪽(아이패드는 위쪽)의 <b>공유</b> 버튼을 누르세요.
          </li>
          <li>
            <span className={styles.stepIcon}><SquarePlus size={18} /></span>
            목록을 내려 <b>홈 화면에 추가</b>를 누르세요.
          </li>
          <li>
            <span className={styles.stepIcon}><img src="/icons/icon-192.png" alt="" /></span>
            홈 화면의 <b>DevChat</b>으로 연 뒤, 프로필 메뉴에서 <b>푸시 알림 켜기</b>를 누르세요.
          </li>
        </ol>

        <p className={styles.foot}>사파리에서만 추가할 수 있어요. 알림은 iOS 16.4 이상에서 받을 수 있어요.</p>
      </div>
    </div>
  )
}
