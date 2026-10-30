import { imageFromClipboard } from './clipboardImage';

// 브라우저 ClipboardEvent.clipboardData 모양을 흉내 낸다
const clipboard = ({ files = [], text = '' } = {}) => ({
  items: files.map((f) => ({ kind: 'file', type: f.type, getAsFile: () => f })),
  getData: (type) => (type === 'text/plain' ? text : ''),
});

const png = (name = 'image.png') => new File(['x'], name, { type: 'image/png' });

test('이미지만 붙여 넣으면 그 이미지를 돌려준다', () => {
  const file = imageFromClipboard(clipboard({ files: [png()] }));
  expect(file).not.toBeNull();
  expect(file.type).toBe('image/png');
});

test('글자도 같이 있으면 글자로 붙여 넣도록 null', () => {
  expect(imageFromClipboard(clipboard({ files: [png()], text: '복사한 글' }))).toBeNull();
});

test('공백뿐인 글자는 없는 것으로 본다', () => {
  expect(imageFromClipboard(clipboard({ files: [png()], text: '  \n' }))).not.toBeNull();
});

test('이미지가 아닌 파일이나 빈 클립보드는 null', () => {
  const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });
  expect(imageFromClipboard(clipboard({ files: [pdf] }))).toBeNull();
  expect(imageFromClipboard(clipboard())).toBeNull();
  expect(imageFromClipboard(null)).toBeNull();
});

test('서버가 확장자로 검사하므로 형식에 맞는 이름을 붙인다', () => {
  expect(imageFromClipboard(clipboard({ files: [png('')] })).name).toMatch(/^붙여넣은-이미지-\d+\.png$/);
  const jpeg = new File(['x'], 'image', { type: 'image/jpeg' });
  expect(imageFromClipboard(clipboard({ files: [jpeg] })).name).toMatch(/\.jpg$/);
});

test('이름에 맞는 확장자가 이미 있으면 그대로 둔다', () => {
  expect(imageFromClipboard(clipboard({ files: [png('스크린샷.png')] })).name).toBe('스크린샷.png');
});
