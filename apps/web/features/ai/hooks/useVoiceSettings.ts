'use client';

import { useQuery } from '@tanstack/react-query';

import { getVoiceSettings } from '@/features/ai/api/ai-settings-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useVoiceSettings() {
  return useQuery({ queryKey: aiSettingsKeys.voice(), queryFn: getVoiceSettings });
}
