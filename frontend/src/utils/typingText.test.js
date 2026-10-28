import { typingText, applyTypingEvent, activeTypers, TYPING_SHOW_MS } from './typingText';

describe('typingText', () => {
  it('사람 수에 맞게 문구를 만든다', () => {
    expect(typingText([])).toBe('');
    expect(typingText(['강현'])).toBe('강현님이 입력 중…');
    expect(typingText(['강현', '민수'])).toBe('강현님, 민수님이 입력 중…');
    expect(typingText(['강현', '민수', '지영', '철수'])).toBe('강현님 외 3명이 입력 중…');
  });
});

describe('applyTypingEvent', () => {
  const now = 1000;

  it('입력 중이면 넣고, 멈추면 뺀다', () => {
    let st = applyTypingEvent({}, { memberId: 2, nickname: '민수', typing: true }, 1, now);
    expect(activeTypers(st, now)).toEqual(['민수']);
    st = applyTypingEvent(st, { memberId: 2, nickname: '민수', typing: false }, 1, now + 10);
    expect(activeTypers(st, now + 10)).toEqual([]);
  });

  it('내 신호는 무시한다', () => {
    const st = applyTypingEvent({}, { memberId: 1, nickname: '나', typing: true }, 1, now);
    expect(activeTypers(st, now)).toEqual([]);
  });

  it('소식이 끊기면 잠시 뒤 사라진다', () => {
    const st = applyTypingEvent({}, { memberId: 2, nickname: '민수', typing: true }, 1, now);
    expect(activeTypers(st, now + TYPING_SHOW_MS - 1)).toEqual(['민수']);
    expect(activeTypers(st, now + TYPING_SHOW_MS + 1)).toEqual([]);
  });
});
