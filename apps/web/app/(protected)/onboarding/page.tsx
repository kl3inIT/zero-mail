'use client';

import { useState } from 'react';

import { useTranslations } from 'next-intl';

import { TemplateCard } from '@/features/onboarding/components/TemplateCard';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useSelectTemplate } from '@/features/onboarding/hooks/useSelectTemplate';
import { useCompleteOnboarding } from '@/features/onboarding/hooks/useCompleteOnboarding';
import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';

type TemplateKey = 'archive-receipts' | 'label-newsletters' | 'pin-calendar';

/**
 * /onboarding (client). Phase 01.5 Plan 02 — deflated from PageShell/SectionCard/
 * StatusAlert to raw shadcn primitives (D-C1, D-C2).
 *
 * Deleted (Plan 01 backend no longer emits these):
 *  - useSearchParams + ERROR_KEY_MAP + handleRetry (mismatch flow removed)
 *  - StatusAlert mismatch-error block (errors now route to /login?error=...)
 *  - step === 'SIGNED_IN' block (OnboardingStep.SIGNED_IN dropped in Plan 01)
 *
 * Remaining step branches: GMAIL_CONNECTED (template selection), TEMPLATE_SELECTED
 * (completion step), COMPLETE (redirect handled by auth guard).
 *
 * Plain <button className={cn(buttonVariants())}> pattern preserved (STATE.md
 * line 153 — vitest @base-ui/react useRef null-dispatcher boundary).
 */
export default function OnboardingPage() {
  const t = useTranslations();
  const me = useCurrentUser();
  const selectMut = useSelectTemplate();
  const completeMut = useCompleteOnboarding();
  const [selected, setSelected] = useState<TemplateKey | null>(null);

  if (!me.data) {
    return (
      <main className="mx-auto max-w-3xl space-y-6 p-6">
        <p>{t('onboarding.loading')}</p>
      </main>
    );
  }

  const step = me.data.onboardingStep;

  const templates: { key: TemplateKey; titleKey: string; descKey: string }[] = [
    {
      key: 'archive-receipts',
      titleKey: 'templates.receipts.title',
      descKey: 'templates.receipts.description',
    },
    {
      key: 'label-newsletters',
      titleKey: 'templates.newsletters.title',
      descKey: 'templates.newsletters.description',
    },
    {
      key: 'pin-calendar',
      titleKey: 'templates.calendarInvites.title',
      descKey: 'templates.calendarInvites.description',
    },
  ];

  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6">
      <p className="text-muted-foreground text-sm">{t('common.loadingApp')}</p>

      {step === 'GMAIL_CONNECTED' && (
        <Card>
          <CardHeader>
            <CardTitle>{t('onboarding.template.heading')}</CardTitle>
            <CardDescription>{t('onboarding.template.body')}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4">
              {templates.map((tpl) => (
                <TemplateCard
                  key={tpl.key}
                  templateKey={tpl.key}
                  title={t(tpl.titleKey as never)}
                  description={t(tpl.descKey as never)}
                  selected={selected === tpl.key}
                  onSelect={() => setSelected(tpl.key)}
                />
              ))}
              <button
                type="button"
                disabled={!selected || selectMut.isPending}
                onClick={() => selected && selectMut.mutate({ templateKey: selected })}
                className={cn(buttonVariants())}
              >
                {selectMut.isPending ? t('common.loading') : t('onboarding.template.saveCta')}
              </button>
            </div>
          </CardContent>
        </Card>
      )}

      {step === 'TEMPLATE_SELECTED' && (
        <Card>
          <CardHeader>
            <CardTitle>{t('onboarding.completion.heading')}</CardTitle>
          </CardHeader>
          <CardContent>
            <button
              type="button"
              onClick={() => completeMut.mutate()}
              className={cn(buttonVariants())}
            >
              {t('onboarding.completion.cta')}
            </button>
          </CardContent>
        </Card>
      )}

      {step === 'COMPLETE' && <p>{t('onboarding.loading')}</p>}
    </main>
  );
}
