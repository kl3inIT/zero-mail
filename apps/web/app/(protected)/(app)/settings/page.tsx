import { cookies } from 'next/headers';

import type { CurrentUser } from '@/features/account/api/account-api';
import { getCurrentUserCached } from '@/features/account/api/account-api';

import { SettingsClient } from './SettingsClient';

/**
 * RSC entry — pre-fetches the current user on the server so the first paint
 * already shows email/locale/Gmail status without a hydration flash. The
 * client component still uses TanStack Query (via `useCurrentUser`) for
 * refetch + mutation invalidation; `initialUser` only seeds the cache.
 *
 * Server-side fetches are mocked by MSW under `instrumentation.ts` when
 * `NEXT_PUBLIC_E2E_MSW=1` (Playwright runs). Production hits the real backend.
 * The try/catch keeps the page renderable when the RSC fetch fails (backend
 * down, MSW intercept misses, etc.) — TanStack Query on the client picks up
 * the refetch and the page degrades to a hydration flash instead of a 500.
 */
export default async function SettingsPage() {
  const cookieStore = await cookies();
  let initialUser: CurrentUser | undefined;
  try {
    initialUser = await getCurrentUserCached(cookieStore.toString());
  } catch {
    initialUser = undefined;
  }
  return <SettingsClient initialUser={initialUser} />;
}
