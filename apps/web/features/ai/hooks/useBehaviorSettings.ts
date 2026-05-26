'use client';

import { useQuery } from '@tanstack/react-query';

import { getBehaviorSettings } from '@/features/ai/api/ai-settings-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useBehaviorSettings() {
  return useQuery({ queryKey: aiSettingsKeys.behavior(), queryFn: getBehaviorSettings });
}
