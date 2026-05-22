'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { LoadingState } from '@/components/states/LoadingState';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';

export function GmailConnectClient() {
  const t = useTranslations();
  const router = useRouter();
  const me = useCurrentUser();

  useEffect(() => {
    if (!me.data) return;
    const step = me.data.onboardingStep;
    if (step === 'TEMPLATE_SELECTED') router.replace('/onboarding/complete');
    else if (step === 'COMPLETE') router.replace('/settings');
  }, [me.data, router]);

  if (!me.data) {
    return (
      <section className="bg-card w-full min-w-0 rounded-md border p-5 shadow-sm sm:p-7">
        <LoadingState count={1} />
      </section>
    );
  }

  const connectionStatus = me.data.gmailConnectionStatus?.status;
  const displayEmail = me.data.gmailConnectionStatus?.googleEmail ?? me.data.email;
  const isConnected = connectionStatus === 'CONNECTED';
  const previewRows = [
    {
      sender: t('onboarding.connect.preview.newsletter.sender'),
      label: t('onboarding.connect.preview.newsletter.label'),
      labelClassName: 'bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300',
      subject: t('onboarding.connect.preview.newsletter.subject'),
      excerpt: t('onboarding.connect.preview.newsletter.excerpt'),
    },
    {
      sender: t('onboarding.connect.preview.reply.sender'),
      label: t('onboarding.connect.preview.reply.label'),
      labelClassName: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
      subject: t('onboarding.connect.preview.reply.subject'),
      excerpt: t('onboarding.connect.preview.reply.excerpt'),
    },
    {
      sender: t('onboarding.connect.preview.receipt.sender'),
      label: t('onboarding.connect.preview.receipt.label'),
      labelClassName:
        'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
      subject: t('onboarding.connect.preview.receipt.subject'),
      excerpt: t('onboarding.connect.preview.receipt.excerpt'),
    },
  ];

  return (
    <section className="bg-card w-full min-w-0 rounded-md border p-5 shadow-sm sm:p-7">
      <div className="grid gap-7 lg:grid-cols-[minmax(0,1.15fr)_minmax(280px,0.85fr)] lg:items-center">
        <div className="bg-background overflow-hidden rounded-md border shadow-sm">
          <div className="bg-secondary/70 flex items-center justify-between border-b px-4 py-3">
            <div className="flex min-w-0 items-center gap-2">
              <span className="bg-primary size-2 rounded-full" />
              <span className="text-foreground truncate text-sm font-semibold">
                {t('onboarding.connect.previewTitle')}
              </span>
            </div>
            <span className="text-muted-foreground text-xs">
              {t('onboarding.connect.previewMode')}
            </span>
          </div>
          <div className="divide-y">
            {previewRows.map((row) => (
              <div
                key={row.sender}
                className="grid min-w-0 grid-cols-[18px_minmax(0,0.78fr)] gap-3 px-4 py-3 sm:grid-cols-[18px_minmax(120px,0.55fr)_auto_minmax(0,1fr)] sm:items-center"
              >
                <span
                  className="border-border mt-1 size-3.5 rounded border sm:mt-0"
                  aria-hidden="true"
                />
                <strong className="text-foreground min-w-0 truncate text-sm">{row.sender}</strong>
                <span
                  className={`${row.labelClassName} col-start-2 inline-flex w-fit rounded-full px-2 py-0.5 text-xs font-medium sm:col-start-auto`}
                >
                  {row.label}
                </span>
                <p className="text-muted-foreground col-start-2 min-w-0 truncate text-sm sm:col-start-auto">
                  <span className="text-foreground">{row.subject}</span>
                  <span> - {row.excerpt}</span>
                </p>
              </div>
            ))}
          </div>
        </div>

        <div className="min-w-0">
          <span className="text-muted-foreground inline-flex items-center gap-2 font-mono text-xs font-medium uppercase">
            <span className="bg-primary size-1.5 rounded-full" />
            {t('onboarding.connect.eyebrow')}
          </span>
          <h1 className="text-foreground mt-4 text-[28px] leading-tight font-semibold">
            {t('onboarding.connect.heading')}
          </h1>
          <p className="text-muted-foreground mt-4 text-sm leading-relaxed">
            {t('onboarding.connect.body')}
          </p>
          <div className="border-border bg-secondary/50 mt-5 rounded-md border px-3 py-2 text-sm">
            <div className="text-foreground truncate font-medium">{displayEmail}</div>
            <div className={isConnected ? 'text-green' : 'text-warning'}>
              {isConnected ? t('connectionHealth.connected') : t('connectionHealth.notConnected')}
            </div>
          </div>
          <Button
            type="button"
            variant="accent"
            size="lg"
            className="mt-5 h-11 w-full"
            onClick={() => router.push('/onboarding/template-select')}
          >
            {t('onboarding.connect.continueCta')}
          </Button>
        </div>
      </div>
    </section>
  );
}
