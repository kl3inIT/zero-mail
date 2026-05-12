'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { buttonVariants } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { LoadingState } from '@/components/states/LoadingState';
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
      <section className="bg-card w-full min-w-0 rounded-md border p-5 shadow-sm sm:p-7">
        <LoadingState count={1} />
      </section>
    );
  }

  return (
    <section className="bg-card w-full min-w-0 rounded-md border p-6 shadow-sm sm:p-8">
      <div className="grid size-14 place-items-center rounded-full bg-[var(--accent-soft)] text-[var(--accent)]">
        <CheckIcon size={28} />
      </div>
      <h1 className="text-foreground mt-4 text-[28px] leading-tight font-semibold">
        <span>{t('onboarding.completion.heading')}</span>
      </h1>
      <p className="text-muted-foreground mt-4 max-w-xl text-sm leading-relaxed">
        {t('onboarding.completion.body')}
      </p>
      <Card className="bg-secondary mt-7 rounded-md border p-5 shadow-none ring-0">
        <h2 className="text-muted-foreground font-mono text-xs uppercase">
          {t('onboarding.completion.nextTitle')}
        </h2>
        <ul className="text-foreground mt-4 grid gap-3 text-sm">
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
        className={cn(buttonVariants({ variant: 'accent', size: 'lg' }), 'mt-6 h-11 w-full')}
      >
        {completeMut.isPending ? t('common.loading') : t('onboarding.completion.cta')}
      </button>
    </section>
  );
}
