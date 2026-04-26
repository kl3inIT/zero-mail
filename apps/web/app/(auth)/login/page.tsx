import { getLocale, getTranslations } from 'next-intl/server';

import { LanguageSwitcher } from '@/features/i18n/components/LanguageSwitcher';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

import type { AppLocale } from '@/i18n/routing';

/**
 * /login (RSC). Plan 06 Task 2 — every prose literal flows through
 * `getTranslations` (UI-SPEC §"Page And State Copy" + planning_context
 * D-A4 + D-D6). The compact LanguageSwitcher is mounted in the card header
 * top-right per UI-SPEC §"Language Switcher Contract".
 */
export default async function LoginPage() {
  const t = await getTranslations('auth.login');
  const locale = (await getLocale()) as AppLocale;
  const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? '';

  return (
    <main className="flex min-h-dvh items-center justify-center p-6">
      <Card className="w-full max-w-md p-8">
        <div className="flex items-start justify-between gap-3">
          <h1 className="text-3xl font-semibold">{t('headline')}</h1>
          <LanguageSwitcher currentLocale={locale} authenticated={false} variant="compact" />
        </div>
        <p className="mt-4 text-base">{t('body')}</p>
        <ul className="mt-4 list-disc pl-5 text-sm text-stone-700">
          <li>{t('safety.noAutoSend')}</li>
          <li>{t('safety.noLongTermStorage')}</li>
          <li>{t('safety.revokeAnytime')}</li>
        </ul>
        <a href={`${apiBase}/oauth2/authorization/google`}>
          <Button className="mt-6 w-full">{t('googleButton')}</Button>
        </a>
      </Card>
    </main>
  );
}
