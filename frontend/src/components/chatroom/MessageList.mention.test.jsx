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

describe('MessageItem @멘션 강조', () => {
  it('방 참여자 닉네임만 강조한다', () => {
    renderItem([{ memberId: 1, nickname: '지수' }, { memberId: 2, nickname: '밥' }, { memberId: 3, nickname: '밥2' }]);
    expect(screen.getByText('@지수').className).toMatch(/mention/);
    expect(screen.getByText('@밥2').className).toMatch(/mention/);
    expect(screen.queryByText('@stranger')).toBeNull(); // 일반 글로 합쳐져 있다
    expect(screen.getByText(/@stranger/).className || '').not.toMatch(/mention/);
  });

  it('참여자 정보가 없으면 강조하지 않는다', () => {
    renderItem([]);
    expect(screen.queryByText('@지수')).toBeNull();
  });
});
