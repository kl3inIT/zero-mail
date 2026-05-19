import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type RuleResponse = components['schemas']['RuleResponse'];
export type RuleListResponse = components['schemas']['RulesListResponse'];
export type RuleTemplateResponse = components['schemas']['RuleTemplateResponse'];
export type RuleTemplateMaterializationResponse =
  components['schemas']['RuleTemplateMaterializationResponse'];
export type RuleCompileRequest = components['schemas']['RuleCompileRequest'];
export type RuleCompileResponse = components['schemas']['RuleCompileResponse'];
export type RuleCompiledPayloadResponse = components['schemas']['CompiledPayloadResponse'];
export type RuleCompiledPayloadRequest = components['schemas']['CompiledPayloadRequest'];
export type RuleCreateRequest = components['schemas']['RuleCreateRequest'];
export type RuleUpdateRequest = components['schemas']['RuleUpdateRequest'];
export type RuleEnabledRequest = components['schemas']['RuleEnabledRequest'];
export type RulePreviewRequest = components['schemas']['RulePreviewRequest'];
export type RuleDraftPreviewRequest = components['schemas']['RuleDraftPreviewRequest'];
export type RulePreviewResponse = components['schemas']['RulePreviewResponse'];

export type RuleCompileCompiledResult = {
  status: 'compiled';
  compiled: RuleCompiledPayloadResponse;
};

export type RuleCompileClarificationResult = {
  status: 'clarificationRequired';
  clarification: NonNullable<RuleCompileResponse['clarification']>;
  priorCompileContext: string;
};

export type RuleCompileInvalidResult = {
  status: 'invalid';
  invalid: NonNullable<RuleCompileResponse['invalid']>;
};

export type RuleCompileResult =
  | RuleCompileCompiledResult
  | RuleCompileClarificationResult
  | RuleCompileInvalidResult;

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

function unsafeHeaders(): HeadersInit {
  return { ...xsrfHeader() };
}

/**
 * Narrow the discriminated `{ data, error, response }` shape returned by
 * openapi-fetch into a non-error data branch. Throws on transport
 * failure or non-2xx so callers can `await` and use the result directly.
 *
 * Throwing the structured `error` (a typed ApiError) keeps the existing
 * client-side error-localization contract: hooks switch on `error.code`,
 * never on a thrown Error message.
 */
function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export function toRuleCompileResult(response: RuleCompileResponse): RuleCompileResult {
  if (response.status === 'compiled' && response.compiled) {
    return { status: 'compiled', compiled: response.compiled };
  }

  if (response.status === 'clarificationRequired' && response.clarification?.question) {
    return {
      status: 'clarificationRequired',
      clarification: response.clarification,
      priorCompileContext: response.clarification.question,
    };
  }

  if (response.status === 'invalid') {
    return { status: 'invalid', invalid: response.invalid ?? { reason: 'invalid' } };
  }

  return { status: 'invalid', invalid: { reason: 'malformed_response' } };
}

export function compiledResponseToRequest(
  compiled: RuleCompiledPayloadResponse,
): RuleCompiledPayloadRequest {
  return {
    status: compiled.status ?? 'compiled',
    sourceLanguage: compiled.sourceLanguage,
    schemaVersion: compiled.schemaVersion,
    matcherAst: compiled.matcherAst,
    actionIntents: compiled.actionIntents,
  };
}

export async function listRules(): Promise<RuleListResponse> {
  const result = await api.GET('/api/rules', {});
  return unwrap(result, `/api/rules list failed: ${result.response.status}`);
}

export async function getRule(ruleId: string): Promise<RuleResponse> {
  const result = await api.GET('/api/rules/{ruleId}', {
    params: { path: { ruleId } },
  });
  return unwrap(result, `/api/rules/${ruleId} get failed: ${result.response.status}`);
}

export async function compileRule(payload: RuleCompileRequest): Promise<RuleCompileResult> {
  const result = await api.POST('/api/rules/compile', {
    body: payload,
    headers: jsonHeaders(),
  });
  const data = unwrap(result, `/api/rules/compile failed: ${result.response.status}`);
  return toRuleCompileResult(data);
}

export async function createRule(payload: RuleCreateRequest): Promise<RuleResponse> {
  const result = await api.POST('/api/rules', {
    body: payload,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/rules create failed: ${result.response.status}`);
}

export async function updateRule(
  ruleId: string,
  payload: RuleUpdateRequest,
): Promise<RuleResponse> {
  const result = await api.PUT('/api/rules/{ruleId}', {
    params: { path: { ruleId } },
    body: payload,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/rules/${ruleId} update failed: ${result.response.status}`);
}

export async function updateRuleEnabled(
  ruleId: string,
  payload: RuleEnabledRequest,
): Promise<RuleResponse> {
  const result = await api.PATCH('/api/rules/{ruleId}/enabled', {
    params: { path: { ruleId } },
    body: payload,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/rules/${ruleId}/enabled failed: ${result.response.status}`);
}

export async function deleteRule(ruleId: string): Promise<void> {
  const result = await api.DELETE('/api/rules/{ruleId}', {
    params: { path: { ruleId } },
    headers: unsafeHeaders(),
  });
  if (result.error || !result.response.ok) {
    throw (
      result.error ?? new Error(`/api/rules/${ruleId} delete failed: ${result.response.status}`)
    );
  }
}

export async function previewSavedRule(
  ruleId: string,
  payload: RulePreviewRequest,
): Promise<RulePreviewResponse> {
  const result = await api.POST('/api/rules/{ruleId}/preview', {
    params: { path: { ruleId } },
    body: payload,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/rules/${ruleId}/preview failed: ${result.response.status}`);
}

export async function previewDraftRule(
  payload: RuleDraftPreviewRequest,
): Promise<RulePreviewResponse> {
  const result = await api.POST('/api/rules/preview', {
    body: payload,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/rules/preview failed: ${result.response.status}`);
}

export type RuleEnabledPreviewRequest = components['schemas']['RuleEnabledPreviewRequest'];

export async function previewAllEnabledRules(
  payload: RuleEnabledPreviewRequest,
): Promise<RulePreviewResponse> {
  const result = await api.POST('/api/rules/preview-enabled', {
    headers: jsonHeaders(),
    body: payload,
  });
  return unwrap(result, `/api/rules/preview-enabled failed: ${result.response.status}`);
}

export type RuleCustomPreviewRequest = components['schemas']['RuleCustomPreviewRequest'];
export type RuleCustomPreviewEntry =
  components['schemas']['RuleCustomPreviewResponse']['entries'][number];
export type RuleCustomPreviewResponse = components['schemas']['RuleCustomPreviewResponse'];

export async function previewCustomMail(
  payload: RuleCustomPreviewRequest,
): Promise<RuleCustomPreviewResponse> {
  const result = await api.POST('/api/rules/preview-custom', {
    headers: jsonHeaders(),
    body: payload,
  });
  return unwrap(result, `/api/rules/preview-custom failed: ${result.response.status}`);
}

export async function listRuleTemplates(): Promise<RuleTemplateResponse[]> {
  const result = await api.GET('/api/rules/templates', {});
  return unwrap(result, `/api/rules/templates failed: ${result.response.status}`);
}

export async function materializeRuleTemplate(
  templateKey: string,
): Promise<RuleTemplateMaterializationResponse> {
  const result = await api.POST('/api/rules/templates/{templateKey}/materialize', {
    params: { path: { templateKey } },
    headers: unsafeHeaders(),
  });
  return unwrap(
    result,
    `/api/rules/templates/${templateKey}/materialize failed: ${result.response.status}`,
  );
}
