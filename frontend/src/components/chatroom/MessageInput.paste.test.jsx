import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import MessageInput from './MessageInput';

// jsdom은 이미지를 그리지 못하므로 압축은 받은 파일을 그대로 돌려준다
// (CRA 설정이 테스트마다 jest.fn 구현을 지우므로 보통 함수로 둔다)
jest.mock('../../utils/imageCompress', () => ({
  compressImage: async (file) => file,
  CHAT_IMAGE_OPTIONS: {},
  MAX_UPLOAD_BYTES: 10 * 1024 * 1024,
  formatBytes: (n) => `${n}B`,
}));

const setup = () => {
  const props = {
    inputMode: 'TEXT',
    setInputMode: jest.fn(),
    content: '쓰던 글',
    setContent: jest.fn(),
    language: 'javascript',
    setLanguage: jest.fn(),
    handleUnifiedSend: jest.fn(),
    setImageFile: jest.fn(),
    imagePreviewUrl: null,
    setImagePreviewUrl: jest.fn(),
    roomName: '테스트방',
  };
  render(<MessageInput {...props} />);
  return { props, input: screen.getByRole('textbox') };
};

const clipboardData = ({ files = [], text = '' }) => ({
  items: files.map((f) => ({ kind: 'file', type: f.type, getAsFile: () => f })),
  getData: (type) => (type === 'text/plain' ? text : ''),
});

test('이미지를 붙여 넣으면 이미지 보내기로 바꾸고 첨부한다', async () => {
  const { props, input } = setup();
  const shot = new File(['png'], 'image.png', { type: 'image/png' });

  fireEvent.paste(input, { clipboardData: clipboardData({ files: [shot] }) });

  expect(props.setInputMode).toHaveBeenCalledWith('IMAGE');
  await waitFor(() => expect(props.setImageFile).toHaveBeenCalledTimes(1));
  expect(props.setImageFile.mock.calls[0][0].type).toBe('image/png');
  await waitFor(() => expect(props.setImagePreviewUrl).toHaveBeenCalled());
  // 쓰던 글은 건드리지 않는다
  expect(props.setContent).not.toHaveBeenCalled();
});

test('글자를 붙여 넣으면 평소처럼 둔다', () => {
  const { props, input } = setup();

  fireEvent.paste(input, { clipboardData: clipboardData({ text: '복사한 글' }) });

  expect(props.setInputMode).not.toHaveBeenCalled();
  expect(props.setImageFile).not.toHaveBeenCalled();
});
