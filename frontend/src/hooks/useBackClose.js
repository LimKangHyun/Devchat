import { useEffect, useRef } from 'react';
import { openLayer } from '../utils/backLayers';
import { MOBILE_QUERY } from './useIsMobile';

// 휴대폰에서 open인 동안 뒤로가기를 누르면 앱을 나가거나 이전 화면으로 가지 않고 onClose만 부른다.
// 열렸을 때만 그려지는 모달은 useBackClose(true, onClose)로 쓴다. PC 화면에서는 아무것도 하지 않는다.
const useBackClose = (open, onClose) => {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open || !window.matchMedia?.(MOBILE_QUERY).matches) return undefined;
    return openLayer(() => onCloseRef.current?.());
  }, [open]);
};

export default useBackClose;
