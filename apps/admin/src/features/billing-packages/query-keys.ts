export const billingPackageQueryKeys = {
  all: ['billing-packages'] as const,
  list: () => [...billingPackageQueryKeys.all, 'list'] as const,
  detail: (packageId: string) => [...billingPackageQueryKeys.all, 'detail', packageId] as const,
};
