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
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

/**
 * /settings (client). Phase 01.5 Plan 02 — deflated from PageShell/SectionCard
 * to raw shadcn Card chains (D-C1, D-C2).
 * Phase 01.5 Plan 04 — visual polish via frontend-design skill (D-D1).
 *
 * Design intent (Plan 04):
 *  - Section rhythm: max-w-2xl (tighter than max-w-3xl for form-heavy pages),
 *    space-y-4 between cards (tighter than space-y-6 — settings feel dense, not airy).
 *  - Language card: added CardHeader + CardTitle for consistent section heading
 *    hierarchy with other sections.
 *  - gmailConnection CardFooter: text-xs (down from text-sm) to subordinate the
 *    single-account note clearly below the connection status content.
 *  - Privacy section: CardDescription for intro line, list remains as items.
 *  - dangerZone: border-destructive/40 (up from /30) — clearer visual separation
 *    without using a filled background. Separator above provides gap.
 *  - Danger zone actions: flex-wrap gap-3, kept as-is per existing behavior.
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
    <main className="mx-auto max-w-2xl space-y-4 p-6">
      {/* Account */}
      <Card>
        <CardHeader>
          <CardTitle>{t('settings.account.heading')}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-foreground text-sm">{me.data?.email ?? t('common.loading')}</p>
        </CardContent>
      </Card>

      {/* Language */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle>{t('settings.language.label')}</CardTitle>
          <CardDescription>{t('settings.language.helper')}</CardDescription>
        </CardHeader>
        <CardContent>
          <LanguageSwitcher currentLocale={preferredLanguage} authenticated={true} variant="row" />
        </CardContent>
      </Card>

      {/* Gmail connection */}
      <Card>
        <CardHeader>
          <CardTitle>{t('settings.gmailConnection.heading')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-3">
            <ConnectionHealthBadge status={connStatus} />
          </div>
          {connStatus === 'DISCONNECTED' && <ReconnectPrompt onReconnect={reconnect} />}
          {connStatus === 'NOT_CONNECTED' && (
            <form method="post" action={getApiUrl('/tenant/connect-gmail')}>
              <Button type="submit">{t('onboarding.connect.cta')}</Button>
            </form>
          )}
        </CardContent>
        <CardFooter>
          <p className="text-muted-foreground text-xs">
            {t('settings.gmailConnection.singleAccountNote')}
          </p>
        </CardFooter>
      </Card>

      {/* Privacy */}
      <Card>
        <CardHeader>
          <CardTitle>{t('settings.privacy.heading')}</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="text-muted-foreground space-y-1.5 text-sm">
            <li>{t('settings.privacy.noBodyStorage')}</li>
            <li>{t('settings.privacy.noAutoSend')}</li>
            <li>{t('settings.privacy.revokeAnytime')}</li>
            <li>{t('settings.privacy.byokPlanned')}</li>
          </ul>
        </CardContent>
      </Card>

      <Separator />

      {/* Danger zone */}
      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="text-destructive">{t('settings.dangerZone.heading')}</CardTitle>
          <CardDescription>{t('deleteAccount.body')}</CardDescription>
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
