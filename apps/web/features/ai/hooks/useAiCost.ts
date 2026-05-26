'use client';

import { useQuery } from '@tanstack/react-query';

import { getAiCost } from '@/features/ai/api/byok-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useAiCost(window = '7d') {
  return useQuery({
    queryKey: aiSettingsKeys.cost(window),
    queryFn: () => getAiCost(window),
  });
}
