import { xsrfHeader } from '@/lib/api/client';
import { getApiUrl } from '@/lib/api/base-url';

export type TelegramStatus = {
  configured: boolean;
  connected: boolean;
  telegramUsername: string | null;
  linkedAt: string | null;
  lastActiveAt: string | null;
};

export type TelegramPairing = {
  code: string;
  deeplink: string;
  expiresAt: string;
};

async function readJson<T>(response: Response, endpoint: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`${endpoint} failed: ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function getTelegramStatus(signal?: AbortSignal): Promise<TelegramStatus> {
  const endpoint = '/api/integrations/telegram/status';
  const response = await fetch(getApiUrl(endpoint), {
    credentials: 'include',
    signal,
  });
  return readJson<TelegramStatus>(response, endpoint);
}

export async function createTelegramPairing(): Promise<TelegramPairing> {
  const endpoint = '/api/integrations/telegram/pairing';
  const response = await fetch(getApiUrl(endpoint), {
    credentials: 'include',
    headers: xsrfHeader(),
    method: 'POST',
  });
  return readJson<TelegramPairing>(response, endpoint);
}

export async function disconnectTelegram(): Promise<void> {
  const endpoint = '/api/integrations/telegram/connection';
  const response = await fetch(getApiUrl(endpoint), {
    credentials: 'include',
    headers: xsrfHeader(),
    method: 'DELETE',
  });
  if (!response.ok) {
    throw new Error(`${endpoint} failed: ${response.status}`);
  }
}
