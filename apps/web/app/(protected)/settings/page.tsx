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
import { PageShell } from '@/components/ui/PageShell';
import { SectionCard } from '@/components/ui/SectionCard';
import { Separator } from '@/components/ui/separator';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

/**
 * /settings (client). Plan 06 Task 1 — refactored onto Plan 04 primitives
 * (PageShell + 5 SectionCards) with token-aware Tailwind classes (CONTEXT
 * D-C2 / D-C3 / D-D5). Existing behaviour, hooks, copy, and click handlers
 * preserved verbatim; only composition + tokens change.
 *
 * UI-SPEC §Spacing locks `app` variant at `max-w-3xl` — narrower than the
 * historical `max-w-4xl`, but the design contract takes precedence over the
 * incidental width. Container padding flows from PageShell.
 *
 * D-D5 single-account note: Gmail card carries a footer note (i18n key
 * `settings.gmailConnection.singleAccountNote`) so users understand the v1
 * single-Gmail expectation without seeing a disabled "add another" stub
 * (D-B4 — no false-promise affordances).
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
    <PageShell variant="app">
      <SectionCard heading={t('settings.account.heading')}>
        <p>{me.data?.email ?? t('common.loading')}</p>
      </SectionCard>

      <SectionCard>
        <LanguageSwitcher currentLocale={preferredLanguage} authenticated={true} variant="row" />
      </SectionCard>

      <SectionCard
        heading={t('settings.gmailConnection.heading')}
        footer={
          <p className="text-muted-foreground text-sm">
            {t('settings.gmailConnection.singleAccountNote')}
          </p>
        }
      >
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
      </SectionCard>

      <SectionCard heading={t('settings.privacy.heading')}>
        <ul className="list-disc pl-5 text-sm">
          <li>{t('settings.privacy.noBodyStorage')}</li>
          <li>{t('settings.privacy.noAutoSend')}</li>
          <li>{t('settings.privacy.revokeAnytime')}</li>
          <li>{t('settings.privacy.byokPlanned')}</li>
        </ul>
      </SectionCard>

      <Separator />

      <SectionCard heading={t('settings.dangerZone.heading')} className="border-destructive/30">
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
      </SectionCard>
    </PageShell>
  );
}
