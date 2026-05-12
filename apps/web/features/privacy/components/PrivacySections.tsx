import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { KeyRound, MailX, ShieldCheck } from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export async function PrivacySections() {
  const t = await getTranslations();

  return (
    <div className="grid gap-4">
      <Card className="border-accent/30 bg-accent-soft/30">
        <CardHeader>
          <div className="flex items-start gap-3">
            <span className="bg-accent text-accent-foreground grid size-10 shrink-0 place-items-center rounded-lg">
              <ShieldCheck className="size-5" aria-hidden="true" />
            </span>
            <CardTitle className="text-xl font-semibold">
              {t('privacy.neverStore.heading')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm leading-6">{t('privacy.neverStore.body')}</p>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <div className="flex items-start gap-3">
              <span className="bg-warning-soft text-warning grid size-10 shrink-0 place-items-center rounded-lg">
                <MailX className="size-5" aria-hidden="true" />
              </span>
              <CardTitle className="text-xl font-semibold">
                {t('privacy.capabilities.heading')}
              </CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground text-sm leading-6">
              {t('privacy.capabilities.body')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-start gap-3">
              <span className="bg-muted text-foreground grid size-10 shrink-0 place-items-center rounded-lg">
                <KeyRound className="size-5" aria-hidden="true" />
              </span>
              <CardTitle className="text-xl font-semibold">{t('privacy.byok.heading')}</CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground text-sm leading-6">{t('privacy.byok.body')}</p>
          </CardContent>
        </Card>
      </div>

      <p className="text-muted-foreground text-sm leading-6">
        {t('privacy.publicPolicyIntro')}{' '}
        <Link
          href="/privacy"
          className="text-accent font-medium underline-offset-4 hover:underline"
        >
          {t('privacy.publicPolicyLink')}
        </Link>
      </p>
    </div>
  );
}
