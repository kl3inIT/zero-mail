'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { TemplateCard } from '@/features/onboarding/components/TemplateCard';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { api, xsrfHeader } from '@/lib/api/client';

type TemplateKey = 'archive-receipts' | 'label-newsletters' | 'pin-calendar';

type MeData = { onboardingStep: string };

/**
 * /onboarding (client). Plan 06 Task 2 — pull every prose literal through
 * `useTranslations`. TemplateCard remains a presentational component (props-down
 * pattern); the call site below resolves translations and passes them down.
 */
export default function OnboardingPage() {
  const t = useTranslations();
  const qc = useQueryClient();
  const me = useQuery<MeData | undefined>({
    queryKey: ['me'],
    queryFn: async () => (await api.GET('/me', {})).data as MeData | undefined,
  });
  const selectMut = useMutation({
    mutationFn: async (templateKey: TemplateKey) =>
      (
        await api.POST('/onboarding/select-template', {
          body: { templateKey },
          headers: xsrfHeader(),
        })
      ).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  });
  const completeMut = useMutation({
    mutationFn: async () =>
      (
        await api.POST('/onboarding/complete', {
          headers: xsrfHeader(),
        })
      ).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  });
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
            onClick={() => selected && selectMut.mutate(selected)}
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
