'use client';

import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { TemplateCard } from '@/features/onboarding/components/TemplateCard';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useSelectTemplate } from '@/features/onboarding/hooks/useSelectTemplate';
import { useCompleteOnboarding } from '@/features/onboarding/hooks/useCompleteOnboarding';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

type TemplateKey = 'archive-receipts' | 'label-newsletters' | 'pin-calendar';

/**
 * /onboarding (client). Plan 04 Task 3 — all endpoint-specific calls moved to
 * features/<name>/api + hooks (REVIEWS Revision 1, Codex HIGH #1). Inline
 * api.GET/POST removed; JSX/copy preserved.
 */
export default function OnboardingPage() {
  const t = useTranslations();
  const me = useCurrentUser();
  const selectMut = useSelectTemplate();
  const completeMut = useCompleteOnboarding();
  const [selected, setSelected] = useState<TemplateKey | null>(null);

  if (!me.data) return <p className="p-6">{t('onboarding.loading')}</p>;

  const step = me.data.onboardingStep;
  const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? '';

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
    <main className="mx-auto max-w-3xl p-6">
      <p className="text-sm text-stone-600">{t('common.loadingApp')}</p>
      {step === 'SIGNED_IN' && (
        <Card className="mt-4 p-6">
          <h2 className="text-xl font-semibold">{t('onboarding.connect.heading')}</h2>
          <p className="mt-2">{t('onboarding.connect.body')}</p>
          <form method="post" action={`${apiBase}/tenant/connect-gmail`} className="mt-4">
            <Button type="submit">{t('onboarding.connect.cta')}</Button>
          </form>
        </Card>
      )}
      {step === 'GMAIL_CONNECTED' && (
        <div className="mt-4 grid gap-4">
          <h2 className="text-xl font-semibold">{t('onboarding.template.heading')}</h2>
          <p className="text-sm text-stone-600">{t('onboarding.template.body')}</p>
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
          <Button
            disabled={!selected || selectMut.isPending}
            onClick={() => selected && selectMut.mutate({ templateKey: selected })}
          >
            {selectMut.isPending ? t('common.loading') : t('onboarding.template.saveCta')}
          </Button>
        </div>
      )}
      {step === 'TEMPLATE_SELECTED' && (
        <Card className="mt-4 p-6">
          <h2 className="text-xl font-semibold">{t('onboarding.completion.heading')}</h2>
          <Button onClick={() => completeMut.mutate()} className="mt-4">
            {t('onboarding.completion.cta')}
          </Button>
        </Card>
      )}
      {step === 'COMPLETE' && <p>{t('onboarding.loading')}</p>}
    </main>
  );
}
