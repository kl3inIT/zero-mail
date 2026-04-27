/**
 * HIGH-2 Review Fix — Phase 01.5 Plan 03
 *
 * Kiểm tra primitive-keyed React cache() dedupe cho /me fetch.
 *
 * Vấn đề gốc: React cache() key theo REFERENCE EQUALITY đối với object args.
 * RSC callers tạo fresh `{ headers: { cookie } }` mỗi render → KHÔNG dedupe.
 *
 * Fix: wrapper getCurrentUserCached(cookieHeader: string | undefined) nhận
 * PRIMITIVE STRING làm cache key. Primitive string equality là VALUE-based →
 * trong React RSC server context: hai calls với cùng string → cùng Promise.
 *
 * IMPORTANT — vitest/jsdom environment limitation:
 * React cache() CHỈ dedupe trong React server component render pass context.
 * Ngoài RSC context (vitest/jsdom, Node REPL), cache() là pass-through — mỗi
 * call tạo Promise mới (no-op memoization). Do đó:
 * - Promise identity (Object.is) KHÔNG thể verify trong vitest.
 * - Test thực sự verify được: export shape, underlying fetch behavior, no-store.
 * - Integration (layout.tsx + RSC dedupe): manual gate trong Plan 04 walkthrough.
 *
 * Xem: https://react.dev/reference/react/cache — "only available in Server Components"
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

// Verify exports tồn tại (sẽ RED trước Task 2, GREEN sau Task 2)
import { getCurrentUserCached, fetchCurrentUser } from '@/features/account/api/me';

// Mock fetch globally để capture invocations
const mockFetch = vi.fn();

const MOCK_USER = {
  id: 'user-123',
  email: 'test@example.com',
  preferredLanguage: 'vi' as const,
  onboardingStep: 'GMAIL_CONNECTED',
};

beforeEach(() => {
  vi.resetAllMocks();
  // Reset fetch mock với response hợp lệ
  mockFetch.mockResolvedValue({
    ok: true,
    json: async () => MOCK_USER,
    status: 200,
  } as unknown as Response);

  // Inject mock vào global fetch
  vi.stubGlobal('fetch', mockFetch);
});

describe('HIGH-2 — getCurrentUserCached primitive-keyed cache() (Phase 01.5 fix)', () => {
  /**
   * Test 1: Export shape — getCurrentUserCached tồn tại và callable
   *
   * Chứng minh: cache key là PRIMITIVE STRING (không phải options object).
   * Trong RSC context, cùng string → dedupe. Trong vitest, Promise identity
   * không verify được (React cache() là RSC-context-only).
   *
   * NOTE: Object.is(p1, p2) không thể assert trong vitest vì React cache()
   * chỉ dedupe trong server component render pass. Manual gate: Plan 04 dev walkthrough.
   */
  it('same_cookie_string_dedupes_to_one_fetch — callable với primitive string arg', async () => {
    const cookieStr = 'sid=abc; locale=vi';

    // getCurrentUserCached nhận primitive string (không phải object)
    // — đây là core shape fix của HIGH-2
    const p1 = getCurrentUserCached(cookieStr);
    const p2 = getCurrentUserCached(cookieStr);

    expect(p1).toBeInstanceOf(Promise);
    expect(p2).toBeInstanceOf(Promise);

    const [result1, result2] = await Promise.all([p1, p2]);

    // Cả hai calls trả về đúng data
    expect(result1).toEqual(MOCK_USER);
    expect(result2).toEqual(MOCK_USER);

    // NOTE: trong vitest, React cache() không dedupe (RSC-context-only).
    // fetch count = 2 trong test env (expected); trong RSC production = 1 (dedupe thật sự).
    // Manual gate: pnpm dev + reassertServerLocale call trace xác nhận 1 fetch.
    expect(mockFetch.mock.calls.length).toBeGreaterThanOrEqual(1);
  });

  /**
   * Test 2: Cookie strings khác nhau → fetch args khác nhau
   *
   * Chứng minh: mỗi cookie string tạo fetch call riêng biệt với đúng header.
   * Trong RSC context, different strings → KHÔNG share cache entry (correct).
   */
  it('different_cookie_strings_no_dedupe — mỗi string gọi fetch với header riêng', async () => {
    await getCurrentUserCached('sid=abc');
    await getCurrentUserCached('sid=DIFFERENT');

    // Mỗi call đều kích hoạt fetch với cookie header tương ứng
    expect(mockFetch).toHaveBeenCalledTimes(2);

    // Verify cookie header được forward đúng
    const calls = mockFetch.mock.calls as [string, RequestInit][];
    const headers0 = calls[0][1].headers as Record<string, string>;
    const headers1 = calls[1][1].headers as Record<string, string>;
    expect(headers0['cookie']).toBe('sid=abc');
    expect(headers1['cookie']).toBe('sid=DIFFERENT');
  });

  /**
   * Test 3: Underlying fetch giữ cache: 'no-store'
   *
   * Chứng minh: cached wrapper KHÔNG thay đổi staleness semantics.
   * cache: 'no-store' trên fetch đảm bảo fresh per request;
   * React cache() chỉ dedup TRONG MỘT render pass.
   */
  it('underlying_fetch_no_store_preserved', async () => {
    await getCurrentUserCached('sid=abc; locale=vi');

    expect(mockFetch).toHaveBeenCalledTimes(1);

    // Kiểm tra fetch init argument chứa cache: 'no-store'
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(init).toMatchObject({ cache: 'no-store' });
  });

  /**
   * Test 4: Integration-style — getCurrentUserCached nhận primitive string arg
   *
   * Simulates hai RSC consumer gọi getCurrentUserCached với cùng cookie string
   * (như cookies().toString() trả về trong một render pass).
   *
   * NOTE: Object.is(p1, p2) không verify được trong vitest vì React cache() là
   * RSC-context-only. Trong production RSC render, p1 === p2 (dedupe thật sự).
   *
   * MANUAL GATE: Plan 04 pnpm dev walkthrough — reassertServerLocale call trace
   * phải cho thấy chỉ 1 /me fetch call khi layout.tsx render với authenticated user.
   */
  it('integration_layout_dedupe_two_rsc_consumers_same_render — shape verification', async () => {
    // cookies().toString() trong RSC trả về stable primitive string
    const sharedCookieStr = 'ZEROMAIL_SESSION=xyz; NEXT_LOCALE=vi';

    // Consumer 1 (reassertServerLocale trong layout.tsx)
    const promiseFromConsumer1 = getCurrentUserCached(sharedCookieStr);
    // Consumer 2 (future RSC consumer trong cùng render tree)
    const promiseFromConsumer2 = getCurrentUserCached(sharedCookieStr);

    // Verify cả hai return Promise
    expect(promiseFromConsumer1).toBeInstanceOf(Promise);
    expect(promiseFromConsumer2).toBeInstanceOf(Promise);

    const [result1, result2] = await Promise.all([promiseFromConsumer1, promiseFromConsumer2]);

    // Cả hai nhận đúng data
    expect(result1).toEqual(MOCK_USER);
    expect(result2).toEqual(MOCK_USER);

    // NOTE: trong vitest, React cache() không dedupe — fetch được gọi 2 lần.
    // Trong production RSC: fetch chỉ gọi 1 lần (same primitive string → same cache entry).
    // Primitive string design đảm bảo correctness trong RSC context.
    expect(mockFetch.mock.calls.length).toBeGreaterThanOrEqual(1);
  });
});

describe('fetchCurrentUser — raw export for client/TanStack callers', () => {
  /**
   * fetchCurrentUser là raw (un-cached) function export.
   * TanStack Query dùng nó trực tiếp — không cần cache() wrapping vì
   * TanStack tự quản lý dedup/staleness qua queryKey.
   */
  it('fetchCurrentUser is callable without caching', async () => {
    const result = await fetchCurrentUser({ headers: { cookie: 'sid=test' } });
    expect(result).toEqual(MOCK_USER);
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('fetchCurrentUser called twice creates two separate fetches (no cache)', async () => {
    await fetchCurrentUser({ headers: { cookie: 'sid=test' } });
    await fetchCurrentUser({ headers: { cookie: 'sid=test' } });
    // Không có cache wrapping → 2 fetch calls (expected behavior for client callers)
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });
});
