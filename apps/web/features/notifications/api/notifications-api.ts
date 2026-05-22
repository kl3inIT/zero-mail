import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type NotificationPreferencesResponse =
  components['schemas']['NotificationPreferencesResponse'];
export type NotificationPreferencesUpdateRequest =
  components['schemas']['NotificationPreferencesUpdateRequest'];

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function fetchNotificationPreferences(): Promise<NotificationPreferencesResponse> {
  const result = await api.GET('/api/me/notifications', {});
  return unwrap(result, `/api/me/notifications failed: ${result.response.status}`);
}

export async function updateNotificationPreferences(
  body: NotificationPreferencesUpdateRequest,
): Promise<NotificationPreferencesResponse> {
  const result = await api.PATCH('/api/me/notifications', {
    body,
  });
  return unwrap(result, `/api/me/notifications failed: ${result.response.status}`);
}
