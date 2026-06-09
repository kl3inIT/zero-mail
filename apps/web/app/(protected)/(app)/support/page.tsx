import { cookies } from 'next/headers';

import type { CurrentUser } from '@/features/account/api/account-api';
import { getCurrentUserCached } from '@/features/account/api/account-api';

import { SupportClient } from './SupportClient';

export const metadata = { title: 'Help & Feedback' };

export default async function SupportPage() {
  const cookieStore = await cookies();
  let initialUser: CurrentUser | undefined;
  try {
    initialUser = await getCurrentUserCached(cookieStore.toString());
  } catch {
    initialUser = undefined;
  }
  return <SupportClient initialUser={initialUser} />;
}
