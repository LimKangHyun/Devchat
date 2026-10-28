import { useCallback, useEffect, useRef, useState } from 'react';
import { applyTypingEvent, activeTypers, typingText } from '../../utils/typingText';

// 입력하는 동안 이 간격으로 "입력 중"을 다시 알린다 (서버는 2초보다 잦으면 버린다)
const RESEND_MS = 2500;
// 입력이 이만큼 멈추면 "멈춤"을 알린다
const IDLE_MS = 4000;

/**
 * 같은 방 사람들의 "입력 중" 표시.
 * - notifyTyping(text): 입력창 내용이 바뀔 때 부른다 (비면 멈춤)
 * - stopTyping(): 보냈을 때 부른다
 * - text: "○○님이 입력 중…" (아무도 없으면 '')
 */
export default function useTypingIndicator({ roomId, clientRef, connected, myId }) {
  const [typers, setTypers] = useState({});
  const sentAtRef = useRef(0);
  const typingRef = useRef(false);
  const idleTimerRef = useRef(null);
  const roomRef = useRef(roomId);
  roomRef.current = roomId;

  const publish = useCallback((typing, targetRoom = roomRef.current) => {
    const client = clientRef.current;
    if (!client?.connected || !targetRoom) return;
    try {
      client.publish({ destination: `/chat/typing/${targetRoom}`, body: JSON.stringify({ typing }) });
    } catch (e) { /* 끊겨 있으면 그냥 넘긴다 */ }
  }, [clientRef]);

  const stopTyping = useCallback(() => {
    clearTimeout(idleTimerRef.current);
    if (!typingRef.current) return;
    typingRef.current = false;
    sentAtRef.current = 0;
    publish(false);
  }, [publish]);

  const notifyTyping = useCallback((text) => {
    if (!String(text || '').trim()) { stopTyping(); return; }
    const now = Date.now();
    if (!typingRef.current || now - sentAtRef.current >= RESEND_MS) {
      typingRef.current = true;
      sentAtRef.current = now;
      publish(true);
    }
    clearTimeout(idleTimerRef.current);
    idleTimerRef.current = setTimeout(stopTyping, IDLE_MS);
  }, [publish, stopTyping]);

  // 방 신호 받기
  useEffect(() => {
    const client = clientRef.current;
    setTypers({});
    if (!connected || !client?.connected || !roomId) return undefined;
    const sub = client.subscribe(`/topic/chat/${roomId}/typing`, (frame) => {
      try {
        const event = JSON.parse(frame.body);
        setTypers((prev) => applyTypingEvent(prev, event, myId));
      } catch (e) { /* 형식이 이상한 신호는 무시 */ }
    });
    return () => { try { if (client.connected) sub.unsubscribe(); } catch (e) {} };
  }, [connected, roomId, clientRef, myId]);

  // 소식이 끊긴 사람은 잠시 뒤 지운다
  useEffect(() => {
    if (!Object.keys(typers).length) return undefined;
    const timer = setInterval(() => setTypers((prev) => applyTypingEvent(prev, null, myId)), 1000);
    return () => clearInterval(timer);
  }, [typers, myId]);

  // 방을 옮기거나 나가면 내 "입력 중"을 멈춘다
  useEffect(() => () => {
    clearTimeout(idleTimerRef.current);
    if (typingRef.current) {
      typingRef.current = false;
      publish(false, roomId);
    }
  }, [roomId, publish]);

  return { text: typingText(activeTypers(typers)), notifyTyping, stopTyping };
}
