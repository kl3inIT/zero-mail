export const billingKeys = {
  all: ['billing'] as const,
  balance: () => [...billingKeys.all, 'balance'] as const,
  ledger: (limit = 10) => [...billingKeys.all, 'ledger', { limit }] as const,
  plans: () => [...billingKeys.all, 'plans'] as const,
} as const;
