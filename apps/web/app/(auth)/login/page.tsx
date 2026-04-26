import { getLocale, getTranslations } from 'next-intl/server';

import { LanguageSwitcher } from '@/features/i18n/components/LanguageSwitcher';
import { PageShell } from '@/components/ui/PageShell';
import { buttonVariants } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

/**
 * /login (RSC). Plan 06 Task 2 — refactored onto PageShell variant=auth +
 * token-aware Tailwind classes (CONTEXT D-C2 / D-C3). PageShell `auth`
 * variant pins max-w-md and flex-centers the viewport; Card frames the
 * auth content inside that centered column.
 *
 * Behavior unchanged: getTranslations + LanguageSwitcher + Google sign-in
 * anchor exactly as before. Only token classes + container shape change.
 */
export default async function LoginPage() {
  const t = await getTranslations('auth.login');
  const locale = (await getLocale()) as AppLocale;

  return (
    <PageShell variant="auth">
      <Card className="w-full p-8">
        <div className="flex items-start justify-between gap-3">
          <h1 className="text-3xl font-semibold tracking-tight">{t('headline')}</h1>
          <LanguageSwitcher currentLocale={locale} authenticated={false} variant="compact" />
        </div>
        <p className="text-foreground mt-4 text-base">{t('body')}</p>
        <ul className="text-muted-foreground mt-4 list-disc pl-5 text-sm">
          <li>{t('safety.noAutoSend')}</li>
          <li>{t('safety.noLongTermStorage')}</li>
          <li>{t('safety.revokeAnytime')}</li>
        </ul>
        <a
          href={getApiUrl('/oauth2/authorization/google')}
          className={cn(buttonVariants(), 'mt-6 w-full')}
        >
          {t('googleButton')}
        </a>
      </Card>
    </PageShell>
  );
}
