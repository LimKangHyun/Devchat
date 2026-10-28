import { validateFeedback, MAX_TITLE, MAX_CONTENT } from './feedbackForm';

describe('validateFeedback', () => {
  it('제목·내용이 있으면 통과', () => {
    expect(validateFeedback({ title: '전송이 안 돼요', content: '버튼을 눌러도 안 가요' })).toBeNull();
  });

  it('공백만 있으면 막는다', () => {
    expect(validateFeedback({ title: '   ', content: '내용' })).toBe('제목을 입력해 주세요.');
    expect(validateFeedback({ title: '제목', content: '\n  ' })).toBe('내용을 입력해 주세요.');
  });

  it('너무 길면 막는다', () => {
    expect(validateFeedback({ title: 'a'.repeat(MAX_TITLE + 1), content: '내용' })).toMatch('80자');
    expect(validateFeedback({ title: '제목', content: 'a'.repeat(MAX_CONTENT + 1) })).toMatch('3000자');
  });
});
