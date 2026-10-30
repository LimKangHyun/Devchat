import { openLayer, closeAllLayers } from './backLayers';

// jsdom의 history.go/back은 비동기로 popstate를 보낸다
const flush = () => new Promise((r) => setTimeout(r, 30));
const layerId = () => window.history.state?.__dcLayer ?? null;

let n = 0;
// 라우터가 화면을 하나 쌓은 것처럼 기록을 만든다
const routerPush = (path) => {
  n += 1;
  window.history.pushState({ usr: null, key: `k${n}`, idx: n }, '', path);
};

beforeEach(async () => {
  routerPush('/');
  await flush();
});

test('뒤로가기를 누르면 열린 레이어만 닫히고 화면은 그대로다', async () => {
  const close = jest.fn();
  openLayer(close);
  expect(layerId()).not.toBeNull();

  window.history.back();
  await flush();

  expect(close).toHaveBeenCalledTimes(1);
  expect(layerId()).toBeNull();
  expect(window.location.pathname).toBe('/');
});

test('버튼으로 닫으면 쌓아 둔 기록을 빼고 onClose는 다시 부르지 않는다', async () => {
  const startLen = window.history.length;
  const close = jest.fn();
  const done = openLayer(close);
  done();
  await flush();

  expect(close).not.toHaveBeenCalled();
  expect(layerId()).toBeNull();
  expect(window.history.state.key).toBe(`k${n}`);
  // 앞쪽 기록은 남지만 지금 위치는 레이어를 열기 전으로 돌아왔다
  expect(window.history.length).toBeGreaterThanOrEqual(startLen);
});

test('겹쳐 연 레이어는 뒤로가기 한 번에 맨 위 것만 닫힌다', async () => {
  const closeA = jest.fn();
  const closeB = jest.fn();
  openLayer(closeA);
  openLayer(closeB);

  window.history.back();
  await flush();
  expect(closeB).toHaveBeenCalledTimes(1);
  expect(closeA).not.toHaveBeenCalled();

  window.history.back();
  await flush();
  expect(closeA).toHaveBeenCalledTimes(1);
  expect(layerId()).toBeNull();
});

test('메뉴를 닫으면서 바로 모달을 열어도 기록이 하나만 남는다', async () => {
  const base = window.history.state.key;
  const closeMenu = openLayer(jest.fn());
  const closeModal = jest.fn();

  closeMenu();
  openLayer(closeModal);
  await flush();

  const modalEntry = layerId();
  expect(modalEntry).not.toBeNull();

  window.history.back();
  await flush();
  expect(closeModal).toHaveBeenCalledTimes(1);
  expect(layerId()).toBeNull();
  expect(window.history.state.key).toBe(base);
});

test('닫으면서 다른 화면으로 이동했으면 기록을 빼지 않고, 나중에 그 기록은 건너뛴다', async () => {
  const base = window.history.state.key;
  const close = jest.fn();
  const done = openLayer(close);

  routerPush('/chat/abc'); // 알림을 눌러 방으로 이동
  done();
  await flush();
  expect(window.location.pathname).toBe('/chat/abc');

  window.history.back(); // 방에서 뒤로가기 → 닫힌 레이어 기록에 도착 → 한 칸 더
  await flush();
  await flush();
  expect(close).not.toHaveBeenCalled();
  expect(layerId()).toBeNull();
  expect(window.history.state.key).toBe(base);
});

test('closeAllLayers는 열린 레이어를 다 닫고 기록까지 뺀 뒤에 끝난다', async () => {
  const base = window.history.state.key;
  const closeA = jest.fn();
  const closeB = jest.fn();
  openLayer(closeA);
  openLayer(closeB);

  await closeAllLayers();

  expect(closeA).toHaveBeenCalledTimes(1);
  expect(closeB).toHaveBeenCalledTimes(1);
  expect(layerId()).toBeNull();
  expect(window.history.state.key).toBe(base);
});
