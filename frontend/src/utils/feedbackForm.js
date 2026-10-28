// 피드백 보내기 폼 (서버 FeedbackRequest와 같은 규칙)
export const FEEDBACK_TYPES = [
  ['BUG', '버그'],
  ['IDEA', '제안'],
  ['OTHER', '기타'],
];
export const MAX_TITLE = 80;
export const MAX_CONTENT = 3000;

// 문제가 있으면 안내 문구, 없으면 null
export function validateFeedback({ title, content }) {
  const t = (title || '').trim();
  const c = (content || '').trim();
  if (!t) return '제목을 입력해 주세요.';
  if (t.length > MAX_TITLE) return `제목은 ${MAX_TITLE}자까지 쓸 수 있어요.`;
  if (!c) return '내용을 입력해 주세요.';
  if (c.length > MAX_CONTENT) return `내용은 ${MAX_CONTENT}자까지 쓸 수 있어요.`;
  return null;
}
