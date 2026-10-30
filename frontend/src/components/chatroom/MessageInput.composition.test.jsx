import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import MessageInput from './MessageInput';

// 아이폰: 마지막 글자를 조합하는 중에 전송 버튼을 누르면, 입력창을 비운 뒤 키보드가 그 글자를 다시 넣어
// 다음 메시지가 "요저는"처럼 시작했다 (#98)
const setup = () => {
  const calls = [];
  const props = {
    inputMode: 'TEXT',
    setInputMode: () => {},
    content: '안녕하세요',
    setContent: () => {},
    language: 'javascript',
    setLanguage: () => {},
    handleUnifiedSend: () => calls.push('send'),
    setImageFile: () => {},
    imagePreviewUrl: null,
    setImagePreviewUrl: () => {},
    roomName: '테스트방',
  };
  render(<MessageInput {...props} />);
  const input = screen.getByRole('textbox');
  input.focus();
  const blur = input.blur.bind(input);
  const focus = input.focus.bind(input);
  input.blur = () => { calls.push('blur'); blur(); };
  input.focus = () => { calls.push('focus'); focus(); };
  return { calls, input, sendBtn: screen.getByRole('button', { name: '전송' }) };
};

test('조합 중에 전송 버튼을 누르면 조합을 끝낸 뒤 보내고 입력창을 다시 잡는다', () => {
  const { calls, input, sendBtn } = setup();

  fireEvent.compositionStart(input);
  fireEvent.click(sendBtn);

  expect(calls).toEqual(['blur', 'focus', 'send']);
});

test('조합 중이 아니면 입력창을 건드리지 않고 바로 보낸다', () => {
  const { calls, input, sendBtn } = setup();

  fireEvent.compositionStart(input);
  fireEvent.compositionEnd(input);
  fireEvent.click(sendBtn);

  expect(calls).toEqual(['send']);
});
