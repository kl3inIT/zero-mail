'use client';

import { useCallback, useMemo, useState, useSyncExternalStore } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { TopupAmountForm, type TopupIntentDetails } from './TopupAmountForm';
import { TopupInstructions } from './TopupInstructions';
import { TopupSuccess } from './TopupSuccess';
import { TopupExpired } from './TopupExpired';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';

type TopupStep = 'amount' | 'instructions' | 'success' | 'expired';

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
  const [creditedBalance, setCreditedBalance] = useState<number | null>(null);
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
      sessionStorage.removeItem(storageKey(activeIntent.code));
      setClearedCode(activeIntent.code);
    }
    setCurrentIntent(null);
    setCreditedBalance(null);
    setManualStep('amount');
    router.replace('/billing/top-up', { scroll: false });
  }, [activeIntent, router]);

  const handleIntentCreated = useCallback(
    (createdIntent: TopupIntentDetails, baselineCredits: number) => {
      const storedIntent: StoredTopupIntent = { ...createdIntent, baselineCredits };
      sessionStorage.setItem(storageKey(createdIntent.code), JSON.stringify(storedIntent));
      setCurrentIntent(storedIntent);
      setClearedCode(null);
      setCreditedBalance(null);
      setManualStep(isExpired(storedIntent.expiresAt) ? 'expired' : 'instructions');
      router.replace(`/billing/top-up?code=${encodeURIComponent(createdIntent.code)}`, {
        scroll: false,
      });
    },
    [router],
  );

  const handleCredited = useCallback(
    (newBalance: number) => {
      if (activeIntent?.code) {
        sessionStorage.removeItem(storageKey(activeIntent.code));
        setClearedCode(activeIntent.code);
      }
      setCurrentIntent(null);
      setCreditedBalance(newBalance);
      setManualStep('success');
      router.replace('/billing/top-up', { scroll: false });
    },
    [activeIntent, router],
  );

  const handleExpired = useCallback(() => {
    setManualStep('expired');
  }, []);

  const baselineCredits = balance.data?.availableCredits ?? 0;

  return (
    <div className="space-y-5">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('billing.topup.page.title')}</h1>
        <p className="text-muted-foreground max-w-2xl text-sm leading-6">
          {t('billing.topup.page.description')}
        </p>
      </div>

      {step === 'success' && creditedBalance !== null ? (
        <TopupSuccess newBalance={creditedBalance} />
      ) : step === 'expired' ? (
        <TopupExpired onRestart={restart} />
      ) : step === 'instructions' && activeIntent ? (
        <TopupInstructions
          intent={activeIntent}
          baselineCredits={activeIntent.baselineCredits}
          onCredited={handleCredited}
          onExpired={handleExpired}
        />
      ) : (
        <TopupAmountForm baselineCredits={baselineCredits} onIntentCreated={handleIntentCreated} />
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

function parseStoredIntent(raw: string): StoredTopupIntent | null {
  try {
    const parsed = JSON.parse(raw) as Partial<StoredTopupIntent>;
    if (
      typeof parsed.code !== 'string' ||
      typeof parsed.amountVnd !== 'number' ||
      typeof parsed.expiresAt !== 'string' ||
      typeof parsed.qrPayload !== 'string' ||
      typeof parsed.baselineCredits !== 'number'
    ) {
      return null;
    }
    return parsed as StoredTopupIntent;
  } catch {
    return null;
  }
}

function isExpired(expiresAt: string): boolean {
  const expiryTime = Date.parse(expiresAt);
  return Number.isFinite(expiryTime) && expiryTime <= Date.now();
}
