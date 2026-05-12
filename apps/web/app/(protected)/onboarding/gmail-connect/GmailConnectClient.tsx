'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card } from '@/components/ui/card';
import { LoadingState } from '@/components/states/LoadingState';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { ShieldIcon } from '@/features/landing/components/PrototypeIcons';

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

  return (
    <section className="bg-card w-full min-w-0 rounded-md border p-6 shadow-sm sm:p-8">
      <span className="text-muted-foreground inline-flex items-center gap-2 font-mono text-xs font-medium uppercase">
        <span className="bg-primary size-1.5 rounded-full" />
        {t('onboarding.connect.eyebrow')}
      </span>
      <h1 className="text-foreground mt-4 text-[28px] leading-tight font-semibold">
        <span>{t('onboarding.connect.heading')}</span>
      </h1>
      <p className="text-muted-foreground mt-4 max-w-xl text-sm leading-relaxed">
        {t('onboarding.connect.body')}
      </p>
      <Card className="bg-card mt-7 gap-0 overflow-hidden rounded-md border p-0 shadow-none ring-0">
        <div className="grid grid-cols-[44px_minmax(0,1fr)] items-center gap-3 border-b p-4 sm:grid-cols-[44px_minmax(0,1fr)_auto]">
          <span className="bg-primary text-primary-foreground grid size-11 place-items-center rounded-full">
            {me.data.email?.[0]?.toUpperCase() ?? 'Z'}
          </span>
          <div className="min-w-0">
            <div className="text-foreground truncate font-semibold">{me.data.email}</div>
            <div className="text-muted-foreground text-sm">{t('connectionHealth.connected')}</div>
          </div>
          <span className="bg-green-soft text-green col-start-2 inline-flex min-h-6 w-fit items-center rounded-full px-2.5 text-xs font-medium sm:col-start-auto">
            {t('connectionHealth.connected')}
          </span>
        </div>
        <div className="grid sm:grid-cols-2">
          <div className="border-b p-4 sm:border-r sm:border-b-0">
            <div className="text-muted-foreground font-mono text-xs">
              {t('onboarding.connect.lastSync')}
            </div>
            <div className="text-foreground mt-1 font-medium">12,431 messages</div>
          </div>
          <div className="p-4">
            <div className="text-muted-foreground font-mono text-xs">
              {t('onboarding.connect.scopes')}
            </div>
            <div className="text-foreground mt-1 font-medium">read · modify · drafts</div>
          </div>
        </div>
      </Card>
      <Alert className="border-border bg-accent-soft text-accent mt-5 flex gap-2 p-3 text-sm">
        <ShieldIcon size={14} className="mt-0.5 shrink-0" />
        <AlertDescription className="text-accent">
          {t('onboarding.connect.callout')}
        </AlertDescription>
      </Alert>
    </section>
  );
}
