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
import type { ByokProvider, ByokSaveResult, ByokValidateResult } from '@/features/llm/api/llm-api';
import {
  byokKeys,
  useCurrentByok,
  useSaveByok,
  useValidateByok,
} from '@/features/llm/hooks/use-byok';
import { useLocalizedApiError, type ApiError } from '@/lib/api/errors';

const DEFAULT_PROVIDER: ByokProvider = 'openai-compatible';
const OPENAI_COMPATIBLE_ENDPOINT = 'https://openrouter.ai/api/v1';

function isByokProvider(value: string): value is ByokProvider {
  return value === 'anthropic' || value === 'openai-compatible';
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

function providerLabelKey(
  provider: ByokProvider,
): 'llm.byok.provider.anthropic' | 'llm.byok.provider.openaiCompatible' {
  return provider === 'anthropic'
    ? 'llm.byok.provider.anthropic'
    : 'llm.byok.provider.openaiCompatible';
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

  const [provider, setProvider] = useState<ByokProvider>(DEFAULT_PROVIDER);
  const [endpoint, setEndpoint] = useState(OPENAI_COMPATIBLE_ENDPOINT);
  const [hasApiKey, setHasApiKey] = useState(false);
  const [hasEdited, setHasEdited] = useState(false);
  const [validationResult, setValidationResult] = useState<ByokValidateResult | null>(null);
  const [saveResult, setSaveResult] = useState<ByokSaveResult | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const isBusy = validateMutation.isPending || saveMutation.isPending;
  const endpointRequired = provider === 'openai-compatible';
  const canValidate = hasApiKey && (!endpointRequired || endpoint.trim().length > 0) && !isBusy;
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
        provider,
        endpoint: endpointRequired ? endpoint.trim() : undefined,
        apiKey,
      });
      validateMutation.reset();
      setValidationResult(result);
      if (result.ok !== true) {
        setValidationError(t('llm.byok.validation.invalid'));
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
        provider,
        endpoint: endpointRequired ? endpoint.trim() : undefined,
        apiKey,
      });
      saveMutation.reset();
      formRef.current?.reset();
      setProvider(DEFAULT_PROVIDER);
      setEndpoint(OPENAI_COMPATIBLE_ENDPOINT);
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

  const visibleModels = validationResult?.models?.slice(0, 5) ?? [];
  const hiddenModelCount = Math.max(
    (validationResult?.models?.length ?? 0) - visibleModels.length,
    0,
  );

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
                  {t(providerLabelKey(existingConfig.provider as ByokProvider))}
                </span>
                {existingConfig.endpointHost && (
                  <span className="max-w-full overflow-hidden text-ellipsis">
                    {existingConfig.endpointHost}
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
                value={provider}
                onValueChange={(value) => {
                  if (!isByokProvider(value)) return;
                  setProvider(value);
                  markEdited();
                }}
                className="grid gap-2"
                aria-label={t('llm.byok.provider.label')}
                disabled={isBusy}
              >
                <Label
                  htmlFor="byok-provider-openai-compatible"
                  className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                >
                  <RadioGroupItem id="byok-provider-openai-compatible" value="openai-compatible" />
                  {t('llm.byok.provider.openaiCompatible')}
                </Label>
                <Label
                  htmlFor="byok-provider-anthropic"
                  className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                >
                  <RadioGroupItem id="byok-provider-anthropic" value="anthropic" />
                  {t('llm.byok.provider.anthropic')}
                </Label>
              </RadioGroup>
            </div>

            {endpointRequired && (
              <div className="space-y-2">
                <Label htmlFor="byok-endpoint">{t('llm.byok.endpoint.label')}</Label>
                <Input
                  id="byok-endpoint"
                  name="endpoint"
                  type="url"
                  value={endpoint}
                  onChange={(event) => {
                    setEndpoint(event.currentTarget.value);
                    markEdited();
                  }}
                  placeholder={t('llm.byok.endpoint.placeholder')}
                  disabled={isBusy}
                />
              </div>
            )}

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
                provider,
                endpoint,
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
              <Alert role="status">
                <AlertTitle>{t('llm.byok.validation.success')}</AlertTitle>
                {visibleModels.length > 0 && (
                  <AlertDescription>
                    <span className="flex flex-wrap gap-1.5 pt-1">
                      {visibleModels.map((model) => (
                        <Badge key={model} variant="outline">
                          {model}
                        </Badge>
                      ))}
                      {hiddenModelCount > 0 && (
                        <Badge variant="secondary">
                          {t('llm.byok.validation.moreModels', { count: hiddenModelCount })}
                        </Badge>
                      )}
                    </span>
                  </AlertDescription>
                )}
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
