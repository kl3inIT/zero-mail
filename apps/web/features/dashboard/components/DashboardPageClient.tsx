'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { ListChecks, MailCheck, MailX, Sparkles } from 'lucide-react';

import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { AuditLog } from '@/features/triage/components/AuditLog';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useToReplyCount } from '@/features/needs-reply/hooks/useToReplyCount';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';
import { formatCredits } from '@/lib/format';
import { useHydrated } from '@/lib/use-hydrated';
import { cn } from '@/lib/utils';

export function DashboardPageClient() {
  const t = useTranslations();
  const currentUser = useCurrentUser();
  const toReplyCount = useToReplyCount();
  const billingBalance = useBillingBalance();
  const hydrated = useHydrated();

  const email = currentUser.data?.email ?? '';
  const displayName = email.split('@')[0] || null;
  const connectionStatus = currentUser.data?.gmailConnectionStatus?.status;
  const connected = connectionStatus === 'CONNECTED';
  const toReply = hydrated ? (toReplyCount.data ?? 0) : 0;
  const credits = hydrated ? (billingBalance.data?.availableCredits ?? 0) : 0;

  return (
    <div className="flex h-full flex-col">
      <div className="border-border border-b px-4 py-3">
        <h1 className="text-foreground text-[17px] font-semibold">
          {displayName ? `${t('dashboard.welcome')}, ${displayName}` : t('dashboard.welcome')}
        </h1>
        <p className="text-muted-foreground text-sm">{t('dashboard.subtitle')}</p>
      </div>
      <div className="flex-1 space-y-4 overflow-auto p-3 sm:p-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-muted-foreground text-sm font-medium">
                {t('dashboard.stats.connection')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              {currentUser.isPending ? (
                <Skeleton className="h-7 w-28" />
              ) : (
                <div className="flex items-center gap-2">
                  {connected ? (
                    <MailCheck className="text-green size-5" aria-hidden="true" />
                  ) : (
                    <MailX className="text-red size-5" aria-hidden="true" />
                  )}
                  <span
                    className={cn('text-base font-semibold', connected ? 'text-green' : 'text-red')}
                  >
                    {connected
                      ? t('shell.connection.connected')
                      : t('shell.connection.disconnected')}
                  </span>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-muted-foreground text-sm font-medium">
                {t('nav.needsReply')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              {toReplyCount.isPending ? (
                <Skeleton className="h-7 w-12" />
              ) : (
                <Link href="/needs-reply" className="flex items-baseline gap-1.5 hover:underline">
                  <span className="text-foreground text-2xl font-bold tabular-nums">{toReply}</span>
                  <span className="text-muted-foreground text-sm">
                    {t('dashboard.stats.threads')}
                  </span>
                </Link>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-muted-foreground text-sm font-medium">
                {t('shell.balance.label')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              {billingBalance.isPending ? (
                <Skeleton className="h-7 w-16" />
              ) : (
                <Link href="/billing" className="flex items-baseline gap-1.5 hover:underline">
                  <span className="text-foreground font-mono text-2xl font-bold tabular-nums">
                    {formatCredits(credits)}
                  </span>
                </Link>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="flex flex-wrap gap-2">
          <Link href="/rules" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            <ListChecks className="size-4" aria-hidden="true" />
            {t('nav.rules')}
          </Link>
          <Link href="/chat" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            <Sparkles className="size-4" aria-hidden="true" />
            {t('nav.chat')}
          </Link>
        </div>

        <div className="space-y-3">
          <h2 className="text-foreground text-base font-semibold">
            {t('dashboard.recentActivity')}
          </h2>
          <AuditLog />
        </div>
      </div>
    </div>
  );
}
