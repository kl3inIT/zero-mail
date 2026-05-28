import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';

// TODO: switch to the generated OpenAPI client when :backend:api:generateOpenApiDocs is wired
// to the dev loop. Following the raw-fetch precedent from features/inbox/api/inbox-api.ts.
export type AssistantSettings = {
  personalInstructions: string | null;
  writingStyle: string | null;
  aiOutputLanguage: string | null;
};

export type AssistantSettingsUpdateInput = {
  personalInstructions: string | null;
  writingStyle: string | null;
  aiOutputLanguage: string | null;
};

export class AssistantSettingsApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'AssistantSettingsApiError';
    this.status = status;
  }
}

export async function getAssistantSettings(): Promise<AssistantSettings> {
  const response = await fetch(getApiUrl('/api/assistant/settings'), {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new AssistantSettingsApiError(
      `/api/assistant/settings GET failed: ${response.status}`,
      response.status,
    );
  }
  return (await response.json()) as AssistantSettings;
}

export async function updateAssistantSettings(
  body: AssistantSettingsUpdateInput,
): Promise<AssistantSettings> {
  const response = await fetch(getApiUrl('/api/assistant/settings'), {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...xsrfHeader(),
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new AssistantSettingsApiError(
      `/api/assistant/settings PUT failed: ${response.status}`,
      response.status,
    );
  }
  return (await response.json()) as AssistantSettings;
}
