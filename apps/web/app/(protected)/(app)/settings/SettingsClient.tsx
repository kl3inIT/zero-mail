'use client';

import { useTranslations } from 'next-intl';
import { Languages, Mail, Moon, Palette, Sun, TriangleAlert, UserCircle } from 'lucide-react';
import { toast } from 'sonner';

import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import type { CurrentUser } from '@/features/account/api/account-api';
import { DeleteAccountDialog } from '@/features/account/components/DeleteAccountDialog';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useDeleteAccount } from '@/features/account/hooks/useDeleteAccount';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';
import { ConnectionHealthBadge } from '@/features/gmail/components/ConnectionHealthBadge';
import { ReconnectPromptGate } from '@/features/gmail/components/ReconnectPrompt';
import { useDisconnectGmail } from '@/features/gmail/hooks/useDisconnectGmail';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import { getConnectMailboxUrl, getReconnectMailboxUrl } from '@/features/mailbox/api/mailbox-api';
import { useActiveMailbox } from '@/features/mailbox/hooks/useActiveMailbox';
import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';
import type { AppLocale } from '@/i18n/routing';
import { cn } from '@/lib/utils';

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
      className="border-border bg-muted/40 inline-flex overflow-hidden rounded-lg border p-0.5"
      role="radiogroup"
      aria-label={t('settings.theme.heading')}
      data-testid="theme-switcher"
    >
      {(['light', 'dark'] as const).map((value) => {
        const active = currentTheme === value;
        const label = value === 'light' ? t('settings.theme.light') : t('settings.theme.dark');
        const Icon = value === 'light' ? Sun : Moon;
        return (
          <form key={value} action="/actions/theme" method="post">
            <input type="hidden" name="theme" value={value} />
            <button
              type="submit"
              role="radio"
              aria-checked={active}
              data-active={active}
              className={cn(
                'flex items-center justify-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
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
  const activeMailbox = useActiveMailbox();
  const disconnect = useDisconnectGmail();
  const del = useDeleteAccount();

  const gmailConnection = me.data?.gmailConnectionStatus;
  const tenantConnectionStatus = status.data?.connectionStatus;
  const connStatus = isGmailConnectionStatus(gmailConnection?.status)
    ? gmailConnection.status
    : isGmailConnectionStatus(tenantConnectionStatus)
      ? tenantConnectionStatus
      : 'NOT_CONNECTED';
  const ingestionHealth = gmailConnection?.ingestionHealth ?? 'HEALTHY';
  const preferredLanguage = (me.data?.preferredLanguage === 'en' ? 'en' : 'vi') as AppLocale;
  const accountEmail = me.data?.email ?? '';
  // Google profile name/picture come straight from the OAuth session (never stored);
  // fall back to the email local-part + a letter tile when absent.
  const accountDisplayName =
    me.data?.displayName?.trim() || (accountEmail ? accountEmail.split('@')[0] : '');
  const accountAvatarUrl = me.data?.avatarUrl ?? undefined;
  const accountInitial = (accountDisplayName || accountEmail || '?').charAt(0).toUpperCase();
  const activeMailboxId = activeMailbox.data?.gmailConnectionId ?? null;
  const reconnect = () => {
    window.location.href = activeMailboxId
      ? getReconnectMailboxUrl(activeMailboxId)
      : getConnectMailboxUrl();
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-6">
        <div className="mx-auto w-full max-w-3xl space-y-8">
          <header className="space-y-1">
            <h1 className="text-foreground text-2xl font-semibold tracking-normal">
              {t('settings.title')}
            </h1>
            <p className="text-muted-foreground text-sm">{t('settings.description')}</p>
          </header>

          {/* Tài khoản */}
          <section className="space-y-4" aria-labelledby="settings-section-account">
            <SectionHeader
              id="settings-section-account"
              title={t('settings.account.heading')}
              icon={UserCircle}
            />
            <SettingCard className="py-3" contentClassName="py-0">
              <div className="flex items-center gap-3">
                <Avatar className="size-10">
                  {accountAvatarUrl ? (
                    <AvatarImage src={accountAvatarUrl} alt="" referrerPolicy="no-referrer" />
                  ) : null}
                  <AvatarFallback className="bg-primary/10 text-primary text-sm font-semibold">
                    {accountInitial}
                  </AvatarFallback>
                </Avatar>
                <div className="min-w-0">
                  {accountDisplayName ? (
                    <p className="text-foreground truncate text-sm font-medium">
                      {accountDisplayName}
                    </p>
                  ) : null}
                  <p className="text-muted-foreground truncate text-sm">
                    {accountEmail || t('common.loading')}
                  </p>
                </div>
              </div>
            </SettingCard>
          </section>

          {/* Hiển thị */}
          <section className="space-y-4" aria-labelledby="settings-section-display">
            <SectionHeader
              id="settings-section-display"
              title={t('settings.display.heading')}
              helperText={t('settings.display.helper')}
              icon={Palette}
            />
            <div className="space-y-3">
              <SettingCard
                // overflow-visible: the language popup is absolutely positioned and
                // the Card primitive clips with overflow-hidden, which otherwise
                // shears the open dropdown to a thin sliver inside the card.
                className="overflow-visible"
                title={t('settings.language.label')}
                icon={Languages}
                rightSlot={
                  <LanguageSwitcher
                    currentLocale={preferredLanguage}
                    authenticated={true}
                    variant="compact"
                  />
                }
              />
              <SettingCard
                title={t('settings.theme.heading')}
                icon={initialTheme === 'light' ? Sun : Moon}
                rightSlot={<ThemeSwitcher currentTheme={initialTheme} />}
              />
            </div>
          </section>

          {/* Kết nối Gmail */}
          <section className="space-y-4" aria-labelledby="settings-section-gmail">
            <SectionHeader
              id="settings-section-gmail"
              title={t('settings.gmailConnection.heading')}
              helperText={t('settings.gmailConnection.singleAccountNote')}
              icon={Mail}
            />
            <SettingCard className="py-3" contentClassName="py-0">
              <div className="space-y-3">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="text-foreground truncate text-sm font-medium">
                      {gmailConnection?.googleEmail || accountEmail || t('common.loading')}
                    </p>
                  </div>
                  <ConnectionHealthBadge
                    status={connStatus}
                    className="w-fit shrink-0 self-start sm:self-auto"
                  />
                </div>
                <ReconnectPromptGate
                  status={connStatus}
                  ingestionHealth={ingestionHealth}
                  onReconnect={reconnect}
                />
                {connStatus === 'NOT_CONNECTED' && (
                  <Button onClick={reconnect}>{t('onboarding.connect.cta')}</Button>
                )}
              </div>
            </SettingCard>
          </section>

          {/* Vùng nguy hiểm */}
          <section className="space-y-4" aria-labelledby="settings-section-danger">
            <SectionHeader
              id="settings-section-danger"
              title={t('settings.dangerZone.heading')}
              helperText={t('deleteAccount.body')}
              icon={TriangleAlert}
            />
            <SettingCard className="border-destructive/40 bg-destructive/5">
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
                    try {
                      await del.mutateAsync();
                      window.location.href = '/login';
                    } catch {
                      toast.error(t('common.retry'));
                    }
                  }}
                />
              </div>
            </SettingCard>
          </section>
        </div>
      </div>
    </div>
  );
}
