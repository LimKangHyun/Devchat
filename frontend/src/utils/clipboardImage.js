// 붙여 넣은 클립보드에서 보낼 이미지를 꺼낸다.
// 글자도 같이 있으면(웹페이지·문서 복사) 글자 붙여넣기로 두고 null을 돌려준다.

const EXT = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/gif': 'gif',
  'image/webp': 'webp',
  'image/bmp': 'bmp',
};

// 클립보드 이미지는 이름이 없거나 "image" 같아서, 서버가 확장자로 검사할 수 있게 형식에 맞는 이름을 붙인다
const withImageName = (file) => {
  const ext = EXT[file.type];
  if (!ext) return file;
  const name = file.name || '';
  const hasExt = name.toLowerCase().endsWith(`.${ext}`) || (ext === 'jpg' && /\.jpe?g$/i.test(name));
  if (hasExt) return file;
  return new File([file], `붙여넣은-이미지-${Date.now()}.${ext}`, { type: file.type, lastModified: Date.now() });
};

export const imageFromClipboard = (clipboardData) => {
  if (!clipboardData?.items) return null;
  if ((clipboardData.getData?.('text/plain') || '').trim()) return null;
  for (const item of Array.from(clipboardData.items)) {
    if (item.kind === 'file' && EXT[item.type]) {
      const file = item.getAsFile();
      if (file) return withImageName(file);
    }
  }
  return null;
};
