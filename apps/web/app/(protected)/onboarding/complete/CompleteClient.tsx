'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
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
      <p className="text-muted-foreground text-sm leading-relaxed">{t('onboarding.loading')}</p>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('onboarding.completion.heading')}</CardTitle>
      </CardHeader>
      <CardContent>
        <button
          type="button"
          disabled={completeMut.isPending}
          onClick={() =>
            completeMut.mutate(undefined, {
              onSuccess: () => router.replace('/settings'),
            })
          }
          className={cn(buttonVariants(), 'w-full')}
        >
          {completeMut.isPending ? t('common.loading') : t('onboarding.completion.cta')}
        </button>
      </CardContent>
    </Card>
  );
}
