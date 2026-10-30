// 휴대폰 뒤로가기(안드로이드 뒤로 버튼·제스처, 사파리 스와이프)로 열린 모달·시트를 닫는다.
// 레이어를 열면 같은 주소로 기록을 하나 쌓고, 뒤로가기로 그 기록이 빠지면 레이어를 닫는다.
// 버튼으로 닫으면 쌓아 둔 기록을 직접 빼서, 뒤로가기를 헛눌러야 하는 일이 없게 한다.

const KEY = '__dcLayer';
const layers = []; // 열린 순서대로 { id, close, pushed, done }
let seq = 0;
let skipPops = 0; // 우리가 직접 부른 history.go 때문에 올 popstate 수
const queue = []; // 그 popstate가 다 온 뒤에 할 일 (기록 순서가 꼬이지 않게)
let listening = false;

const currentId = () => window.history.state?.[KEY] ?? null;

const later = (fn) => {
  if (skipPops > 0) queue.push(fn);
  else fn();
};

const goBack = (n, then) => {
  skipPops += 1;
  if (then) queue.push(then);
  window.history.go(-n);
};

const onPop = () => {
  if (skipPops > 0) {
    skipPops -= 1;
    while (skipPops === 0 && queue.length) queue.shift()();
    return;
  }
  const id = currentId();
  const at = id == null ? -1 : layers.findIndex((l) => l.id === id);
  // 도착한 기록보다 위에 열려 있던 레이어를 위에서부터 닫는다
  layers.splice(at + 1).reverse().forEach((l) => { l.done = true; l.close(); });
  // 이미 닫힌 레이어의 기록이면(닫으면서 다른 화면으로 이동했던 경우) 한 칸 더 뒤로 간다
  if (id != null && at === -1) window.history.back();
};

const closeLayer = (layer) => {
  if (layer.done) return; // 뒤로가기로 이미 닫혔다
  layer.done = true;
  const i = layers.indexOf(layer);
  if (i !== -1) layers.splice(i, 1);
  // 지금 이 레이어 기록 위에 있을 때만 뺀다. 그사이 다른 화면으로 이동했으면 그대로 둔다
  later(() => { if (layer.pushed && currentId() === layer.id) goBack(1); });
};

// 레이어를 열고, 닫을 때 부를 함수를 돌려준다
export const openLayer = (close) => {
  if (!listening) {
    window.addEventListener('popstate', onPop);
    listening = true;
  }
  seq += 1;
  const layer = { id: `${Date.now()}-${seq}`, close, pushed: false, done: false };
  layers.push(layer);
  later(() => {
    if (layer.done) return;
    window.history.pushState({ ...window.history.state, [KEY]: layer.id }, '');
    layer.pushed = true;
  });
  return () => closeLayer(layer);
};

// 열린 레이어를 모두 닫고 그 기록까지 뺀 뒤에 끝난다.
// 닫자마자 navigate(..., { replace: true })를 해야 할 때 쓴다 (레이어 기록 대신 화면 기록을 바꾸도록)
export const closeAllLayers = () =>
  new Promise((resolve) => {
    later(() => {
      const at = layers.findIndex((l) => l.id === currentId());
      const n = at === -1 ? 0 : layers.slice(0, at + 1).filter((l) => l.pushed).length;
      layers.splice(0).reverse().forEach((l) => { l.done = true; l.close(); });
      if (n > 0) goBack(n, resolve);
      else resolve();
    });
  });

// 휴대폰 채팅방의 '<' 버튼. 앞 화면에서 들어왔으면 뒤로가기와 똑같이 기록을 되돌리고,
// 바로 열린 방이면(앞 기록 없음) 목록으로 바꾼다. 기록이 쌓이지 않아 뒤로가기로 방에 다시 들어가지 않는다.
// idx는 react-router가 기록마다 붙이는 순번이다.
export const backToList = (navigate) => {
  if ((window.history.state?.idx ?? 0) > 0) navigate(-1);
  else navigate('/', { replace: true });
};

// 방을 나가거나 지운 뒤: 열린 확인창의 기록까지 빼고, 방 화면 기록을 목록(또는 다른 방)으로 바꾼다.
// 뒤로가기를 눌러도 나간 방으로 돌아가지 않게 한다.
export const leaveTo = async (navigate, to = '/') => {
  await closeAllLayers();
  navigate(to, { replace: true });
};
