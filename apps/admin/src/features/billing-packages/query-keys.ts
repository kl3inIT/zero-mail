export const billingPackageQueryKeys = {
  all: ['billing-packages'] as const,
  overview: () => [...billingPackageQueryKeys.all, 'overview'] as const,
};
