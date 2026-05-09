'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import type {
  ByokProvider,
  ByokProviderPreset,
  ByokSaveResult,
  ByokValidateResult,
} from '@/features/llm/api/llm-api';
import {
  byokKeys,
  useCurrentByok,
  useSaveByok,
  useValidateByok,
} from '@/features/llm/hooks/use-byok';
import { useLocalizedApiError, type ApiError } from '@/lib/api/errors';

const DEFAULT_PRESET: ByokProviderPreset = 'openrouter';
const OPENROUTER_DEFAULT_MODEL = 'openai/gpt-4o-mini';
const OPENAI_DEFAULT_MODEL = 'gpt-4o-mini';
const ANTHROPIC_DEFAULT_MODEL = 'claude-3-haiku-20240307';
const GOOGLE_GENAI_DEFAULT_MODEL = 'gemini-2.0-flash';
const DEEPSEEK_DEFAULT_MODEL = 'deepseek-chat';

const MODEL_EXAMPLES: Record<ByokProviderPreset, string[]> = {
  openrouter: ['openai/gpt-4o-mini', 'anthropic/claude-3.5-sonnet', 'google/gemini-flash-1.5'],
  openai: ['gpt-4o-mini', 'gpt-4.1-mini', 'o4-mini'],
  anthropic: ['claude-sonnet-4-20250514', 'claude-3-5-sonnet-latest', 'claude-3-haiku-20240307'],
  'google-genai': ['gemini-2.0-flash', 'gemini-1.5-flash', 'gemini-1.5-pro'],
  deepseek: ['deepseek-chat', 'deepseek-reasoner', 'deepseek-v4-flash'],
  'openai-compatible': ['gpt-4o-mini', 'llama-3.1-70b-instruct', 'qwen2.5-72b-instruct'],
  'anthropic-compatible': [
    'claude-sonnet-4-20250514',
    'claude-3-5-sonnet-latest',
    'claude-3-haiku-20240307',
  ],
};

function isByokProviderPreset(value: string): value is ByokProviderPreset {
  return (
    value === 'openrouter' ||
    value === 'openai' ||
    value === 'anthropic' ||
    value === 'google-genai' ||
    value === 'deepseek' ||
    value === 'openai-compatible' ||
    value === 'anthropic-compatible'
  );
}

function readApiKey(form: HTMLFormElement | null): string {
  const apiKeyInput = form?.elements.namedItem('apiKey');
  if (!(apiKeyInput instanceof HTMLInputElement)) return '';
  return apiKeyInput.value.trim();
}

function maybeApiError(error: unknown): ApiError | undefined {
  if (
    error !== null &&
    typeof error === 'object' &&
    typeof (error as { code?: unknown }).code === 'string'
  ) {
    return error as ApiError;
  }
  return undefined;
}

function isCompatiblePreset(preset: ByokProviderPreset): boolean {
  return preset === 'openai-compatible' || preset === 'anthropic-compatible';
}

function endpointForPreset(preset: ByokProviderPreset, endpoint: string): string | undefined {
  return isCompatiblePreset(preset) ? endpoint.trim() : undefined;
}

function defaultModelForPreset(preset: ByokProviderPreset): string {
  if (preset === 'anthropic' || preset === 'anthropic-compatible') return ANTHROPIC_DEFAULT_MODEL;
  if (preset === 'google-genai') return GOOGLE_GENAI_DEFAULT_MODEL;
  if (preset === 'deepseek') return DEEPSEEK_DEFAULT_MODEL;
  if (preset === 'openai') return OPENAI_DEFAULT_MODEL;
  return OPENROUTER_DEFAULT_MODEL;
}

function providerLabelKey(
  provider: ByokProvider,
  endpointHost?: string,
):
  | 'llm.byok.provider.anthropic'
  | 'llm.byok.provider.anthropicCompatible'
  | 'llm.byok.provider.deepseek'
  | 'llm.byok.provider.googleGenAi'
  | 'llm.byok.provider.openai'
  | 'llm.byok.provider.openrouter'
  | 'llm.byok.provider.openaiCompatible' {
  if (provider === 'anthropic') {
    return endpointHost === 'api.anthropic.com'
      ? 'llm.byok.provider.anthropic'
      : 'llm.byok.provider.anthropicCompatible';
  }
  if (provider === 'google-genai') return 'llm.byok.provider.googleGenAi';
  if (provider === 'deepseek') return 'llm.byok.provider.deepseek';
  if (endpointHost === 'openrouter.ai') return 'llm.byok.provider.openrouter';
  if (endpointHost === 'api.openai.com') return 'llm.byok.provider.openai';
  return 'llm.byok.provider.openaiCompatible';
}

