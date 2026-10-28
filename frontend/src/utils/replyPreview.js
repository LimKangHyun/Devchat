// 답장에 보일 원문 한 줄 (서버 ReplyPreview와 같은 규칙)
const PREVIEW_LENGTH = 80;

const oneLine = (s) => String(s || '').replace(/\s+/g, ' ').trim();
const cut = (s) => (s.length > PREVIEW_LENGTH ? `${s.slice(0, PREVIEW_LENGTH)}…` : s);

export function replyPreviewOf(message) {
  if (!message) return '';
  if (message.type === 'IMAGE') return '[이미지]';
  if (message.type === 'AI_REVIEW') return 'AI 코드 리뷰';
  if (message.type === 'CODE') {
    const first = String(message.content || '').trim().split(/\r?\n/)[0] || '';
    return `[코드] ${cut(oneLine(first))}`;
  }
  return cut(oneLine(message.content));
}
