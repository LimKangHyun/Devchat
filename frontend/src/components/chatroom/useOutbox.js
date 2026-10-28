import { useCallback, useEffect, useRef, useState } from "react"

// 보낸 뒤 이 시간 안에 서버가 메시지를 돌려주지 않으면 "전송 실패"로 본다
export const SEND_TIMEOUT_MS = 10000

export const newClientMessageId = () =>
  (typeof crypto !== "undefined" && crypto.randomUUID)
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`

/**
 * 아직 서버가 받았다고 확인하지 않은 내 메시지들.
 *
 * - 연결돼 있으면 바로 보내고 SEND_TIMEOUT_MS 동안 확인을 기다린다.
 * - 끊겨 있으면 "sending"으로 두었다가 다시 연결되면(flush) 보낸다.
 * - 연결이 끊기면(pause) 기다리던 시간을 멈춘다. 끊긴 탓에 실패로 처리하지 않도록.
 * - 같은 clientMessageId로 다시 보내도 서버가 중복 저장하지 않는다.
 *
 * item: { clientMessageId, body, status: 'sending' | 'failed', error, waiting }
 *   waiting: 연결을 기다리는 중(아직 한 번도 못 보냈거나 끊겨서 멈춤)
 */
export default function useOutbox({ publish, isConnected }) {
  const [items, setItems] = useState([])
  const itemsRef = useRef([])
  const timersRef = useRef(new Map())

  const update = useCallback((fn) => {
    setItems((prev) => {
      const next = fn(prev)
      itemsRef.current = next
      return next
    })
  }, [])

  const clearTimer = useCallback((id) => {
    const t = timersRef.current.get(id)
    if (t) clearTimeout(t)
    timersRef.current.delete(id)
  }, [])

  const patch = useCallback((id, fields) => {
    update((prev) => prev.map((it) => (it.clientMessageId === id ? { ...it, ...fields } : it)))
  }, [update])

  const fail = useCallback((id, error = "전송하지 못했어요.") => {
    if (!itemsRef.current.some((it) => it.clientMessageId === id)) return
    clearTimer(id)
    patch(id, { status: "failed", error, waiting: false })
  }, [clearTimer, patch])

  const send = useCallback((item) => {
    const id = item.clientMessageId
    clearTimer(id)
    if (!isConnected()) {
      patch(id, { status: "sending", error: null, waiting: true })
      return
    }
    try {
      publish(item.body, id)
    } catch {
      patch(id, { status: "sending", error: null, waiting: true })
      return
    }
    patch(id, { status: "sending", error: null, waiting: false })
    timersRef.current.set(id, setTimeout(() => fail(id, "서버 응답이 없어요."), SEND_TIMEOUT_MS))
  }, [clearTimer, fail, isConnected, patch, publish])

  /** 새 메시지를 보낸다. 반환값은 붙인 임시 id */
  const enqueue = useCallback((body) => {
    const item = { clientMessageId: newClientMessageId(), body, status: "sending", error: null, waiting: true }
    update((prev) => [...prev, item])
    itemsRef.current = [...itemsRef.current.filter((it) => it.clientMessageId !== item.clientMessageId), item]
    send(item)
    return item.clientMessageId
  }, [send, update])

  /** 서버가 메시지를 받았다 (방 메시지·확인 응답·다시 불러온 목록에서 같은 id를 봤을 때) */
  const resolve = useCallback((id) => {
    if (!id) return
    clearTimer(id)
    update((prev) => prev.filter((it) => it.clientMessageId !== id))
  }, [clearTimer, update])

  const retry = useCallback((id) => {
    const item = itemsRef.current.find((it) => it.clientMessageId === id)
    if (item) send(item)
  }, [send])

  const discard = useCallback((id) => {
    clearTimer(id)
    update((prev) => prev.filter((it) => it.clientMessageId !== id))
  }, [clearTimer, update])

  /** 연결이 끊겼다: 기다리던 시간을 멈추고 연결을 기다린다 */
  const pause = useCallback(() => {
    itemsRef.current
      .filter((it) => it.status === "sending")
      .forEach((it) => { clearTimer(it.clientMessageId); patch(it.clientMessageId, { waiting: true }) })
  }, [clearTimer, patch])

  /** 다시 연결됐다: 전송 중이던 메시지를 같은 id로 다시 보낸다 (서버가 중복을 걸러낸다) */
  const flush = useCallback(() => {
    itemsRef.current.filter((it) => it.status === "sending").forEach(send)
  }, [send])

  useEffect(() => () => {
    timersRef.current.forEach(clearTimeout)
    timersRef.current.clear()
  }, [])

  return { items, enqueue, resolve, fail, retry, discard, pause, flush }
}