function endpointLabelKey(
  preset: ByokProviderPreset,
): 'llm.byok.endpoint.openaiCompatibleLabel' | 'llm.byok.endpoint.anthropicCompatibleLabel' {
  return preset === 'anthropic-compatible'
    ? 'llm.byok.endpoint.anthropicCompatibleLabel'
    : 'llm.byok.endpoint.openaiCompatibleLabel';
}

function endpointPlaceholderKey(
  preset: ByokProviderPreset,
):
  | 'llm.byok.endpoint.openaiCompatiblePlaceholder'
  | 'llm.byok.endpoint.anthropicCompatiblePlaceholder' {
  return preset === 'anthropic-compatible'
    ? 'llm.byok.endpoint.anthropicCompatiblePlaceholder'
    : 'llm.byok.endpoint.openaiCompatiblePlaceholder';
}

function validationMessageKey(
  reason?: string,
):
  | 'llm.byok.validation.invalid'
  | 'llm.byok.validation.endpointRejected'
  | 'llm.byok.validation.connectionFailed'
  | 'llm.byok.validation.timeout'
  | 'llm.byok.validation.modelNotFound'
  | 'llm.byok.validation.upstreamRejected'
  | 'llm.byok.validation.modelRequired' {
  switch (reason) {
    case 'endpoint_rejected':
      return 'llm.byok.validation.endpointRejected';
    case 'connection_failed':
      return 'llm.byok.validation.connectionFailed';
    case 'timeout':
      return 'llm.byok.validation.timeout';
    case 'model_not_found':
      return 'llm.byok.validation.modelNotFound';
    case 'upstream_rejected':
      return 'llm.byok.validation.upstreamRejected';
    case 'model_required':
      return 'llm.byok.validation.modelRequired';
    default:
      return 'llm.byok.validation.invalid';
  }
}

