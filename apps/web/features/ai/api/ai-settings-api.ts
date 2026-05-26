import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type VoiceSettings = components['schemas']['VoiceSettingsResponse'];
export type VoiceSettingsUpdate = components['schemas']['VoiceSettingsUpdateRequest'];
export type BehaviorSettings = components['schemas']['BehaviorSettingsResponse'];
export type BehaviorSettingsUpdate = components['schemas']['BehaviorSettingsUpdateRequest'];
export type GenerateFromSentResponse = components['schemas']['GenerateFromSentResponse'];

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function getVoiceSettings(): Promise<VoiceSettings> {
  const result = await api.GET('/api/settings/voice', {});
  return unwrap(result, `/api/settings/voice failed: ${result.response.status}`);
}

export async function updateVoiceSettings(body: VoiceSettingsUpdate): Promise<VoiceSettings> {
  const result = await api.PUT('/api/settings/voice', { body });
  return unwrap(result, `/api/settings/voice update failed: ${result.response.status}`);
}

export async function getBehaviorSettings(): Promise<BehaviorSettings> {
  const result = await api.GET('/api/settings/behavior', {});
  return unwrap(result, `/api/settings/behavior failed: ${result.response.status}`);
}

export async function updateBehaviorSettings(
  body: BehaviorSettingsUpdate,
): Promise<BehaviorSettings> {
  const result = await api.PUT('/api/settings/behavior', { body });
  return unwrap(result, `/api/settings/behavior update failed: ${result.response.status}`);
}

export async function generateVoiceFromSent(sampleSize = 20): Promise<GenerateFromSentResponse> {
  const result = await api.POST('/api/settings/voice/generate-from-sent', {
    body: { sampleSize },
  });
  return unwrap(result, `/api/settings/voice/generate-from-sent failed: ${result.response.status}`);
}
