import { findMentionedIds, splitMentions } from './mention';

const P = (memberId, nickname) => ({ memberId, nickname });
const mentionsOf = (text, participants) =>
  splitMentions(text, participants).filter((p) => p.mention).map((p) => p.text);

describe('splitMentions (참여자만 강조)', () => {
  const room = [P(1, '지수'), P(2, '밥'), P(3, '밥2')];

  it('참여자가 아닌 @stranger, @everyone은 강조하지 않는다', () => {
    expect(mentionsOf('@stranger @everyone 안녕', room)).toEqual([]);
    expect(splitMentions('@stranger 안녕', room)).toEqual([{ text: '@stranger 안녕', mention: false }]);
  });

  it('참여자 목록이 없으면 아무것도 강조하지 않는다', () => {
    expect(mentionsOf('@지수 안녕', [])).toEqual([]);
    expect(mentionsOf('@지수 안녕', undefined)).toEqual([]);
  });

  it('참여자는 강조하고 앞뒤 글은 그대로 둔다', () => {
    expect(splitMentions('안녕 @지수 반가워', room)).toEqual([
      { text: '안녕 ', mention: false },
      { text: '@지수', mention: true },
      { text: ' 반가워', mention: false },
    ]);
  });

  it('밥과 밥2를 구분한다', () => {
    expect(mentionsOf('@밥2 와 @밥 보세요', room)).toEqual(['@밥2', '@밥']);
    expect(mentionsOf('@밥3 보세요', room)).toEqual([]);
    // 짧은 쪽이 목록 앞에 있어도 긴 닉네임을 고른다
    expect(mentionsOf('@밥2', [P(2, '밥'), P(3, '밥2')])).toEqual(['@밥2']);
  });

  it('공백과 특수문자가 든 닉네임도 맞춘다', () => {
    const odd = [P(4, 'Kim (dev)'), P(5, 'a.b+c')];
    expect(mentionsOf('@Kim (dev) 확인 부탁', odd)).toEqual(['@Kim (dev)']);
    expect(mentionsOf('@a.b+c 확인', odd)).toEqual(['@a.b+c']);
    expect(mentionsOf('@axb+c 확인', odd)).toEqual([]);
  });

  it('이메일은 강조하지 않는다', () => {
    expect(mentionsOf('a@b.com 로 보내줘', [P(6, 'b.com')])).toEqual([]);
    expect(mentionsOf('mail a@지수 로', room)).toEqual([]);
  });

  it('문장부호가 뒤에 붙어도 강조한다', () => {
    expect(mentionsOf('@지수, 안녕! @밥? @밥2.', room)).toEqual(['@지수', '@밥', '@밥2']);
    expect(mentionsOf('(@지수) 와 @밥:', room)).toEqual(['@밥']); // 여는 괄호 바로 뒤는 알림 규칙과 같이 제외
  });

  it('줄바꿈 뒤와 연달아 붙은 멘션도 처리한다', () => {
    expect(mentionsOf('@지수 @밥\n@밥2', room)).toEqual(['@지수', '@밥', '@밥2']);
  });

  it('같은 목록으로 여러 번 불러도 결과가 같다', () => {
    expect(splitMentions('@지수 @밥', room)).toEqual(splitMentions('@지수 @밥', room));
  });
});

describe('강조와 알림 대상이 같은 규칙이다', () => {
  const room = [P(1, '지수'), P(2, '밥'), P(3, '밥2'), P(4, 'Kim (dev)'), P(5, 'a.b+c')];
  const inputs = [
    '@지수 안녕',
    '@밥2 보세요',
    '@밥 보세요',
    '@밥3 보세요',
    'a@지수 로',
    '@Kim (dev), 확인',
    '@a.b+c! 확인',
    '@axb+c 확인',
    '(@밥) 와 @밥2.',
    '@stranger 만',
    '@지수@밥',
    '',
  ];

  it.each(inputs)('%j', (text) => {
    const highlighted = mentionsOf(text, room).map((t) => t.slice(1)).sort();
    const notified = findMentionedIds(text, room)
      .map((id) => room.find((p) => p.memberId === id).nickname)
      .sort();
    expect(highlighted).toEqual(notified);
  });
});