export function ByokForm() {
  const t = useTranslations();
  const locale = useLocale();
  const queryClient = useQueryClient();
  const localizeApiError = useLocalizedApiError();

  const formRef = useRef<HTMLFormElement>(null);
  const currentByok = useCurrentByok();
  const validateMutation = useValidateByok();
  const saveMutation = useSaveByok();

  const [preset, setPreset] = useState<ByokProviderPreset>(DEFAULT_PRESET);
  const [endpoint, setEndpoint] = useState('');
  const [model, setModel] = useState(OPENROUTER_DEFAULT_MODEL);
  const [hasApiKey, setHasApiKey] = useState(false);
  const [hasEdited, setHasEdited] = useState(false);
  const [validationResult, setValidationResult] = useState<ByokValidateResult | null>(null);
  const [saveResult, setSaveResult] = useState<ByokSaveResult | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const isBusy = validateMutation.isPending || saveMutation.isPending;
  const endpointRequired = isCompatiblePreset(preset);
  const selectedEndpoint = endpointForPreset(preset, endpoint);
  const modelExamples = MODEL_EXAMPLES[preset];
  const canValidate =
    hasApiKey &&
    model.trim().length > 0 &&
    (!endpointRequired || endpoint.trim().length > 0) &&
    !isBusy;
  const canSave = validationResult?.ok === true && !isBusy;
  const existingConfig = currentByok.data;

  const savedAt = existingConfig?.savedAt ?? saveResult?.savedAt;
  const savedAtLabel = savedAt
    ? new Intl.DateTimeFormat(locale, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(new Date(savedAt))
    : null;

  function resetValidatedState() {
    validateMutation.reset();
    saveMutation.reset();
    setValidationResult(null);
    setValidationError(null);
    setSaveError(null);
    setSaveResult(null);
  }

  function markEdited() {
    setHasEdited(true);
    resetValidatedState();
  }

  function handleApiKeyInput(event: { currentTarget: HTMLInputElement }) {
    setHasApiKey(event.currentTarget.value.trim().length > 0);
    markEdited();
  }

  async function handleValidate() {
    const apiKey = readApiKey(formRef.current);
    if (!apiKey) {
      setHasApiKey(false);
      return;
    }

    setValidationError(null);
    setSaveError(null);
    try {
      const result = await validateMutation.mutateAsync({
        preset,
        endpoint: selectedEndpoint,
        model: model.trim(),
        apiKey,
      });
      validateMutation.reset();
      setValidationResult(result);
      if (result.ok !== true) {
        setValidationError(t(validationMessageKey(result.reason)));
      }
    } catch (error) {
      validateMutation.reset();
      const apiError = maybeApiError(error);
      setValidationResult({ ok: false });
      setValidationError(
        apiError ? localizeApiError(apiError) : t('errors.llm.byokValidateFailed'),
      );
    }
  }

  async function handleSave() {
    if (validationResult?.ok !== true) return;

    const apiKey = readApiKey(formRef.current);
    if (!apiKey) {
      setHasApiKey(false);
      resetValidatedState();
      return;
    }

    try {
      const result = await saveMutation.mutateAsync({
        preset,
        endpoint: selectedEndpoint,
        model: model.trim(),
        apiKey,
      });
      saveMutation.reset();
      formRef.current?.reset();
      setPreset(DEFAULT_PRESET);
      setEndpoint('');
      setModel(OPENROUTER_DEFAULT_MODEL);
      setHasApiKey(false);
      setHasEdited(false);
      setValidationResult(null);
      setValidationError(null);
      setSaveError(null);
      setSaveResult(result);
      await queryClient.invalidateQueries({ queryKey: byokKeys.current() });
    } catch (error) {
      saveMutation.reset();
      const apiError = maybeApiError(error);
      setSaveError(apiError ? localizeApiError(apiError) : t('llm.byok.save.error'));
    }
  }

  return (
    <Card>
      <form ref={formRef} onSubmit={(event) => event.preventDefault()}>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0 space-y-1">
              <CardTitle>{t('llm.byok.title')}</CardTitle>
              <CardDescription>{t('llm.byok.description')}</CardDescription>
            </div>
            {existingConfig && (
              <Badge variant="outline" className="border-primary/30 text-primary">
                {t('llm.byok.existing.badge')}
              </Badge>
            )}
          </div>
        </CardHeader>

        <CardContent className="space-y-4">
          <div className="bg-muted/40 text-muted-foreground rounded-lg border p-3 text-xs">
            {existingConfig ? (
              <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <span className="text-foreground font-medium">
                  {t(
                    providerLabelKey(
                      existingConfig.provider as ByokProvider,
                      existingConfig.endpointHost,
                    ),
                  )}
                </span>
                {existingConfig.endpointHost && (
                  <span className="max-w-full overflow-hidden text-ellipsis">
                    {existingConfig.endpointHost}
                  </span>
                )}
                {existingConfig.model && (
                  <span className="max-w-full overflow-hidden text-ellipsis">
                    {existingConfig.model}
                  </span>
                )}
                {savedAtLabel && <time dateTime={savedAt ?? undefined}>{savedAtLabel}</time>}
                <span>{t('llm.byok.existing.creditNote')}</span>
              </div>
            ) : (
              <div className="space-y-1">
                <p className="text-foreground font-medium">{t('llm.byok.empty.heading')}</p>
                <p>{t('llm.byok.empty.body')}</p>
              </div>
            )}
          </div>

          <div className="space-y-3">
            <div className="space-y-2">
              <Label>{t('llm.byok.provider.label')}</Label>
              <RadioGroup
                value={preset}
                onValueChange={(value) => {
                  if (!isByokProviderPreset(value)) return;
                  setPreset(value);
                  setEndpoint('');
                  setModel(defaultModelForPreset(value));
                  markEdited();
                }}
                className="grid gap-2"
                aria-label={t('llm.byok.provider.label')}
                disabled={isBusy}
              >
                <div className="space-y-2">
                  <p className="text-muted-foreground text-xs font-medium">
                    {t('llm.byok.provider.officialGroup')}
                  </p>
                  <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
                    <Label
                      htmlFor="byok-provider-openrouter"
                      className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                    >
                      <RadioGroupItem id="byok-provider-openrouter" value="openrouter" />
                      {t('llm.byok.provider.openrouter')}
                    </Label>
                    <Label
                      htmlFor="byok-provider-openai"
                      className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                    >
                      <RadioGroupItem id="byok-provider-openai" value="openai" />
                      {t('llm.byok.provider.openai')}
                    </Label>
                    <Label
                      htmlFor="byok-provider-anthropic"
                      className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                    >
                      <RadioGroupItem id="byok-provider-anthropic" value="anthropic" />
                      {t('llm.byok.provider.anthropic')}
                    </Label>
                    <Label
                      htmlFor="byok-provider-google-genai"
                      className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                    >
                      <RadioGroupItem id="byok-provider-google-genai" value="google-genai" />
                      {t('llm.byok.provider.googleGenAi')}
                    </Label>
                    <Label
                      htmlFor="byok-provider-deepseek"
                      className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                    >
                      <RadioGroupItem id="byok-provider-deepseek" value="deepseek" />
                      {t('llm.byok.provider.deepseek')}
                    </Label>
                  </div>
                </div>

                <div className="space-y-2">
                  <p className="text-muted-foreground text-xs font-medium">
                    {t('llm.byok.provider.compatibleGroup')}
                  </p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <Label
                      htmlFor="byok-provider-openai-compatible"
                      className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                    >
                      <RadioGroupItem
                        id="byok-provider-openai-compatible"
                        value="openai-compatible"
                      />
                      {t('llm.byok.provider.openaiCompatible')}
                    </Label>
                    <Label
                      htmlFor="byok-provider-anthropic-compatible"
                      className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                    >
                      <RadioGroupItem
                        id="byok-provider-anthropic-compatible"
                        value="anthropic-compatible"
                      />
                      {t('llm.byok.provider.anthropicCompatible')}
                    </Label>
                  </div>
                </div>
              </RadioGroup>
            </div>

            {endpointRequired && (
              <div className="space-y-2">
                <Label htmlFor="byok-endpoint">{t(endpointLabelKey(preset))}</Label>
                <Input
                  id="byok-endpoint"
                  name="endpoint"
                  type="url"
                  value={endpoint}
                  onChange={(event) => {
                    setEndpoint(event.currentTarget.value);
                    markEdited();
                  }}
                  placeholder={t(endpointPlaceholderKey(preset))}
                  disabled={isBusy}
                />
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="byok-model">{t('llm.byok.model.label')}</Label>
              <Input
                id="byok-model"
                name="model"
                list="byok-model-options"
                value={model}
                onChange={(event) => {
                  setModel(event.currentTarget.value);
                  markEdited();
                }}
                placeholder={t('llm.byok.model.placeholder')}
                disabled={isBusy}
              />
              <datalist id="byok-model-options">
                {modelExamples.map((modelExample) => (
                  <option key={modelExample} value={modelExample} />
                ))}
              </datalist>
            </div>

            <div className="space-y-2">
              <Label htmlFor="byok-api-key">{t('llm.byok.apiKey.label')}</Label>
              <Input
                id="byok-api-key"
                name="apiKey"
                type="password"
                autoComplete="off"
                placeholder={t('llm.byok.apiKey.placeholder')}
                disabled={isBusy}
                onChange={handleApiKeyInput}
                onInput={handleApiKeyInput}
              />
            </div>
          </div>

          {process.env.NODE_ENV === 'test' && (
            <p data-testid="form-state-snapshot" hidden>
              {JSON.stringify({
                preset,
                endpoint,
                model,
                hasApiKey,
                validationOk: validationResult?.ok === true,
              })}
            </p>
          )}

          <div aria-live="polite" className="space-y-3">
            {hasEdited && existingConfig && (
              <Alert variant="warning">
                <AlertDescription>{t('llm.byok.existing.replaceNotice')}</AlertDescription>
              </Alert>
            )}

            {validationResult?.ok === true && (
              <Alert
                role="status"
                data-testid="byok-validation-success-alert"
                className="border-green/30 bg-green-soft/60 text-green dark:border-green/40 dark:bg-green-soft/40"
              >
                <AlertTitle>{t('llm.byok.validation.success')}</AlertTitle>
              </Alert>
            )}

            {validationError && (
              <Alert variant="destructive">
                <AlertTitle>{t('errors.llm.byokValidateFailed')}</AlertTitle>
                <AlertDescription>{validationError}</AlertDescription>
              </Alert>
            )}

            {saveResult?.ok === true && (
              <Alert role="status">
                <AlertDescription>{t('llm.byok.save.success')}</AlertDescription>
              </Alert>
            )}

            {saveError && (
              <Alert variant="destructive">
                <AlertDescription>{saveError}</AlertDescription>
              </Alert>
            )}
          </div>
        </CardContent>

        <CardFooter className="flex flex-wrap gap-3">
          <Button type="button" onClick={handleValidate} disabled={!canValidate}>
            {validateMutation.isPending ? t('llm.byok.validating') : t('llm.byok.validateCta')}
          </Button>
          <Button type="button" variant="secondary" onClick={handleSave} disabled={!canSave}>
            {saveMutation.isPending ? t('llm.byok.saving') : t('llm.byok.saveCta')}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
