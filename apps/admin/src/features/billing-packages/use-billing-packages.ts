import { useQuery } from '@tanstack/react-query';

import { fetchBillingPackages } from './billing-packages-api';
import { billingPackageQueryKeys } from './query-keys';

export function useBillingPackages() {
  return useQuery({
    queryKey: billingPackageQueryKeys.list(),
    queryFn: fetchBillingPackages,
  });
}
