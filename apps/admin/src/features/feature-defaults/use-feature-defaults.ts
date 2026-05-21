import { useQuery } from '@tanstack/react-query';

import { fetchFeatureDefaults } from './feature-defaults-api';
import { featureDefaultQueryKeys } from './query-keys';

export function useFeatureDefaults() {
  return useQuery({
    queryKey: featureDefaultQueryKeys.all,
    queryFn: fetchFeatureDefaults,
  });
}
