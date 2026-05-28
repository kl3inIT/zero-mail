'use client';

import { useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import {
  Check,
  CreditCard,
  Inbox,
  Mail,
  Moon,
  Palette,
  ShieldCheck,
  Sun,
  TriangleAlert,
  UserCircle,
} from 'lucide-react';

import { Button, buttonVariants } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import type { CurrentUser } from '@/features/account/api/account-api';
import { DeleteAccountDialog } from '@/features/account/components/DeleteAccountDialog';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useDeleteAccount } from '@/features/account/hooks/useDeleteAccount';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';
import { ConnectionHealthBadge } from '@/features/gmail/components/ConnectionHealthBadge';
import { ReconnectPromptGate } from '@/features/gmail/components/ReconnectPrompt';
import { useDisconnectGmail } from '@/features/gmail/hooks/useDisconnectGmail';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import { ByokForm } from '@/features/llm/components/ByokForm';
import { NotificationsSection } from '@/features/notifications/components/NotificationsSection';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';
import { useTriagePauseState } from '@/features/triage/hooks/useTriagePauseState';
import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';
import type { AppLocale } from '@/i18n/routing';
import { getApiUrl } from '@/lib/api/base-url';
import { cn } from '@/lib/utils';
import Link from 'next/link';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';

function isGmailConnectionStatus(value: string | undefined): value is GmailConnectionStatus {
  return (
    value === 'CONNECTED' ||
    value === 'DISCONNECTED' ||
    value === 'NOT_CONNECTED' ||
    value === 'PENDING'
  );
}

function ThemeSwitcher({ currentTheme }: { currentTheme: 'light' | 'dark' }) {
  const t = useTranslations();
  return (
    <div
      className="border-border bg-muted/40 inline-flex w-full overflow-hidden rounded-lg border p-0.5"
      role="radiogroup"
      aria-label={t('settings.theme.heading')}
      data-testid="theme-switcher"
    >
      {(['light', 'dark'] as const).map((value) => {
        const active = currentTheme === value;
        const label = value === 'light' ? t('settings.theme.light') : t('settings.theme.dark');
        const Icon = value === 'light' ? Sun : Moon;
        return (
          <form key={value} action="/actions/theme" method="post" className="flex-1">
            <input type="hidden" name="theme" value={value} />
            <button
              type="submit"
              role="radio"
              aria-checked={active}
              data-active={active}
              className={cn(
                'flex w-full items-center justify-center gap-1.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                active
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              <Icon className="size-4" aria-hidden="true" />
              <span>{label}</span>
            </button>
          </form>
        );
      })}
    </div>
  );
}

function DisplayPreferencesCard({
  currentLocale,
  currentTheme,
}: {
  currentLocale: AppLocale;
  currentTheme: 'light' | 'dark';
}) {
  const t = useTranslations();
  const [open, setOpen] = useState(false);

  const localeLabel =
    currentLocale === 'vi' ? t('settings.language.vi') : t('settings.language.en');
  const themeLabel =
    currentTheme === 'light' ? t('settings.theme.light') : t('settings.theme.dark');

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2">
          <Palette className="text-muted-foreground size-4" aria-hidden="true" />
          {t('settings.display.heading')}
        </CardTitle>
        <CardDescription>{t('settings.display.helper')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="text-muted-foreground flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <span>
            {t('settings.language.label')}:{' '}
            <span className="text-foreground font-medium">{localeLabel}</span>
          </span>
          <span aria-hidden="true">·</span>
          <span>
            {t('settings.theme.heading')}:{' '}
            <span className="text-foreground font-medium">{themeLabel}</span>
          </span>
        </div>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger
            render={
              <Button variant="outline" size="sm" className="w-full sm:w-auto">
                {t('settings.display.openDialog')}
              </Button>
            }
          />
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t('settings.display.heading')}</DialogTitle>
              <DialogDescription>{t('settings.display.dialogBody')}</DialogDescription>
            </DialogHeader>

            <div className="space-y-5 pt-2">
              <div className="space-y-2">
                <p className="text-sm font-semibold">{t('settings.language.label')}</p>
                <LanguageSwitcher
                  currentLocale={currentLocale}
                  authenticated={true}
                  variant="compact"
                  onSettled={() => setOpen(false)}
                />
              </div>

              <Separator />

              <div className="space-y-2">
                <p className="text-sm font-semibold">{t('settings.theme.heading')}</p>
                <ThemeSwitcher currentTheme={currentTheme} />
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </CardContent>
    </Card>
  );
}

function CreditCardBlock() {
  const t = useTranslations();
  const locale = useLocale();
  const balance = useBillingBalance();
  const formatted = useMemo(() => {
    const available = balance.data?.availableCredits ?? 0;
    return new Intl.NumberFormat(locale === 'vi' ? 'vi-VN' : 'en-US').format(available);
  }, [balance.data?.availableCredits, locale]);

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2">
          <CreditCard className="text-muted-foreground size-4" aria-hidden="true" />
          {t('shell.balance.label')}
        </CardTitle>
        <CardDescription>{t('settings.credit.helper')}</CardDescription>
      </CardHeader>
      <CardContent>
        {balance.isPending ? (
          <Skeleton className="h-9 w-24" />
        ) : (
          <span className="text-foreground font-mono text-3xl font-bold tabular-nums">
            {formatted}
          </span>
        )}
      </CardContent>
    </Card>
  );
}

export function SettingsClient({
  initialUser,
  initialTheme = 'light',
}: {
  initialUser?: CurrentUser;
  initialTheme?: 'light' | 'dark';
} = {}) {
  const t = useTranslations();
  const me = useCurrentUser(initialUser);
  const status = useTenantStatus();
  const disconnect = useDisconnectGmail();
  const del = useDeleteAccount();
  const pauseState = useTriagePauseState();
  const togglePause = useToggleTriagePause();

  const gmailConnection = me.data?.gmailConnectionStatus;
  const tenantConnectionStatus = status.data?.connectionStatus;
  const connStatus = isGmailConnectionStatus(gmailConnection?.status)
    ? gmailConnection.status
    : isGmailConnectionStatus(tenantConnectionStatus)
      ? tenantConnectionStatus
      : 'NOT_CONNECTED';
  const ingestionHealth = gmailConnection?.ingestionHealth ?? 'HEALTHY';
  const triagePaused = pauseState.data ?? false;
  const preferredLanguage = (me.data?.preferredLanguage === 'en' ? 'en' : 'vi') as AppLocale;
  const reconnect = () => {
    window.location.href = getApiUrl('/api/tenant/connect-gmail');
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 space-y-4 overflow-auto p-3 sm:p-4">
        {/* Row 1: Account + Credit + Display preferences */}
        <div className="grid gap-4 md:grid-cols-3">
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
                <p className="text-foreground truncate text-sm">
                  {me.data?.email ?? t('common.loading')}
                </p>
              </div>
            </CardContent>
          </Card>

          <CreditCardBlock />

          <DisplayPreferencesCard currentLocale={preferredLanguage} currentTheme={initialTheme} />
        </div>

        {/* Row 2: Gmail connection + triage controls */}
        <div className="grid gap-4 md:grid-cols-2">
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
                    window.location.href = getApiUrl('/api/tenant/connect-gmail');
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
                <span>{t('settings.privacy.outboundControl')}</span>
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
              <Button
                variant="destructive"
                disabled={disconnect.isPending}
                onClick={() => disconnect.mutate()}
              >
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
    </div>
  );
}
