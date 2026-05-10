import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiClientMocks = vi.hoisted(() => {
  let xsrfToken = 'initial-token';
  return {
    post: vi.fn(),
    setXsrfToken(nextXsrfToken: string) {
      xsrfToken = nextXsrfToken;
    },
    xsrfHeader: vi.fn(() => ({ 'X-XSRF-TOKEN': xsrfToken })),
  };
});

vi.mock('@/lib/api/client', () => ({
  api: {
    GET: vi.fn(),
    POST: apiClientMocks.post,
    PUT: vi.fn(),
    DELETE: vi.fn(),
    PATCH: vi.fn(),
  },
  xsrfHeader: apiClientMocks.xsrfHeader,
}));

import { compileRule } from '@/features/rules/api/rules-api';

describe('rules api client', () => {
  beforeEach(() => {
    apiClientMocks.post.mockReset();
    apiClientMocks.xsrfHeader.mockClear();
    apiClientMocks.setXsrfToken('initial-token');
    apiClientMocks.post.mockResolvedValue({
      data: { status: 'invalid', invalid: { reason: 'test' } },
      response: { ok: true, status: 200 },
    });
  });

  it('reads the XSRF token for each JSON mutation request', async () => {
    apiClientMocks.setXsrfToken('rotated-token');
    await compileRule({ sourceText: 'Archive receipts from Stripe' });
    apiClientMocks.setXsrfToken('next-token');
    await compileRule({ sourceText: 'Archive newsletters' });

    expect(apiClientMocks.xsrfHeader).toHaveBeenCalledTimes(2);
    expect(apiClientMocks.post).toHaveBeenNthCalledWith(
      1,
      '/api/rules/compile',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'rotated-token' }),
      }),
    );
    expect(apiClientMocks.post).toHaveBeenNthCalledWith(
      2,
      '/api/rules/compile',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'next-token' }),
      }),
    );
  });
});
