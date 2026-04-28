'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { buttonVariants } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { CheckIcon } from '@/features/landing/components/PrototypeIcons';
import { useCompleteOnboarding } from '@/features/onboarding/hooks/useCompleteOnboarding';
import { cn } from '@/lib/utils';

export function CompleteClient() {
  const t = useTranslations();
  const router = useRouter();
  const me = useCurrentUser();
  const completeMut = useCompleteOnboarding();

  useEffect(() => {
    if (!me.data) return;
    const step = me.data.onboardingStep;
    if (step === 'GMAIL_CONNECTED') router.replace('/onboarding/template-select');
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
      <div className="grid size-14 place-items-center rounded-full bg-[var(--accent-soft)] text-[var(--accent)]">
        <CheckIcon size={28} />
      </div>
      <h1 className="zm-auth-title">
        <span>{t('onboarding.completion.heading')}</span>
      </h1>
      <p className="zm-auth-sub">{t('onboarding.completion.body')}</p>
      <Card className="mt-7 rounded-xl border border-[var(--line)] bg-[var(--bg-subtle)] p-5 shadow-none ring-0">
        <h2 className="font-mono text-xs tracking-wider text-[var(--text-muted)] uppercase">
          {t('onboarding.completion.nextTitle')}
        </h2>
        <ul className="mt-4 grid gap-3 text-sm text-[var(--text)]">
          <li>{t('onboarding.completion.next1')}</li>
          <li>{t('onboarding.completion.next2')}</li>
          <li>{t('onboarding.completion.next3')}</li>
        </ul>
      </Card>
      <button
        type="button"
        disabled={completeMut.isPending}
        onClick={() =>
          completeMut.mutate(undefined, {
            onSuccess: () => router.replace('/settings'),
          })
        }
        className={cn(buttonVariants({ variant: 'accent', size: 'lg' }), 'mt-6 h-10 w-full')}
      >
        {completeMut.isPending ? t('common.loading') : t('onboarding.completion.cta')}
      </button>
    </section>
  );
}
