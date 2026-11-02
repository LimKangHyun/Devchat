import { messageSegments, firstDateInMessage } from './chatSchedule';
import { findMentionedIds } from './mention';

const room = [{ memberId: 1, nickname: '지수' }, { memberId: 2, nickname: '밥' }];
const createdAt = '2026-10-28T12:00:00';
const mentionTexts = (content) => messageSegments(content, room).filter((s) => s.type === 'mention').map((s) => s.text);

describe('URL과 맞닿은 멘션: 강조와 알림이 같다', () => {
  it.each([
    '@지수https://x.y',
    '@지수 https://x.y',
    'https://x.y @지수',
    '@밥 보고 https://x.y/@지수',
    '@지수https://x.y @밥',
  ])('%j', (content) => {
    const highlighted = mentionTexts(content).map((t) => t.slice(1)).sort();
    const notified = findMentionedIds(content, room)
      .map((id) => room.find((p) => p.memberId === id).nickname)
      .sort();
    expect(highlighted).toEqual(notified);
  });

  it('@지수https://x.y 는 강조도 알림도 없다', () => {
    expect(mentionTexts('@지수https://x.y')).toEqual([]);
    expect(findMentionedIds('@지수https://x.y', room)).toEqual([]);
  });
});

describe('참여자가 아닌 @내일은 날짜 칩이 아니다', () => {
  it('@내일·@모레·@오늘 3시는 날짜로 보지 않는다', () => {
    expect(firstDateInMessage('@내일 안녕', createdAt, room)).toBeUndefined();
    expect(firstDateInMessage('@모레 안녕', createdAt, room)).toBeUndefined();
    expect(firstDateInMessage('@오늘 3시 모임', createdAt, room)).toBeUndefined();
  });

  it('그냥 쓴 내일 3시는 그대로 날짜로 본다', () => {
    expect(firstDateInMessage('내일 3시 모임', createdAt, room)).toBeDefined();
    expect(firstDateInMessage('@지수 내일 3시 모임', createdAt, room)).toBeDefined();
  });
});
