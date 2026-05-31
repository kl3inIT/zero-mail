'use client';

import { useQuery } from '@tanstack/react-query';

import { getByok } from '@/features/ai/api/byok-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useByok() {
  return useQuery({ queryKey: aiSettingsKeys.byok(), queryFn: getByok });
}
