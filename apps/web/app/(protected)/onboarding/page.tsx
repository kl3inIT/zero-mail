'use client';

import { useTranslations } from 'next-intl';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';

import { TemplateCard } from '@/features/onboarding/components/TemplateCard';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useSelectTemplate } from '@/features/onboarding/hooks/useSelectTemplate';
import { useCompleteOnboarding } from '@/features/onboarding/hooks/useCompleteOnboarding';
import { buttonVariants } from '@/components/ui/button';
import { PageShell } from '@/components/ui/PageShell';
import { SectionCard } from '@/components/ui/SectionCard';
import { StatusAlert } from '@/components/ui/StatusAlert';
import { cn } from '@/lib/utils';
import { getApiUrl } from '@/lib/api/base-url';

type TemplateKey = 'archive-receipts' | 'label-newsletters' | 'pin-calendar';

/**
 * /onboarding (client). Plan 06 Task 1 — wires Gmail mismatch / consent-denied
 * alert via `useSearchParams()` (CONTEXT D-D1, UI-SPEC §Interaction Contracts)
 * and refactors the surface onto Plan 04 primitives (PageShell + SectionCard +
 * StatusAlert) with token-aware Tailwind classes.
 *
 * Closed-enum mapping (Threat T-error-param-tamper): only the two known error
 * codes render an alert; arbitrary URL values resolve to `null` and render
 * nothing. The page never echoes `rawError` to the DOM — only i18n-key-resolved
 * copy reaches the user.
 *
 * Retry CTA fires both side effects (Plan 06 contract):
 *   1. window.location.href = '/tenant/connect-gmail'  (re-trigger OAuth init)
 *   2. router.replace('/onboarding')                   (URL hygiene cleanup)
 */
export default function OnboardingPage() {
  const t = useTranslations();
  const me = useCurrentUser();
  const selectMut = useSelectTemplate();
  const completeMut = useCompleteOnboarding();
  const [selected, setSelected] = useState<TemplateKey | null>(null);
  const params = useSearchParams();
  const router = useRouter();

  // Closed-enum mapping per Threat T-error-param-tamper. Unknown values render
  // no alert; we never pass `rawError` to JSX text content.
  const ERROR_KEY_MAP = {
    gmail_identity_mismatch: 'gmail.identity.mismatch',
    gmail_consent_denied: 'gmail.consent.denied',
  } as const;
  type KnownError = keyof typeof ERROR_KEY_MAP;
  const rawError = params.get('error');
  const isKnownError = (v: string | null): v is KnownError =>
    v !== null && Object.prototype.hasOwnProperty.call(ERROR_KEY_MAP, v);
  const baseKey = isKnownError(rawError) ? ERROR_KEY_MAP[rawError] : null;

  const handleRetry = () => {
    // URL hygiene per UI-SPEC §Interaction Contracts — clear ?error= before
    // the full-page nav re-triggers OAuth init (CONTEXT D-D6: same flow for
    // first connect + reconnect).
    router.replace('/onboarding');
    window.location.href = '/tenant/connect-gmail';
  };

  if (!me.data) {
    return (
      <PageShell variant="app">
        <p>{t('onboarding.loading')}</p>
      </PageShell>
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
    <PageShell variant="app">
      {baseKey && (
        <StatusAlert
          variant="error"
          titleKey={`${baseKey}.title`}
          bodyKey={`${baseKey}.body`}
          cta={{ labelKey: `${baseKey}.cta`, onClick: handleRetry }}
        />
      )}

      <p className="text-muted-foreground text-sm">{t('common.loadingApp')}</p>

      {step === 'SIGNED_IN' && (
        <SectionCard heading={t('onboarding.connect.heading')}>
          <p>{t('onboarding.connect.body')}</p>
          <form method="post" action={getApiUrl('/tenant/connect-gmail')} className="mt-4">
            <button type="submit" className={cn(buttonVariants())}>
              {t('onboarding.connect.cta')}
            </button>
          </form>
        </SectionCard>
      )}

      {step === 'GMAIL_CONNECTED' && (
        <SectionCard
          heading={t('onboarding.template.heading')}
          description={t('onboarding.template.body')}
        >
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
        </SectionCard>
      )}

      {step === 'TEMPLATE_SELECTED' && (
        <SectionCard heading={t('onboarding.completion.heading')}>
          <button
            type="button"
            onClick={() => completeMut.mutate()}
            className={cn(buttonVariants())}
          >
            {t('onboarding.completion.cta')}
          </button>
        </SectionCard>
      )}

      {step === 'COMPLETE' && <p>{t('onboarding.loading')}</p>}
    </PageShell>
  );
}
