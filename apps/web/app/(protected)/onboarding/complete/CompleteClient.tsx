'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { LoadingState } from '@/components/states/LoadingState';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { CheckIcon } from '@/features/landing/components/PrototypeIcons';
import { useCompleteOnboarding } from '@/features/onboarding/hooks/useCompleteOnboarding';

export function CompleteClient() {
  const t = useTranslations();
  const router = useRouter();
  const me = useCurrentUser();
  const completeMut = useCompleteOnboarding();

  useEffect(() => {
    if (!me.data) return;
    const step = me.data.onboardingStep;
    if (step === 'GMAIL_CONNECTED') router.replace('/onboarding/template-select');
    else if (step === 'COMPLETE') router.replace('/settings');
  }, [me.data, router]);

  if (!me.data) {
    return (
      <section className="bg-card w-full min-w-0 rounded-md border p-5 shadow-sm sm:p-7">
        <LoadingState count={1} />
      </section>
    );
  }

  return (
    <section className="bg-card w-full min-w-0 rounded-md border p-5 shadow-sm sm:p-7">
      <div className="grid gap-7 lg:grid-cols-[minmax(0,1.05fr)_minmax(280px,0.95fr)] lg:items-center">
        <div className="bg-background overflow-hidden rounded-md border shadow-sm">
          <div className="border-b p-4">
            <div className="flex items-center gap-3">
              <span className="bg-accent-soft text-accent grid size-10 shrink-0 place-items-center rounded-full text-xs font-semibold">
                SC
              </span>
              <div className="min-w-0">
                <strong className="text-foreground block truncate text-sm">
                  {t('onboarding.completion.draft.sender')}
                </strong>
                <span className="text-muted-foreground block truncate text-xs">
                  {t('onboarding.completion.draft.subject')}
                </span>
              </div>
            </div>
            <p className="text-muted-foreground mt-3 text-sm leading-relaxed">
              {t('onboarding.completion.draft.incoming')}
            </p>
          </div>
          <div className="bg-secondary/50 p-4">
            <div className="bg-card overflow-hidden rounded-md border shadow-sm">
              <div className="flex items-center justify-between border-b px-4 py-2">
                <strong className="text-foreground text-sm">
                  {t('onboarding.completion.draft.title')}
                </strong>
                <span className="text-accent text-xs font-medium">
                  {t('onboarding.completion.draft.status')}
                </span>
              </div>
              <p className="text-foreground px-4 py-4 text-sm leading-relaxed whitespace-pre-line">
                {t('onboarding.completion.draft.body')}
              </p>
              <div className="flex items-center gap-2 border-t px-4 py-3">
                <span className="bg-primary text-primary-foreground inline-flex h-7 items-center rounded-md px-3 text-xs font-semibold">
                  Send
                </span>
                <span className="text-muted-foreground text-xs">B</span>
                <span className="text-muted-foreground text-xs">I</span>
                <span className="text-muted-foreground text-xs">link</span>
              </div>
            </div>
          </div>
        </div>

        <div className="min-w-0">
          <div className="bg-accent-soft text-accent grid size-12 place-items-center rounded-full">
            <CheckIcon size={24} />
          </div>
          <h1 className="text-foreground mt-4 text-[28px] leading-tight font-semibold">
            {t('onboarding.completion.heading')}
          </h1>
          <p className="text-muted-foreground mt-4 text-sm leading-relaxed">
            {t('onboarding.completion.body')}
          </p>
          <div className="mt-6 overflow-hidden rounded-md border">
            <div className="bg-secondary/70 border-b px-4 py-3">
              <h2 className="text-foreground text-sm font-semibold">
                {t('onboarding.completion.nextTitle')}
              </h2>
            </div>
            <div className="divide-y">
              <ReadyRow
                title={t('onboarding.completion.next1')}
                badge={t('onboarding.completion.badgeReview')}
              />
              <ReadyRow
                title={t('onboarding.completion.next2')}
                badge={t('onboarding.completion.badgeSafe')}
              />
              <ReadyRow
                title={t('onboarding.completion.next3')}
                badge={t('onboarding.completion.badgeReview')}
              />
            </div>
          </div>
          <Button
            type="button"
            variant="accent"
            size="lg"
            disabled={completeMut.isPending}
            onClick={() =>
              completeMut.mutate(undefined, {
                onSuccess: () => router.replace('/settings'),
              })
            }
            className="mt-6 h-11 w-full"
          >
            {completeMut.isPending ? t('common.loading') : t('onboarding.completion.cta')}
          </Button>
        </div>
      </div>
    </section>
  );
}

function ReadyRow({ title, badge }: { title: string; badge: string }) {
  return (
    <div className="grid gap-2 px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
      <span className="text-foreground text-sm">{title}</span>
      <span className="bg-accent-soft text-accent inline-flex w-fit rounded-full px-2 py-0.5 text-xs font-medium">
        {badge}
      </span>
    </div>
  );
}
