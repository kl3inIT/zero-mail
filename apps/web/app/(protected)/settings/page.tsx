'use client';

import { useTranslations } from 'next-intl';

import { ConnectionHealthBadge } from '@/features/gmail/components/ConnectionHealthBadge';
import { DeleteAccountDialog } from '@/features/account/components/DeleteAccountDialog';
import { LanguageSwitcher } from '@/features/i18n/components/LanguageSwitcher';
import { ReconnectPrompt } from '@/features/gmail/components/ReconnectPrompt';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import { useDisconnectGmail } from '@/features/gmail/hooks/useDisconnectGmail';
import { useDeleteAccount } from '@/features/account/hooks/useDeleteAccount';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

/**
 * /settings (client). Plan 04 Task 3 — endpoint-specific calls fully moved into
 * features/<name>/api + hooks (REVIEWS Revision 1, Codex HIGH #1). All
 * inline api.GET/POST/DELETE removed; UI/JSX/copy preserved.
 */
export default function SettingsPage() {
  const t = useTranslations();
  const me = useCurrentUser();
  const status = useTenantStatus();
  const disconnect = useDisconnectGmail();
  const del = useDeleteAccount();

  const connStatus = status.data?.connectionStatus ?? 'NOT_CONNECTED';
  // Source of truth for the switcher: /me.preferredLanguage. Cookie is the
  // optimistic channel that LanguageSwitcher writes; the server overwrites it
  // on first authenticated SSR (see app/layout.tsx).
  const preferredLanguage = (me.data?.preferredLanguage === 'en' ? 'en' : 'vi') as AppLocale;
  const reconnect = () => {
    window.location.href = getApiUrl('/tenant/connect-gmail');
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
          <form method="post" action={getApiUrl('/tenant/connect-gmail')} className="mt-3">
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
              window.location.href = '/login';
            }}
          />
        </div>
      </Card>
    </main>
  );
}
