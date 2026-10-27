import { useEffect, useState } from 'react';

export const MOBILE_QUERY = '(max-width: 768px)';

const getMatch = () =>
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia(MOBILE_QUERY).matches
    : false;

// 화면 폭이 768px 이하이면 모바일 레이아웃으로 본다
const useIsMobile = () => {
  const [isMobile, setIsMobile] = useState(getMatch);

  useEffect(() => {
    if (!window.matchMedia) return;
    const mql = window.matchMedia(MOBILE_QUERY);
    const onChange = (e) => setIsMobile(e.matches);
    setIsMobile(mql.matches);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return isMobile;
};

export default useIsMobile;
