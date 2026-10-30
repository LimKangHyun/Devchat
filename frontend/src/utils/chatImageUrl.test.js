import { chatImageBase, chatImageSrc } from './chatImageUrl';

const R2 = 'https://pub-abc.r2.dev';

test('채팅 이미지 주소가 따로 있으면 그것을 쓴다', () => {
  expect(chatImageBase({ REACT_APP_CHAT_IMAGE_URL: `${R2}/images/chat`, REACT_APP_PROFILE_IMAGE_URL: `${R2}/images/profile` }))
    .toBe(`${R2}/images/chat`);
});

test('없으면 프로필 이미지 주소의 images/profile을 images/chat으로 바꿔 쓴다', () => {
  expect(chatImageBase({ REACT_APP_PROFILE_IMAGE_URL: `${R2}/images/profile` })).toBe(`${R2}/images/chat`);
  expect(chatImageBase({ REACT_APP_CHAT_IMAGE_URL: '', REACT_APP_PROFILE_IMAGE_URL: `${R2}/images/profile/` })).toBe(`${R2}/images/chat`);
});

test('끝의 / 는 하나만 붙는다', () => {
  expect(chatImageSrc('a.webp', { REACT_APP_CHAT_IMAGE_URL: `${R2}/images/chat/` })).toBe(`${R2}/images/chat/a.webp`);
});

test('파일 이름이 없으면 주소를 만들지 않는다', () => {
  expect(chatImageSrc(null, { REACT_APP_CHAT_IMAGE_URL: `${R2}/images/chat` })).toBeNull();
});
