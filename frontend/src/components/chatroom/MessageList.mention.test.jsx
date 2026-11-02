import React from 'react';
import { render, screen } from '@testing-library/react';
import { MessageItem, MentionParticipantsContext } from './MessageList';

jest.mock('react-highlight', () => ({ children }) => <pre>{children}</pre>);
jest.mock('../schedule/ScheduleModalContext', () => ({ useScheduleModal: () => ({ openCreate: () => {} }) }));

const msg = { messageId: 1, senderId: 9, type: 'TEXT', content: '@지수 @stranger @밥2 확인', createdAt: '2026-10-28T12:00:00' };

const renderItem = (participants) =>
  render(
    <MentionParticipantsContext.Provider value={participants}>
      <MessageItem msg={msg} currentUser={{ id: 1 }} />
    </MentionParticipantsContext.Provider>
  );

const hasMentionClass = (el) => /mention/.test(el.className || '');
const insideMention = (el) => {
  for (let n = el; n; n = n.parentElement) if (hasMentionClass(n)) return true;
  return false;
};

describe('MessageItem @멘션 강조', () => {
  it('방 참여자 닉네임만 mention 클래스를 갖는다', () => {
    renderItem([{ memberId: 1, nickname: '지수' }, { memberId: 2, nickname: '밥' }, { memberId: 3, nickname: '밥2' }]);
    const jisu = screen.getByText('@지수');
    const bab2 = screen.getByText('@밥2');
    expect(jisu.tagName).toBe('SPAN');
    expect(hasMentionClass(jisu)).toBe(true);
    expect(hasMentionClass(bab2)).toBe(true);

    const stranger = screen.getByText(/@stranger/);
    expect(insideMention(stranger)).toBe(false);
  });

  it('참여자 정보가 없으면 아무것도 강조하지 않는다', () => {
    renderItem([]);
    expect(insideMention(screen.getByText(/@지수 @stranger @밥2 확인/))).toBe(false);
  });
});
