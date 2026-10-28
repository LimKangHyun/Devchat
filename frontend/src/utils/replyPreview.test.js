import { replyPreviewOf } from './replyPreview';

describe('replyPreviewOf', () => {
  it('글은 한 줄로 줄이고 80자에서 자른다', () => {
    expect(replyPreviewOf({ type: 'TEXT', content: '안녕\n  하세요' })).toBe('안녕 하세요');
    expect(replyPreviewOf({ type: 'TEXT', content: 'a'.repeat(100) })).toBe(`${'a'.repeat(80)}…`);
  });

  it('이미지·코드는 표시만 한다', () => {
    expect(replyPreviewOf({ type: 'IMAGE' })).toBe('[이미지]');
    expect(replyPreviewOf({ type: 'CODE', content: 'int a = 1;\nint b = 2;' })).toBe('[코드] int a = 1;');
  });
});
