// 로그인 후 원래 가려던 화면(예: 초대 링크 /invite/...)으로 돌아가기 위한 도우미.
// 외부 주소로 튕기는 오픈 리다이렉트를 막으려고 앱 안 경로("/"로 시작, "//" 아님)만 허용한다.

const KEY = 'postLoginRedirect';

export const safeRedirect = (path) =>
  typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')
    && !path.startsWith('/login') && !path.startsWith('/signup')
    ? path
    : null;

const withRedirect = (base, currentPath) => {
  const path = safeRedirect(currentPath);
  return path && path !== '/' ? `${base}?redirect=${encodeURIComponent(path)}` : base;
};

/** 로그인 화면 주소. 돌아올 경로가 있으면 ?redirect=로 붙인다. */
export const loginUrlFor = (currentPath) => withRedirect('/login', currentPath);

/** 회원가입 화면 주소. 가입 후 로그인을 거쳐 돌아올 수 있도록 ?redirect=를 그대로 넘긴다. */
export const signupUrlFor = (currentPath) => withRedirect('/signup', currentPath);

/** GitHub 로그인은 서버가 항상 홈으로 돌려보내므로, 떠나기 전에 돌아올 경로를 저장해 둔다. */
export const rememberRedirect = (path) => {
  const safe = safeRedirect(path);
  try {
    if (safe) sessionStorage.setItem(KEY, safe);
  } catch { /* 저장소를 못 쓰면 홈으로 간다 */ }
};

/** 저장해 둔 경로를 꺼내고 지운다. */
export const consumeRedirect = () => {
  try {
    const path = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return safeRedirect(path);
  } catch {
    return null;
  }
};
