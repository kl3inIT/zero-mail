'use client';

import { useTranslations } from 'next-intl';

import { ConnectionHealthBadge } from '@/features/gmail/components/ConnectionHealthBadge';
import { DeleteAccountDialog } from '@/features/account/components/DeleteAccountDialog';
import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';
import { ReconnectPrompt } from '@/features/gmail/components/ReconnectPrompt';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import { useDisconnectGmail } from '@/features/gmail/hooks/useDisconnectGmail';
import { useDeleteAccount } from '@/features/account/hooks/useDeleteAccount';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

/**
 * /settings (client). Phase 01.5 Plan 02 — deflated from PageShell/SectionCard
 * to raw shadcn Card chains (D-C1, D-C2).
 *
 * Five sections: account, language, gmailConnection (with CardFooter single-
 * account-note per D-D5), privacy, dangerZone. Separator preserved. All hooks,
 * click handlers, and behavior unchanged.
 */
export default function SettingsPage() {
  const t = useTranslations();
  const me = useCurrentUser();
  const status = useTenantStatus();
  const disconnect = useDisconnectGmail();
  const del = useDeleteAccount();

  const connStatus = status.data?.connectionStatus ?? 'NOT_CONNECTED';
  const preferredLanguage = (me.data?.preferredLanguage === 'en' ? 'en' : 'vi') as AppLocale;
  const reconnect = () => {
    window.location.href = getApiUrl('/tenant/connect-gmail');
  };

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6">
      <Card>
        <CardHeader>
          <CardTitle>{t('settings.account.heading')}</CardTitle>
        </CardHeader>
        <CardContent>
          <p>{me.data?.email ?? t('common.loading')}</p>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <LanguageSwitcher currentLocale={preferredLanguage} authenticated={true} variant="row" />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('settings.gmailConnection.heading')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-3">
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
        </CardContent>
        <CardFooter>
          <p className="text-muted-foreground text-sm">
            {t('settings.gmailConnection.singleAccountNote')}
          </p>
        </CardFooter>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('settings.privacy.heading')}</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="list-disc pl-5 text-sm">
            <li>{t('settings.privacy.noBodyStorage')}</li>
            <li>{t('settings.privacy.noAutoSend')}</li>
            <li>{t('settings.privacy.revokeAnytime')}</li>
            <li>{t('settings.privacy.byokPlanned')}</li>
          </ul>
        </CardContent>
      </Card>

      <Separator />

      <Card className="border-destructive/30">
        <CardHeader>
          <CardTitle>{t('settings.dangerZone.heading')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-3">
            <Button variant="destructive" onClick={() => disconnect.mutate()}>
              {t('settings.gmailConnection.disconnectCta')}
            </Button>
            <DeleteAccountDialog
              isPending={del.isPending}
              onConfirm={async () => {
                await del.mutateAsync();
                window.location.href = '/login';
              }}
            />
          </div>
        </CardContent>
      </Card>
    </main>
  );
}
