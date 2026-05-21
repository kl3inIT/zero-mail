import { cookies } from 'next/headers';

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
 */
export default async function SettingsPage() {
  const cookieStore = await cookies();
  const user = await getCurrentUserCached(cookieStore.toString());
  return <SettingsClient initialUser={user} />;
}
