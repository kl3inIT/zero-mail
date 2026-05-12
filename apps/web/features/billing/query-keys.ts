export const billingKeys = {
  all: ['billing'] as const,
  balance: () => [...billingKeys.all, 'balance'] as const,
  ledger: () => [...billingKeys.all, 'ledger'] as const,
  topupIntent: (code: string) => [...billingKeys.all, 'topup-intent', code] as const,
} as const;
