import type { Route } from 'next';
import { redirect } from 'next/navigation';

import { getPublicApiUrl } from '@/lib/api/base-url';

export default async function PublicReferralRedirectPage({
  params,
}: {
  params: Promise<{ code: string }>;
}) {
  const { code } = await params;
  redirect(getPublicApiUrl(`/r/${encodeURIComponent(code)}` as `/${string}`) as Route);
}
