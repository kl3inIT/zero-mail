import { api } from '@/lib/api/client';

export type ByokProviderPreset =
  | 'openrouter'
  | 'openai'
  | 'anthropic'
  | 'google-genai'
  | 'deepseek'
  | 'openai-compatible'
  | 'anthropic-compatible';

export type ByokProvider = ByokProviderPreset;

export type ByokValidatePayload = {
  preset: ByokProviderPreset;
  endpoint?: string;
  model: string;
  apiKey: string;
};

export type ByokValidateResult = {
  ok: boolean;
  models?: string[] | null;
  reason?: string | null;
};

export type ByokSavePayload = ByokValidatePayload;

export type ByokSaveResult = {
  ok?: boolean;
  savedAt?: string | null;
};

export type ByokCurrentResult = {
  provider?: ByokProvider | null;
  endpointHost?: string | null;
  model?: string | null;
  savedAt?: string | null;
};

type ApiResult<T> = { data?: T; error?: unknown; response: Response };
type LegacyPost<TPayload, TResult> = (
  path: string,
  options: { body: TPayload; headers?: Record<string, string> },
) => Promise<ApiResult<TResult>>;
type LegacyGet<TResult> = (path: string, options: object) => Promise<ApiResult<TResult>>;

const postLegacyByok = api.POST as unknown as LegacyPost<ByokValidatePayload, ByokValidateResult>;
const postLegacyByokSave = api.POST as unknown as LegacyPost<ByokSavePayload, ByokSaveResult>;
const getLegacyByok = api.GET as unknown as LegacyGet<ByokCurrentResult>;

export async function validateByok(payload: ByokValidatePayload): Promise<ByokValidateResult> {
  const { data, error, response } = await postLegacyByok('/api/llm/byok/validate', {
    body: payload,
    headers: { 'Content-Type': 'application/json' },
  });
  if (error || !response.ok || data === undefined)
    throw error ?? new Error(`/api/llm/byok/validate failed: ${response.status}`);
  return data;
}

export async function saveByok(payload: ByokSavePayload): Promise<ByokSaveResult> {
  const { data, error, response } = await postLegacyByokSave('/api/llm/byok', {
    body: payload,
    headers: { 'Content-Type': 'application/json' },
  });
  if (error || !response.ok || data === undefined)
    throw error ?? new Error(`/api/llm/byok save failed: ${response.status}`);
  return data;
}

export async function getCurrentByok(): Promise<ByokCurrentResult | null> {
  const { data, error, response } = await getLegacyByok('/api/llm/byok', {});
  if (response.status === 204 || data === null) return null;
  if (error || !response.ok || data === undefined)
    throw error ?? new Error(`/api/llm/byok current failed: ${response.status}`);

  if (!data.provider || !data.savedAt) return null;
  return data;
}
