import { cookies } from 'next/headers';

import { getCurrentUserCached } from '@/features/account/api/account-api';

import { SettingsClient } from './SettingsClient';

// RSC entry point: fetches the user once on the server (`cache()` dedupes
// across the render tree) and hands it to the client island as `initialData`.
// The remaining queries (tenant status, pause state, etc.) stay client-side —
// they update via mutations and refetch on focus, so server prefetch is low ROI.
export default async function SettingsPage() {
  const cookieStore = await cookies();
  const user = await getCurrentUserCached(cookieStore.toString());
  return <SettingsClient initialUser={user} />;
}
