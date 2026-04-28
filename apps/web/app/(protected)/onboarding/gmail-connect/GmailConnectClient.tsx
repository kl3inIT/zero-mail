'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card } from '@/components/ui/card';
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
      <div className="zm-auth-panel">
        <p className="text-sm leading-relaxed text-[var(--text-muted)]">
          {t('onboarding.loading')}
        </p>
      </div>
    );
  }

  return (
    <section className="zm-auth-panel">
      <span className="zm-eyebrow">
        <span className="zm-dot" />
        {t('onboarding.connect.eyebrow')}
      </span>
      <h1 className="zm-auth-title">
        <span>{t('onboarding.connect.heading')}</span>
      </h1>
      <p className="zm-auth-sub">{t('onboarding.connect.body')}</p>
      <Card className="mt-7 gap-0 overflow-hidden rounded-xl border border-[var(--line)] bg-[var(--bg-elevated)] p-0 shadow-none ring-0">
        <div className="zm-gmail-account-row grid grid-cols-[44px_1fr_auto] items-center gap-3 border-b border-[var(--line)] p-4">
          <span className="grid size-11 place-items-center rounded-full bg-gradient-to-br from-[#4285F4] to-[#34A853] text-white">
            {me.data.email?.[0]?.toUpperCase() ?? 'Z'}
          </span>
          <div className="min-w-0">
            <div className="truncate font-semibold text-[var(--ink)]">{me.data.email}</div>
            <div className="text-sm text-[var(--text-muted)]">
              {t('connectionHealth.connected')}
            </div>
          </div>
          <span className="zm-pill pill-green">{t('connectionHealth.connected')}</span>
        </div>
        <div className="grid sm:grid-cols-2">
          <div className="border-b border-[var(--line)] p-4 sm:border-r sm:border-b-0">
            <div className="font-mono text-[11px] text-[var(--text-faint)]">
              {t('onboarding.connect.lastSync')}
            </div>
            <div className="mt-1 font-medium text-[var(--ink)]">12,431 messages</div>
          </div>
          <div className="p-4">
            <div className="font-mono text-[11px] text-[var(--text-faint)]">
              {t('onboarding.connect.scopes')}
            </div>
            <div className="mt-1 font-medium text-[var(--ink)]">read · modify · drafts</div>
          </div>
        </div>
      </Card>
      <Alert className="mt-5 flex gap-2 border-[var(--line)] bg-[var(--accent-soft)] p-3 text-sm text-[var(--accent)]">
        <ShieldIcon size={14} className="mt-0.5 shrink-0" />
        <AlertDescription className="text-[var(--accent)]">
          {t('onboarding.connect.callout')}
        </AlertDescription>
      </Alert>
    </section>
  );
}
