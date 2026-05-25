import { api } from '@/lib/api/client';
import type { components, paths } from '@/lib/api/schema';

type GeneratedRuleCatalogExamplesResponse = components['schemas']['RuleCatalogExamplesResponse'];
type GeneratedRuleCatalogPersonaResponse = components['schemas']['RuleCatalogPersonaResponse'];
type GeneratedRuleCatalogExampleResponse = components['schemas']['RuleCatalogExampleResponse'];

export type RuleCatalogExampleResponse = Omit<GeneratedRuleCatalogExampleResponse, 'sourceRef'> & {
  sourceRef?: string;
};
export type RuleCatalogPersonaResponse = Omit<GeneratedRuleCatalogPersonaResponse, 'examples'> & {
  examples: RuleCatalogExampleResponse[];
};
export type RuleCatalogExamplesResponse = Omit<GeneratedRuleCatalogExamplesResponse, 'personas'> & {
  personas: RuleCatalogPersonaResponse[];
};
export type RuleCatalogActionsResponse = components['schemas']['RuleCatalogActionsResponse'];
export type RuleCatalogActionDescriptorResponse =
  components['schemas']['RuleCatalogActionDescriptorResponse'];
export type RuleAutomationSettingsResponse =
  components['schemas']['RuleAutomationSettingsResponse'];
export type RuleAutomationSettingsUpdateRequest =
  components['schemas']['RuleAutomationSettingsUpdateRequest'];

type RuleCatalogExamplesOperation = paths['/api/rules/catalog/examples']['get'];
type RuleCatalogActionsOperation = paths['/api/rules/catalog/actions']['get'];

type RuleCatalogExamplesQuery = NonNullable<RuleCatalogExamplesOperation['parameters']['query']>;
type RuleCatalogActionsQuery = NonNullable<RuleCatalogActionsOperation['parameters']['query']>;

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export function toRuleCatalogLocale(locale: string): 'en' | 'vi' {
  return locale.toLowerCase().startsWith('vi') ? 'vi' : 'en';
}

export async function listRuleCatalogExamples(
  locale: string,
): Promise<RuleCatalogExamplesResponse> {
  const query: RuleCatalogExamplesQuery = { locale: toRuleCatalogLocale(locale) };
  const result = await api.GET('/api/rules/catalog/examples', {
    params: { query },
  });
  return unwrap(result, `/api/rules/catalog/examples failed: ${result.response.status}`);
}

export async function listRuleCatalogActions(locale: string): Promise<RuleCatalogActionsResponse> {
  const query: RuleCatalogActionsQuery = { locale: toRuleCatalogLocale(locale) };
  const result = await api.GET('/api/rules/catalog/actions', {
    params: { query },
  });
  return unwrap(result, `/api/rules/catalog/actions failed: ${result.response.status}`);
}

export async function getRuleAutomationSettings(): Promise<RuleAutomationSettingsResponse> {
  const result = await api.GET('/api/rules/settings/automation', {});
  return unwrap(result, `/api/rules/settings/automation failed: ${result.response.status}`);
}

export async function updateRuleAutomationSettings(
  payload: RuleAutomationSettingsUpdateRequest,
): Promise<RuleAutomationSettingsResponse> {
  const result = await api.PUT('/api/rules/settings/automation', {
    body: payload,
  });
  return unwrap(result, `/api/rules/settings/automation update failed: ${result.response.status}`);
}
