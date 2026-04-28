'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
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
      <p className="text-muted-foreground text-sm leading-relaxed">{t('onboarding.loading')}</p>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('onboarding.connect.heading')}</CardTitle>
        <CardDescription className="leading-relaxed">
          {t('onboarding.connect.body')}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <p className="text-muted-foreground text-sm leading-relaxed">
          {t('connectionHealth.connected')}
        </p>
      </CardContent>
    </Card>
  );
}
