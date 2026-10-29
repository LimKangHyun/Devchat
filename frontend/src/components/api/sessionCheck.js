// 다른 기기에서 로그아웃시켰다는 알림을 받았을 때 이 기기가 끊긴 건지 확인한다.
// 직접 refresh하면 모든 탭이 동시에 토큰을 돌려 멀쩡한 탭이 로그아웃될 수 있어서,
// 가벼운 인증 GET만 보낸다. 끊긴 기기면 401 → 인터셉터의 refresh 실패 → 로그인 화면.
export const checkSessionStillValid = async (client) => {
  try {
    await client.get('/sessions');
  } catch (e) {
    // 그 밖의 오류(네트워크, 5xx)는 그대로 둔다
  }
};
