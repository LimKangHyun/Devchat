import { MOBILE_QUERY } from '../hooks/useIsMobile';

// 아이폰 사파리는 viewport의 user-scalable=no를 무시해서, 두 손가락 확대 제스처를 직접 막는다.
// (두 번 탭 확대와 당겨서 새로고침은 styles/theme.css에서 막는다)
export const blockPinchZoom = () => {
  const prevent = (e) => e.preventDefault();
  document.addEventListener('gesturestart', prevent, { passive: false });
  document.addEventListener('gesturechange', prevent, { passive: false });
  document.addEventListener(
    'touchmove',
    (e) => { if (e.touches.length > 1) e.preventDefault(); },
    { passive: false },
  );
};

const isStandalone = () =>
  window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true;

const NO_HOME_BEHIND = ['/', '/blank', '/login', '/signup', '/error'];

// 설치한 앱을 알림 등으로 채팅방에서 바로 열면 뒤에 기록이 없어 뒤로가기 한 번에 앱이 닫힌다.
// 처음 열릴 때만 목록('/') 기록을 뒤에 깔아서, 뒤로가기가 목록으로 가게 한다.
// react-router가 렌더링 전에 읽도록 앱을 그리기 전에 부른다. 상태 모양({usr,key,idx})은 react-router 것을 따른다.
export const seedHomeEntry = () => {
  if (!isStandalone() || !window.matchMedia?.(MOBILE_QUERY).matches) return;
  // 새로고침처럼 이미 기록이 있으면 건드리지 않는다
  if (window.history.length > 1 || window.history.state) return;
  const { pathname, search, hash } = window.location;
  if (NO_HOME_BEHIND.includes(pathname)) return;
  window.history.replaceState({ usr: null, key: 'home', idx: 0 }, '', '/');
  window.history.pushState({ usr: null, key: 'entry', idx: 1 }, '', pathname + search + hash);
};
