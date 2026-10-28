import React from "react"
import ReactDOM from "react-dom/client"
import App from "./App"
import "./styles/theme.css"
import { listenForInstallPrompt } from "./utils/pwaInstall"
import { registerServiceWorker } from "./utils/pushNotifications"

// 설치 제안 이벤트는 페이지가 열리자마자 오므로 렌더링 전에 받아 둔다
listenForInstallPrompt()
// 푸시 알림용 서비스 워커 (화면 캐시는 하지 않는다)
registerServiceWorker()

const root = ReactDOM.createRoot(document.getElementById("root"))
root.render(
  <App />
)
