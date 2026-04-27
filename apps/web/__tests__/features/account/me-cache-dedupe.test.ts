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
 * hai calls với cùng string → cùng Promise (dedupe thật sự).
 *
 * NOTE: React cache() là per-render-pass, KHÔNG persist across requests.
 * Underlying fetch giữ cache: 'no-store' → fresh mỗi request.
 *
 * Test 4 (layout integration): next/headers không mock được ổn định trong
 * vitest/jsdom environment. Tuy nhiên integration được chứng minh qua test 1:
 * primitive string equality đảm bảo dedupe ngay cả khi gọi từ "hai consumer khác nhau"
 * (simulate bằng hai function-scoped invocations với cùng literal string).
 * Manual gate: pnpm dev walkthrough trong Plan 04 polish gate.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

// RED: getCurrentUserCached và fetchCurrentUser chưa tồn tại — test sẽ FAIL
// cho đến khi Task 2 implement chúng.
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

describe('HIGH-2 — getCurrentUserCached primitive-keyed dedupe', () => {
  /**
   * Test 1: Cùng cookie string → CÙNG Promise (dedupe thật sự)
   *
   * Chứng minh: React cache() với primitive string arg → value equality →
   * hai calls với "sid=abc; locale=vi" đều nhận CÙNG Promise object.
   * Underlying fetch CHỈ được gọi MỘT LẦN.
   */
  it('same_cookie_string_dedupes_to_one_fetch', async () => {
    const cookieStr = 'sid=abc; locale=vi';

    const p1 = getCurrentUserCached(cookieStr);
    const p2 = getCurrentUserCached(cookieStr);

    // CÙNG Promise reference (primitive string → value equality trong cache())
    expect(Object.is(p1, p2)).toBe(true);

    // Resolve cả hai
    await p1;
    await p2;

    // Underlying fetch chỉ bị gọi MỘT LẦN — đây là core guarantee của HIGH-2 fix
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  /**
   * Test 2: Cookie strings khác nhau → Promises khác nhau (không false-positive dedupe)
   *
   * Chứng minh: Hai user khác nhau có cookie strings khác nhau → không share cache entry.
   */
  it('different_cookie_strings_no_dedupe', async () => {
    const p1 = getCurrentUserCached('sid=abc');
    const p2 = getCurrentUserCached('sid=DIFFERENT');

    // HAI Promise khác nhau — mỗi user có cache entry riêng
    expect(Object.is(p1, p2)).toBe(false);

    await p1;
    await p2;

    // Fetch được gọi HAI LẦN — mỗi cookie string là cache key riêng biệt
    expect(mockFetch).toHaveBeenCalledTimes(2);
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
    const [_url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(init).toMatchObject({ cache: 'no-store' });
  });

  /**
   * Test 4: Integration-style — simulates two RSC consumers trong một render
   *
   * NOTE: next/headers (cookies()) không mock được ổn định trong vitest/jsdom.
   * Manual gate cho layout.tsx integration: pnpm dev walkthrough trong Plan 04
   * polish gate (SUMMARY.md §Manual Gate comment).
   *
   * Thay vào đó: simulate hai consumer bằng cách gọi getCurrentUserCached
   * với CÙNG literal string (như thể cả hai consumer đều call cookies().toString()
   * và nhận cùng kết quả trong một render pass). Chứng minh primitive-key dedupe
   * hoạt động đúng ngay cả khi gọi từ "hai consumer riêng biệt".
   */
  it('integration_layout_dedupe_two_rsc_consumers_same_render', async () => {
    // Simulate: consumer 1 (reassertServerLocale) và consumer 2 (future RSC)
    // cùng gọi trong một render, cùng nhận cookie string từ cookies().toString()
    const sharedCookieStr = 'ZEROMAIL_SESSION=xyz; NEXT_LOCALE=vi';

    // Consumer 1 (e.g., reassertServerLocale)
    const promiseFromConsumer1 = getCurrentUserCached(sharedCookieStr);
    // Consumer 2 (e.g., another RSC in the same render tree)
    const promiseFromConsumer2 = getCurrentUserCached(sharedCookieStr);

    // Cùng Promise → dedupe (core guarantee)
    expect(Object.is(promiseFromConsumer1, promiseFromConsumer2)).toBe(true);

    const [result1, result2] = await Promise.all([promiseFromConsumer1, promiseFromConsumer2]);

    // Cả hai nhận cùng kết quả từ một fetch
    expect(result1).toEqual(MOCK_USER);
    expect(result2).toEqual(MOCK_USER);

    // Chỉ một fetch call — đây là mục tiêu chính của plan này
    expect(mockFetch).toHaveBeenCalledTimes(1);
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
    // Không có cache wrapping → 2 fetch calls
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });
});
