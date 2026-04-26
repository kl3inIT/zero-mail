'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { ConnectionHealthBadge } from '@/features/gmail/components/ConnectionHealthBadge';
import { DeleteAccountDialog } from '@/features/account/components/DeleteAccountDialog';
import { LanguageSwitcher } from '@/features/i18n/components/LanguageSwitcher';
import { ReconnectPrompt } from '@/features/gmail/components/ReconnectPrompt';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { api, xsrfHeader } from '@/lib/api/client';

import type { AppLocale } from '@/i18n/routing';

type MeData = { email?: string; preferredLanguage?: string };
type StatusData = { connectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING' };

/**
 * /settings (client). Plan 06 Task 2 — every prose literal flows through
 * `useTranslations`. The explicit "Language" row at the top mounts the
 * full LanguageSwitcher (variant="row") per UI-SPEC §"Language Switcher
 * Contract"; preferredLanguage flows from /me (TanStack Query) so that the
 * server-side preference is the source of truth on first authenticated SSR.
 */
export default function SettingsPage() {
  const t = useTranslations();
  const qc = useQueryClient();
  const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? '';
  const me = useQuery<MeData | undefined>({
    queryKey: ['me'],
    queryFn: async () => (await api.GET('/me', {})).data as MeData | undefined,
  });
  const status = useQuery<StatusData | undefined>({
    queryKey: ['status'],
    queryFn: async () => (await api.GET('/tenant/status', {})).data as StatusData | undefined,
  });
  const disconnect = useMutation({
    mutationFn: async () =>
      (
        await api.POST('/tenant/disconnect', {
          headers: xsrfHeader(),
        })
      ).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['status'] }),
  });
  const del = useMutation({
    mutationFn: async () =>
      (
        await api.DELETE('/me/account', {
          headers: xsrfHeader(),
        })
      ).data,
    onSuccess: () => {
      window.location.href = '/login';
    },
  });

  const connStatus = status.data?.connectionStatus ?? 'NOT_CONNECTED';
  // Source of truth for the switcher: /me.preferredLanguage. Cookie is the
  // optimistic channel that LanguageSwitcher writes; the server overwrites it
  // on first authenticated SSR (see app/layout.tsx).
  const preferredLanguage = (me.data?.preferredLanguage === 'en' ? 'en' : 'vi') as AppLocale;
  const reconnect = () => {
    window.location.href = `${apiBase}/tenant/connect-gmail`;
  };

  return (
    <main className="mx-auto max-w-4xl space-y-6 p-6">
      <Card className="p-6">
        <h2 className="text-xl font-semibold">{t('settings.account.heading')}</h2>
        <p className="mt-2">{me.data?.email ?? t('common.loading')}</p>
      </Card>

      <Card className="p-6">
        <LanguageSwitcher currentLocale={preferredLanguage} authenticated={true} variant="row" />
      </Card>

      <Card className="p-6">
        <h2 className="text-xl font-semibold">{t('settings.gmailConnection.heading')}</h2>
        <div className="mt-3 flex items-center gap-3">
          <ConnectionHealthBadge status={connStatus} />
        </div>
        {connStatus === 'DISCONNECTED' && (
          <div className="mt-3">
            <ReconnectPrompt onReconnect={reconnect} />
          </div>
        )}
        {connStatus === 'NOT_CONNECTED' && (
          <form method="post" action={`${apiBase}/tenant/connect-gmail`} className="mt-3">
            <Button type="submit">{t('onboarding.connect.cta')}</Button>
          </form>
        )}
      </Card>

      <Card className="p-6">
        <h2 className="text-xl font-semibold">{t('settings.privacy.heading')}</h2>
        <ul className="mt-3 list-disc pl-5 text-sm">
          <li>{t('settings.privacy.noBodyStorage')}</li>
          <li>{t('settings.privacy.noAutoSend')}</li>
          <li>{t('settings.privacy.revokeAnytime')}</li>
          <li>{t('settings.privacy.byokPlanned')}</li>
        </ul>
      </Card>

      <Separator />

      <Card className="border-red-200 p-6">
        <h2 className="text-xl font-semibold">{t('settings.dangerZone.heading')}</h2>
        <div className="mt-4 flex gap-3">
          <Button variant="destructive" onClick={() => disconnect.mutate()}>
            {t('settings.gmailConnection.disconnectCta')}
          </Button>
          <DeleteAccountDialog
            onConfirm={async () => {
              await del.mutateAsync();
            }}
          />
        </div>
      </Card>
    </main>
  );
}
