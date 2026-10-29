import { checkSessionStillValid } from './sessionCheck';
import { safeRefreshToken } from './refreshManager';

jest.mock('./refreshManager', () => ({ safeRefreshToken: jest.fn() }));

describe('checkSessionStillValid', () => {
  beforeEach(() => jest.clearAllMocks());

  it('인증이 필요한 GET 하나만 보내고 직접 refresh하지 않는다', async () => {
    const client = { get: jest.fn().mockResolvedValue({ data: [] }) };

    await checkSessionStillValid(client);

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/sessions');
    expect(safeRefreshToken).not.toHaveBeenCalled();
  });

  it('요청이 실패해도 던지지 않는다 (로그인 이동은 인터셉터가 맡는다)', async () => {
    const client = { get: jest.fn().mockRejectedValue({ response: { status: 500 } }) };

    await expect(checkSessionStillValid(client)).resolves.toBeUndefined();
    expect(safeRefreshToken).not.toHaveBeenCalled();
  });
});
