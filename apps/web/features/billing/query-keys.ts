export const billingKeys = {
  all: ['billing'] as const,
  balance: () => [...billingKeys.all, 'balance'] as const,
  ledger: () => [...billingKeys.all, 'ledger'] as const,
  plans: () => [...billingKeys.all, 'plans'] as const,
} as const;
