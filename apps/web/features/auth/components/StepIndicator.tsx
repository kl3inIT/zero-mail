import type { ReactNode } from 'react';
import type { Route } from 'next';
import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { cn } from '@/lib/utils';

type Step = 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED' | 'COMPLETE';

const STEPS: { key: Step; href: Route; labelKey: string }[] = [
  {
    key: 'GMAIL_CONNECTED',
    href: '/onboarding/gmail-connect',
    labelKey: 'onboarding.steps.gmailConnect.shortLabel',
  },
  {
    key: 'TEMPLATE_SELECTED',
    href: '/onboarding/template-select',
    labelKey: 'onboarding.steps.templateSelect.shortLabel',
  },
  {
    key: 'COMPLETE',
    href: '/onboarding/complete',
    labelKey: 'onboarding.steps.complete.shortLabel',
  },
];

const ORDER: Step[] = ['GMAIL_CONNECTED', 'TEMPLATE_SELECTED', 'COMPLETE'];

export async function StepIndicator({ currentStep }: { currentStep: Step }) {
  const t = await getTranslations();
  const currentIdx = ORDER.indexOf(currentStep);

  return (
    <nav aria-label={t('nav.onboardingProgress')}>
      <div className="flex items-center gap-2 sm:hidden">
        <Pill state="active">{t(STEPS[currentIdx].labelKey as never)}</Pill>
        <span className="text-muted-foreground text-xs">
          {t('onboarding.steps.progressLabel', { current: currentIdx + 1, total: 3 })}
        </span>
      </div>
      <ol className="hidden items-center gap-2 sm:flex">
        {STEPS.map((step, index) => {
          const state: 'active' | 'completed' | 'future' =
            index < currentIdx ? 'completed' : index === currentIdx ? 'active' : 'future';
          const label = t(step.labelKey as never);

          return (
            <li key={step.key} className="flex items-center gap-2">
              {state === 'completed' ? (
                <Link href={step.href} className="transition-opacity hover:opacity-80">
                  <Pill state="completed">{label}</Pill>
                </Link>
              ) : (
                <Pill state={state}>{label}</Pill>
              )}
              {index < STEPS.length - 1 && (
                <span className="text-muted-foreground text-sm" aria-hidden="true">
                  →
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

function Pill({
  state,
  children,
}: {
  state: 'active' | 'completed' | 'future';
  children: ReactNode;
}) {
  return (
    <span
      className={cn(
        'inline-flex min-h-8 items-center gap-1 rounded-full px-3 py-1 text-xs font-medium whitespace-nowrap',
        state === 'active' && 'bg-accent text-accent-foreground',
        state === 'completed' && 'bg-accent-soft text-accent-foreground',
        state === 'future' && 'border',
        state === 'future' && 'border-border text-muted-foreground',
      )}
    >
      {state === 'completed' && <CheckIcon className="size-3 shrink-0" />}
      {children}
    </span>
  );
}

function CheckIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M5 13l4 4L19 7" />
    </svg>
  );
}
