import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const redirectMock = vi.hoisted(() =>
  vi.fn((redirectUrl: string) => {
    throw new Error(`NEXT_REDIRECT:${redirectUrl}`);
  }),
);

vi.mock('next/navigation', () => ({
  redirect: redirectMock,
}));

describe('public referral redirect page', () => {
  beforeEach(() => {
    redirectMock.mockClear();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('routes same-origin production referral links through the backend api path', async () => {
    vi.stubEnv('NEXT_PUBLIC_API_BASE', 'https://zeromail.vn');

    const { default: PublicReferralRedirectPage } = await import('@/app/(public)/r/[code]/page');

    await expect(
      PublicReferralRedirectPage({
        params: Promise.resolve({ code: 'ZME9XXKQX1ZL8K' }),
      }),
    ).rejects.toThrow('NEXT_REDIRECT:https://zeromail.vn/api/r/ZME9XXKQX1ZL8K');

    expect(redirectMock).toHaveBeenCalledWith('https://zeromail.vn/api/r/ZME9XXKQX1ZL8K');
  });
});
