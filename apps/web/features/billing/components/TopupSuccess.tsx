'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { CheckCircle2 } from 'lucide-react';

import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatCredits } from '@/lib/format';

type TopupSuccessProps = {
  newBalance: number;
};

export function TopupSuccess({ newBalance }: TopupSuccessProps) {
  const t = useTranslations();

  return (
    <Card data-testid="topup-success-step">
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="bg-green-soft text-green flex size-10 items-center justify-center rounded-lg">
            <CheckCircle2 className="size-5" aria-hidden="true" />
          </div>
          <CardTitle>
            <h2>{t('billing.topup.success.heading')}</h2>
          </CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground text-sm leading-6">{t('billing.topup.success.body')}</p>
        <div>
          <p className="text-foreground text-3xl font-semibold tracking-normal">
            {formatCredits(newBalance)}
          </p>
          <p className="text-muted-foreground mt-1 text-sm">{t('billing.balance.unit')}</p>
        </div>
        <Link href="/billing" className={buttonVariants({ variant: 'accent' })}>
          {t('billing.topup.success.backCta')}
        </Link>
      </CardContent>
    </Card>
  );
}
