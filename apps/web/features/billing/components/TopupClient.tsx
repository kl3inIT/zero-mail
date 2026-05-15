'use client';

import { useCallback, useMemo, useState, useSyncExternalStore } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { TopupPackageSelector, type TopupIntentDetails } from './TopupPackageSelector';
import { TopupInstructions } from './TopupInstructions';
import { TopupExpired } from './TopupExpired';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';

type TopupStep = 'amount' | 'instructions' | 'expired';

type StoredTopupIntent = TopupIntentDetails & {
  baselineCredits: number;
};

const STORAGE_PREFIX = 'zero-mail:billing-topup:';

export function TopupClient() {
  const t = useTranslations();
  const router = useRouter();
  const searchParams = useSearchParams();
  const searchCode = searchParams.get('code');
  const balance = useBillingBalance();
  const storedIntentJson = useSyncExternalStore(
    subscribeToStoredIntent,
    () => (searchCode ? readStoredIntentJson(searchCode) : null),
    () => null,
  );
  const rehydratedIntent = useMemo(
    () => (storedIntentJson ? parseStoredIntent(storedIntentJson) : null),
    [storedIntentJson],
  );
  const [manualStep, setManualStep] = useState<TopupStep>('amount');
  const [currentIntent, setCurrentIntent] = useState<StoredTopupIntent | null>(null);
  const [clearedCode, setClearedCode] = useState<string | null>(null);

  const activeRehydratedIntent = clearedCode === searchCode ? null : rehydratedIntent;
  const activeIntent = currentIntent ?? activeRehydratedIntent;
  const step =
    manualStep === 'amount' && activeIntent
      ? isExpired(activeIntent.expiresAt)
        ? 'expired'
        : 'instructions'
      : manualStep;

  const restart = useCallback(() => {
    if (activeIntent?.code) {
      safeRemoveStoredIntent(activeIntent.code);
      setClearedCode(activeIntent.code);
    }
    setCurrentIntent(null);
    setManualStep('amount');
    router.replace('/billing/top-up', { scroll: false });
  }, [activeIntent, router]);

  const handleIntentCreated = useCallback(
    (createdIntent: TopupIntentDetails, baselineCredits: number) => {
      const storedIntent: StoredTopupIntent = { ...createdIntent, baselineCredits };
      safeSetStoredIntent(createdIntent.code, storedIntent);
      setCurrentIntent(storedIntent);
      setClearedCode(null);
      setManualStep(isExpired(storedIntent.expiresAt) ? 'expired' : 'instructions');
      router.replace(`/billing/top-up?code=${encodeURIComponent(createdIntent.code)}`, {
        scroll: false,
      });
    },
    [router],
  );

  const handleCredited = useCallback(() => {
    if (activeIntent?.code) {
      safeRemoveStoredIntent(activeIntent.code);
      setClearedCode(activeIntent.code);
    }
    setCurrentIntent(null);
    setManualStep('amount');
    const successUrl = activeIntent?.code ? `/billing` : '/billing';
    router.replace(successUrl, { scroll: false });
  }, [activeIntent, router]);

  const handleExpired = useCallback(() => {
    setManualStep('expired');
  }, []);

  const baselineCredits = balance.data?.availableCredits ?? 0;

  return (
    <div className="space-y-12">
      <div className="mb-12 space-y-4 text-center">
        <h1 className="text-foreground from-foreground to-foreground/70 bg-linear-to-b bg-clip-text text-4xl font-extrabold tracking-tight text-transparent md:text-5xl">
          {t('billing.topup.page.title')}
        </h1>
        <p className="text-muted-foreground mx-auto max-w-2xl text-lg leading-relaxed">
          {t('billing.topup.page.description')}
        </p>
      </div>

      {step === 'expired' ? (
        <TopupExpired onRestart={restart} />
      ) : step === 'instructions' && activeIntent ? (
        <TopupInstructions
          intent={activeIntent}
          baselineCredits={activeIntent.baselineCredits}
          onCredited={handleCredited}
          onExpired={handleExpired}
        />
      ) : (
        <TopupPackageSelector
          baselineCredits={baselineCredits}
          onIntentCreated={handleIntentCreated}
        />
      )}
    </div>
  );
}

function storageKey(code: string): string {
  return `${STORAGE_PREFIX}${code}`;
}

function subscribeToStoredIntent() {
  return () => undefined;
}

function readStoredIntentJson(code: string): string | null {
  if (typeof window === 'undefined') {
    return null;
  }

  try {
    return window.sessionStorage.getItem(storageKey(code));
  } catch {
    return null;
  }
}

function safeRemoveStoredIntent(code: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.removeItem(storageKey(code));
  } catch {
    // Storage can be unavailable in private browsing or locked-down contexts.
  }
}

function safeSetStoredIntent(code: string, intent: StoredTopupIntent): void {
  if (typeof window === 'undefined') return;
  try {
    window.sessionStorage.setItem(storageKey(code), JSON.stringify(intent));
  } catch {
    // The in-memory state still drives the current flow when storage is unavailable.
  }
}

function parseStoredIntent(raw: string): StoredTopupIntent | null {
  try {
    const parsed = JSON.parse(raw) as Partial<StoredTopupIntent>;
    if (
      typeof parsed.code !== 'string' ||
      typeof parsed.amountVnd !== 'number' ||
      typeof parsed.expiresAt !== 'string' ||
      typeof parsed.baselineCredits !== 'number'
    ) {
      return null;
    }
    return { ...parsed, qrPayload: parsed.qrPayload ?? '' } as StoredTopupIntent;
  } catch {
    return null;
  }
}

function isExpired(expiresAt: string): boolean {
  const expiryTime = Date.parse(expiresAt);
  return Number.isFinite(expiryTime) && expiryTime <= Date.now();
}
