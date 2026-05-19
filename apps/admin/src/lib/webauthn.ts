import { startAuthentication, startRegistration } from '@simplewebauthn/browser';

import { getAdminApiUrl } from './api/admin-base-url';

type EnrollmentSessionResponse = {
  expiresAt: string;
};

async function postJson<TResponse>(path: `/${string}`, body: unknown): Promise<TResponse> {
  const response = await fetch(getAdminApiUrl(path), {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`admin request failed: ${response.status}`);
  }
  return (await response.json()) as TResponse;
}

export async function createEnrollmentSession(token: string, email: string): Promise<EnrollmentSessionResponse> {
  return postJson<EnrollmentSessionResponse>('/api/admin/enrollment/session', { token, email });
}

export async function registerPasskey(email: string): Promise<void> {
  const optionsResponse = await postJson<unknown>('/webauthn/register/options', { email });
  const attestationResponse = await startRegistration({ optionsJSON: optionsResponse });
  await postJson('/webauthn/register', attestationResponse);
}

export async function authenticatePasskey(email: string): Promise<void> {
  const optionsResponse = await postJson<unknown>('/webauthn/authenticate/options', { email });
  const assertionResponse = await startAuthentication({ optionsJSON: optionsResponse });
  await postJson('/login/webauthn', assertionResponse);
}
