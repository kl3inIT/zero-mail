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
export type RuleOrderEntryRequest = components['schemas']['RuleOrderEntryRequest'];
export type RuleReorderRequest = components['schemas']['RuleReorderRequest'];
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

type ApiMethodResult<T> = {
  data: T | null;
  error?: unknown;
  response: Response;
};

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

function unsafeHeaders(): HeadersInit {
  return { ...xsrfHeader() };
}

function throwIfFailed<T>(
  result: ApiMethodResult<T>,
  fallbackMessage: string,
): asserts result is ApiMethodResult<T> & { data: T } {
  if (result.error || !result.response.ok) {
    throw result.error ?? new Error(fallbackMessage);
  }
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
  const result = (await api.GET('/api/rules', {})) as ApiMethodResult<RuleListResponse>;
  throwIfFailed(result, `/api/rules list failed: ${result.response.status}`);
  return result.data;
}

export async function getRule(ruleId: string): Promise<RuleResponse> {
  const result = (await api.GET('/api/rules/{ruleId}', {
    params: { path: { ruleId } },
  })) as ApiMethodResult<RuleResponse>;
  throwIfFailed(result, `/api/rules/${ruleId} get failed: ${result.response.status}`);
  return result.data;
}

export async function compileRule(payload: RuleCompileRequest): Promise<RuleCompileResult> {
  const result = (await api.POST('/api/rules/compile', {
    body: payload,
    headers: jsonHeaders(),
  })) as ApiMethodResult<RuleCompileResponse>;
  throwIfFailed(result, `/api/rules/compile failed: ${result.response.status}`);
  return toRuleCompileResult(result.data);
}

export async function createRule(payload: RuleCreateRequest): Promise<RuleResponse> {
  const result = (await api.POST('/api/rules', {
    body: payload,
    headers: jsonHeaders(),
  })) as ApiMethodResult<RuleResponse>;
  throwIfFailed(result, `/api/rules create failed: ${result.response.status}`);
  return result.data;
}

export async function updateRule(
  ruleId: string,
  payload: RuleUpdateRequest,
): Promise<RuleResponse> {
  const result = (await api.PUT('/api/rules/{ruleId}', {
    params: { path: { ruleId } },
    body: payload,
    headers: jsonHeaders(),
  })) as ApiMethodResult<RuleResponse>;
  throwIfFailed(result, `/api/rules/${ruleId} update failed: ${result.response.status}`);
  return result.data;
}

export async function updateRuleEnabled(
  ruleId: string,
  payload: RuleEnabledRequest,
): Promise<RuleResponse> {
  const result = (await api.PATCH('/api/rules/{ruleId}/enabled', {
    params: { path: { ruleId } },
    body: payload,
    headers: jsonHeaders(),
  })) as ApiMethodResult<RuleResponse>;
  throwIfFailed(result, `/api/rules/${ruleId}/enabled failed: ${result.response.status}`);
  return result.data;
}

export async function reorderRules(payload: RuleReorderRequest): Promise<RuleResponse[]> {
  const result = (await api.PUT('/api/rules/reorder', {
    body: payload,
    headers: jsonHeaders(),
  })) as ApiMethodResult<RuleResponse[]>;
  throwIfFailed(result, `/api/rules/reorder failed: ${result.response.status}`);
  return result.data;
}

export async function deleteRule(ruleId: string): Promise<void> {
  const result = (await api.DELETE('/api/rules/{ruleId}', {
    params: { path: { ruleId } },
    headers: unsafeHeaders(),
  })) as ApiMethodResult<void>;
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
  const result = (await api.POST('/api/rules/{ruleId}/preview', {
    params: { path: { ruleId } },
    body: payload,
    headers: jsonHeaders(),
  })) as ApiMethodResult<RulePreviewResponse>;
  throwIfFailed(result, `/api/rules/${ruleId}/preview failed: ${result.response.status}`);
  return result.data;
}

export async function previewDraftRule(
  payload: RuleDraftPreviewRequest,
): Promise<RulePreviewResponse> {
  const result = (await api.POST('/api/rules/preview', {
    body: payload,
    headers: jsonHeaders(),
  })) as ApiMethodResult<RulePreviewResponse>;
  throwIfFailed(result, `/api/rules/preview failed: ${result.response.status}`);
  return result.data;
}

export async function listRuleTemplates(): Promise<RuleTemplateResponse[]> {
  const result = (await api.GET('/api/rules/templates', {})) as ApiMethodResult<
    RuleTemplateResponse[]
  >;
  throwIfFailed(result, `/api/rules/templates failed: ${result.response.status}`);
  return result.data;
}

export async function materializeRuleTemplate(
  templateKey: string,
): Promise<RuleTemplateMaterializationResponse> {
  const result = (await api.POST('/api/rules/templates/{templateKey}/materialize', {
    params: { path: { templateKey } },
    headers: unsafeHeaders(),
  })) as ApiMethodResult<RuleTemplateMaterializationResponse>;
  throwIfFailed(
    result,
    `/api/rules/templates/${templateKey}/materialize failed: ${result.response.status}`,
  );
  return result.data;
}

export async function materializeSelectedRuleTemplates(): Promise<RuleTemplateMaterializationResponse> {
  const result = (await api.POST('/api/rules/templates/materialize-selected', {
    headers: unsafeHeaders(),
  })) as ApiMethodResult<RuleTemplateMaterializationResponse>;
  throwIfFailed(
    result,
    `/api/rules/templates/materialize-selected failed: ${result.response.status}`,
  );
  return result.data;
}
