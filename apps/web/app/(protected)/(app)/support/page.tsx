import { cookies } from 'next/headers';
import { getTranslations } from 'next-intl/server';

import type { CurrentUser } from '@/features/account/api/account-api';
import { getCurrentUserCached } from '@/features/account/api/account-api';

import { SupportClient } from './SupportClient';

export async function generateMetadata() {
  const t = await getTranslations('support');
  return { title: t('pageTitle') };
}

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
