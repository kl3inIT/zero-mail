'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { Check, Globe, Inbox, Mail, ShieldCheck, TriangleAlert, UserCircle } from 'lucide-react';

import { ConnectionHealthBadge } from '@/features/gmail/components/ConnectionHealthBadge';
import { DeleteAccountDialog } from '@/features/account/components/DeleteAccountDialog';
import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';
import { ReconnectPromptGate } from '@/features/gmail/components/ReconnectPrompt';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import { useDisconnectGmail } from '@/features/gmail/hooks/useDisconnectGmail';
import { useDeleteAccount } from '@/features/account/hooks/useDeleteAccount';
import { ByokForm } from '@/features/llm/components/ByokForm';
import { NotificationsSection } from '@/features/notifications/components/NotificationsSection';
import { useTriagePauseState } from '@/features/triage/hooks/useTriagePauseState';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';
import { Button, buttonVariants } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';

function isGmailConnectionStatus(value: string | undefined): value is GmailConnectionStatus {
  return (
    value === 'CONNECTED' ||
    value === 'DISCONNECTED' ||
    value === 'NOT_CONNECTED' ||
    value === 'PENDING'
  );
}

export default function SettingsPage() {
  const t = useTranslations();
  const me = useCurrentUser();
  const status = useTenantStatus();
  const disconnect = useDisconnectGmail();
  const del = useDeleteAccount();
  const pauseState = useTriagePauseState();
  const togglePause = useToggleTriagePause();

  const gmailConnection = me.data?.gmailConnectionStatus;
  const connStatus = isGmailConnectionStatus(gmailConnection?.status)
    ? gmailConnection.status
    : (status.data?.connectionStatus ?? 'NOT_CONNECTED');
  const ingestionHealth = gmailConnection?.ingestionHealth ?? 'HEALTHY';
  const triagePaused = pauseState.data ?? false;
  const preferredLanguage = (me.data?.preferredLanguage === 'en' ? 'en' : 'vi') as AppLocale;
  const reconnect = () => {
    window.location.href = getApiUrl('/tenant/connect-gmail');
  };

  return (
    <div className="mx-auto w-full max-w-6xl space-y-4 p-4 md:p-6">
      {/* Row 1: Account + Language */}
      <div className="grid gap-4 md:grid-cols-2">
        {/* Account */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <UserCircle className="text-muted-foreground size-4" aria-hidden="true" />
              {t('settings.account.heading')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-3">
              <div className="bg-primary/10 text-primary flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold">
                {me.data?.email?.[0]?.toUpperCase() ?? '?'}
              </div>
              <p className="text-foreground text-sm">{me.data?.email ?? t('common.loading')}</p>
            </div>
          </CardContent>
        </Card>

        {/* Language */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2">
              <Globe className="text-muted-foreground size-4" aria-hidden="true" />
              {t('settings.language.label')}
            </CardTitle>
            <CardDescription>{t('settings.language.helper')}</CardDescription>
          </CardHeader>
          <CardContent>
            <LanguageSwitcher
              currentLocale={preferredLanguage}
              authenticated={true}
              variant="row"
            />
          </CardContent>
        </Card>
      </div>

      {/* Row 2: Gmail connection + Automated triage */}
      <div className="grid gap-4 md:grid-cols-2">
        {/* Gmail connection */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Mail className="text-muted-foreground size-4" aria-hidden="true" />
              {t('settings.gmailConnection.heading')}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center gap-3">
              <ConnectionHealthBadge status={connStatus} />
            </div>
            <ReconnectPromptGate
              status={connStatus}
              ingestionHealth={ingestionHealth}
              onReconnect={reconnect}
            />
            {connStatus === 'NOT_CONNECTED' && (
              <Button
                onClick={() => {
                  window.location.href = getApiUrl('/tenant/connect-gmail');
                }}
              >
                {t('onboarding.connect.cta')}
              </Button>
            )}
          </CardContent>
          <CardFooter>
            <p className="text-muted-foreground text-xs">
              {t('settings.gmailConnection.singleAccountNote')}
            </p>
          </CardFooter>
        </Card>

        {/* Automated triage */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Inbox className="text-muted-foreground size-4" aria-hidden="true" />
              {t('settings.triage.pause.title')}
            </CardTitle>
            <CardDescription>{t('settings.triage.pause.body')}</CardDescription>
          </CardHeader>
          <CardContent className="flex items-center justify-between gap-4">
            <span className="text-foreground text-sm font-medium">
              {t('settings.triage.pause.toggleLabel')}
            </span>
            <Switch
              checked={!triagePaused}
              aria-label={t('settings.triage.pause.toggleLabel')}
              disabled={pauseState.isLoading || togglePause.isPending}
              onCheckedChange={(running) => togglePause.mutate(!running)}
              className="data-unchecked:bg-warning/80"
              data-testid="settings-pause-switch"
            />
          </CardContent>
        </Card>
      </div>

      <NotificationsSection />

      <ByokForm />

      {/* Privacy */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="text-muted-foreground size-4" aria-hidden="true" />
            {t('settings.privacy.heading')}
          </CardTitle>
          <CardDescription>{t('privacy.settingsLink.body')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <ul className="text-muted-foreground space-y-2 text-sm">
            <li className="flex items-start gap-2">
              <Check className="text-primary mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              <span>{t('settings.privacy.noBodyStorage')}</span>
            </li>
            <li className="flex items-start gap-2">
              <Check className="text-primary mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              <span>{t('settings.privacy.noAutoSend')}</span>
            </li>
            <li className="flex items-start gap-2">
              <Check className="text-primary mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              <span>{t('settings.privacy.revokeAnytime')}</span>
            </li>
          </ul>
          <Link
            href="/settings/privacy"
            className={buttonVariants({ variant: 'outline', className: 'w-full sm:w-auto' })}
          >
            {t('privacy.settingsLink.cta')}
          </Link>
        </CardContent>
      </Card>

      <Separator />

      {/* Danger zone */}
      <Card className="border-destructive/40 bg-destructive/5">
        <CardHeader>
          <CardTitle className="text-destructive flex items-center gap-2">
            <TriangleAlert className="size-4" aria-hidden="true" />
            {t('settings.dangerZone.heading')}
          </CardTitle>
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
    </div>
  );
}
