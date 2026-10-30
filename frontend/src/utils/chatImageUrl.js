// 채팅 이미지 주소. 서버는 프로필(images/profile/)과 같은 저장소의 images/chat/에 올린다.
// 배포 빌드에 REACT_APP_CHAT_IMAGE_URL이 빠져 있어 이미지가 안 보였던 적이 있어서,
// 없으면 프로필 이미지 주소에서 만든다.
const BUILD_ENV = {
  REACT_APP_CHAT_IMAGE_URL: process.env.REACT_APP_CHAT_IMAGE_URL,
  REACT_APP_PROFILE_IMAGE_URL: process.env.REACT_APP_PROFILE_IMAGE_URL,
};

const trimSlash = (url) => url.replace(/\/+$/, '');

export const chatImageBase = (env = BUILD_ENV) => {
  if (env.REACT_APP_CHAT_IMAGE_URL) return trimSlash(env.REACT_APP_CHAT_IMAGE_URL);
  const profile = env.REACT_APP_PROFILE_IMAGE_URL;
  if (!profile) return '';
  return trimSlash(profile).replace(/\/images\/profile$/, '/images/chat');
};

export const chatImageSrc = (fileName, env = BUILD_ENV) =>
  fileName ? `${chatImageBase(env)}/${fileName}` : null;
