import { useEffect, useRef } from 'react';

// Esc로 창을 닫는다. disabled면(저장 중 등) 무시한다
const useEscapeKey = (onClose, disabled = false) => {
  const ref = useRef(onClose);
  useEffect(() => { ref.current = onClose; }, [onClose]);

  useEffect(() => {
    if (disabled) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') ref.current?.();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [disabled]);
};

export default useEscapeKey;
