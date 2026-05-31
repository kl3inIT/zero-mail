'use client';

import { CheckCircle2, KeyRound, Pencil, Trash2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useReducer, type FormEvent } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Spinner } from '@/components/ui/spinner';
import { Switch } from '@/components/ui/switch';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { ByokProvider, ByokResponse, ByokTestResult } from '@/features/ai/api/byok-api';
import { BYOK_PROVIDER_OPTIONS, DEFAULT_PROVIDER } from '@/features/ai/api/byok-providers';
import { ConfirmDialog } from '@/features/ai/components/ConfirmDialog';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';
import { useActivateByok } from '@/features/ai/hooks/useActivateByok';
import { useAiCost } from '@/features/ai/hooks/useAiCost';
import { useByok } from '@/features/ai/hooks/useByok';
import { useDeleteByok } from '@/features/ai/hooks/useDeleteByok';
import { useSaveByok } from '@/features/ai/hooks/useSaveByok';
import { useSelectByokModel } from '@/features/ai/hooks/useSelectByokModel';
import { useTestByokConnection } from '@/features/ai/hooks/useTestByokConnection';

export function AiProviderSection() {
  const t = useTranslations();
  const locale = useLocale();
  const byok = useByok();
  const aiCost = useAiCost('7d');

  if (byok.isError) throw byok.error;

  const costAmount = formatUsd(locale, aiCost.data?.usd ?? 0);
  const row = byok.data ?? null;
  const rowKey = row ? `${row.provider}:${row.baseUrl}:${row.lastFourChars}` : 'empty';

  return (
    <section className="space-y-4" aria-labelledby="ai-section-provider">
      <SectionHeader
        id="ai-section-provider"
        title={t('ai.sections.provider.title')}
        helperText={t('ai.sections.provider.helper')}
        icon={KeyRound}
      />
      <SettingCard
        title={t('ai.byok.title')}
        description={t('ai.byok.titleDescription')}
        icon={KeyRound}
      >
        {byok.isPending ? (
          <div className="text-muted-foreground flex items-center gap-2 text-sm">
            <Spinner />
            {t('ai.byok.empty.title')}
          </div>
        ) : (
          <ByokCard key={rowKey} initialRow={row} />
        )}
      </SettingCard>
      <p className="text-muted-foreground px-1 text-sm">
        {t('ai.byok.costFooter', { amount: costAmount })}
      </p>
    </section>
  );
}

type ByokFormState = {
  savedRow: ByokResponse | null;
  provider: ByokProvider;
  baseUrl: string;
  apiKey: string;
  editingKey: boolean;
  models: string[];
  modelId: string;
  active: boolean;
  lastTestResult: ByokTestResult | null;
  lastTestedAt: string | null;
};

type ByokFormAction =
  | { type: 'providerChanged'; provider: ByokProvider }
  | { type: 'baseUrlChanged'; baseUrl: string }
  | { type: 'apiKeyChanged'; apiKey: string }
  | { type: 'keyReplaceRequested' }
  | { type: 'modelIdSet'; modelId: string }
  | { type: 'activeSet'; active: boolean }
  | { type: 'savedRowApplied'; row: ByokResponse }
  | { type: 'saveCompleted'; row: ByokResponse }
  | { type: 'tested'; result: ByokTestResult; models: string[]; testedAt: string }
  | { type: 'deleted' };

function createInitialByokFormState(initialRow: ByokResponse | null): ByokFormState {
  return {
    savedRow: initialRow,
    provider: initialRow?.provider ?? DEFAULT_PROVIDER.provider,
    baseUrl: initialRow?.baseUrl ?? DEFAULT_PROVIDER.defaultBaseUrl,
    apiKey: '',
    editingKey: initialRow === null,
    models: initialRow?.modelId ? [initialRow.modelId] : [],
    modelId: initialRow?.modelId ?? '',
    active: initialRow?.active ?? false,
    lastTestResult: initialRow?.lastTestResult ?? null,
    lastTestedAt: initialRow?.lastTestedAt ?? null,
  };
}

function applySavedRowToState(state: ByokFormState, row: ByokResponse): ByokFormState {
  return {
    ...state,
    savedRow: row,
    provider: row.provider,
    baseUrl: row.baseUrl,
    modelId: row.modelId ?? '',
    active: row.active,
    lastTestResult: row.lastTestResult ?? null,
    lastTestedAt: row.lastTestedAt ?? null,
  };
}

function byokFormReducer(state: ByokFormState, action: ByokFormAction): ByokFormState {
  switch (action.type) {
    case 'providerChanged':
      return {
        ...state,
        provider: action.provider,
        baseUrl: defaultBaseUrlFor(action.provider),
        apiKey: '',
        editingKey: true,
        models: [],
        modelId: '',
        lastTestResult: null,
        lastTestedAt: null,
        active: false,
      };
    case 'baseUrlChanged':
      return { ...state, baseUrl: action.baseUrl };
    case 'apiKeyChanged':
      return { ...state, apiKey: action.apiKey };
    case 'keyReplaceRequested':
      return { ...state, apiKey: '', editingKey: true };
    case 'modelIdSet':
      return { ...state, modelId: action.modelId };
    case 'activeSet':
      return { ...state, active: action.active };
    case 'savedRowApplied':
      return applySavedRowToState(state, action.row);
    case 'saveCompleted':
      return {
        ...applySavedRowToState(state, action.row),
        apiKey: '',
        editingKey: false,
        models: [],
      };
    case 'tested': {
      const succeeded = action.result === 'OK';
      return {
        ...state,
        lastTestResult: action.result,
        lastTestedAt: action.testedAt,
        savedRow: state.savedRow
          ? {
              ...state.savedRow,
              active: succeeded ? state.savedRow.active : false,
              lastTestResult: action.result,
              lastTestedAt: action.testedAt,
              modelId: succeeded ? state.savedRow.modelId : undefined,
            }
          : state.savedRow,
        models: succeeded ? action.models : [],
        modelId: succeeded ? state.modelId : '',
        active: succeeded ? state.active : false,
      };
    }
    case 'deleted':
      return createInitialByokFormState(null);
    default:
      return state;
  }
}

function ByokCard({ initialRow }: { initialRow: ByokResponse | null }) {
  const t = useTranslations();
  const locale = useLocale();
  const saveByok = useSaveByok();
  const testConnection = useTestByokConnection();
  const selectModel = useSelectByokModel();
  const activateByok = useActivateByok();
  const deleteByok = useDeleteByok();
  const [state, dispatch] = useReducer(byokFormReducer, initialRow, createInitialByokFormState);
  const {
    savedRow,
    provider,
    baseUrl,
    apiKey,
    editingKey,
    models,
    modelId,
    active,
    lastTestResult,
    lastTestedAt,
  } = state;

  const hasSavedRow = savedRow !== null;
  const formDirty =
    !savedRow ||
    provider !== savedRow.provider ||
    baseUrl !== savedRow.baseUrl ||
    apiKey.trim().length > 0;
  const busy =
    saveByok.isPending ||
    testConnection.isPending ||
    selectModel.isPending ||
    activateByok.isPending ||
    deleteByok.isPending;
  const modelOptions = uniqueModelOptions(models, modelId);
  const canSelectModel = !formDirty && lastTestResult === 'OK' && modelOptions.length > 0;
  const activeGateSatisfied =
    hasSavedRow && !formDirty && modelId.trim().length > 0 && lastTestResult === 'OK';
  const testDisabled = !hasSavedRow || formDirty || busy;
  const saveDisabled = busy || !provider || !baseUrl.trim() || !apiKey.trim();
  const modelPlaceholder = hasSavedRow ? t('ai.byok.model.empty') : t('ai.byok.model.saveFirst');

  function handleProviderChange(nextProvider: ByokProvider) {
    dispatch({ type: 'providerChanged', provider: nextProvider });
  }

  async function handleSave(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const savedByok = await saveByok.mutateAsync({
      provider,
      baseUrl: baseUrl.trim(),
      apiKey: apiKey.trim(),
    });
    dispatch({ type: 'saveCompleted', row: savedByok });
  }

  async function handleTestConnection() {
    const result = await testConnection.mutateAsync();
    const testedAt = new Date().toISOString();
    dispatch({
      type: 'tested',
      result: result.result,
      models: result.models ?? [],
      testedAt,
    });
  }

  async function handleModelChange(nextModelId: string) {
    const previousModelId = modelId;
    dispatch({ type: 'modelIdSet', modelId: nextModelId });
    try {
      const savedByok = await selectModel.mutateAsync(nextModelId);
      dispatch({ type: 'savedRowApplied', row: savedByok });
    } catch (modelSelectionError) {
      dispatch({ type: 'modelIdSet', modelId: previousModelId });
      throw modelSelectionError;
    }
  }

  async function handleActiveChange(nextActive: boolean) {
    const previousActive = active;
    dispatch({ type: 'activeSet', active: nextActive });
    try {
      const savedByok = await activateByok.mutateAsync(nextActive);
      dispatch({ type: 'savedRowApplied', row: savedByok });
    } catch (activationError) {
      dispatch({ type: 'activeSet', active: previousActive });
      throw activationError;
    }
  }

  async function handleDelete() {
    await deleteByok.mutateAsync();
    dispatch({ type: 'deleted' });
  }

  const statusBadge = lastTestResult ? (
    <div className="flex flex-wrap items-center gap-2" aria-live="polite">
      <Badge
        variant={lastTestResult === 'OK' ? 'outline' : 'destructive'}
        className={
          lastTestResult === 'OK'
            ? 'border-green/30 bg-green-soft text-green h-7 px-2.5 text-sm font-semibold'
            : 'h-7 px-2.5 text-sm font-semibold'
        }
      >
        {lastTestResult === 'OK' ? <CheckCircle2 className="size-3.5" aria-hidden="true" /> : null}
        {lastTestResult === 'OK' ? t('ai.byok.status.ok') : t('ai.byok.status.fail')}
      </Badge>
      {lastTestResult === 'OK' ? null : (
        <span className="text-muted-foreground text-sm">{lastTestResult}</span>
      )}
      {lastTestedAt ? (
        <span className="text-muted-foreground text-sm">{formatTime(locale, lastTestedAt)}</span>
      ) : null}
    </div>
  ) : hasSavedRow ? (
    <p className="text-muted-foreground text-sm">{t('ai.byok.model.empty')}</p>
  ) : (
    <div className="space-y-1 text-sm">
      <p className="font-medium">{t('ai.byok.empty.title')}</p>
      <p className="text-muted-foreground">{t('ai.byok.empty.body')}</p>
    </div>
  );

  const activeSwitch = (
    <Switch
      aria-label={t('ai.byok.active.title')}
      checked={active}
      disabled={!activeGateSatisfied || busy}
      onCheckedChange={(nextActive) => void handleActiveChange(nextActive)}
    />
  );

  const testButton = (
    <Button
      type="button"
      variant="outline"
      disabled={testDisabled}
      onClick={() => void handleTestConnection()}
    >
      {testConnection.isPending ? <Spinner /> : null}
      {t('ai.actions.testConnection')}
    </Button>
  );

  return (
    <form className="space-y-4" onSubmit={handleSave}>
      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="byok-provider">{t('ai.byok.provider.label')}</Label>
          <Select
            value={provider}
            onValueChange={(nextProvider) => {
              if (nextProvider) handleProviderChange(nextProvider as ByokProvider);
            }}
          >
            <SelectTrigger id="byok-provider" className="w-full" disabled={busy}>
              <SelectValue>{providerLabel(provider)}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {BYOK_PROVIDER_OPTIONS.map((providerOption) => (
                <SelectItem key={providerOption.provider} value={providerOption.provider}>
                  {providerLabel(providerOption.provider)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="byok-base-url">{t('ai.byok.baseUrl.label')}</Label>
          <Input
            id="byok-base-url"
            type="url"
            value={baseUrl}
            onChange={(changeEvent) =>
              dispatch({ type: 'baseUrlChanged', baseUrl: changeEvent.target.value })
            }
            maxLength={255}
            disabled={busy}
            required
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="byok-api-key">{t('ai.byok.key.label')}</Label>
          {hasSavedRow && !editingKey ? (
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <div className="border-input bg-muted/40 flex h-8 min-w-0 flex-1 items-center rounded-lg border px-2.5 text-sm">
                <span className="truncate">
                  {t('ai.byok.key.masked', { lastFourChars: savedRow.lastFourChars })}
                </span>
              </div>
              <ConfirmDialog
                title={t('ai.actions.replaceKey')}
                description={t('ai.byok.replace.confirm', { provider: providerLabel(provider) })}
                confirmLabel={t('ai.actions.replaceKey')}
                onConfirm={() => dispatch({ type: 'keyReplaceRequested' })}
                trigger={
                  <Button type="button" variant="outline">
                    <Pencil className="size-4" aria-hidden="true" />
                    {t('ai.actions.replaceKey')}
                  </Button>
                }
              />
            </div>
          ) : (
            <Input
              id="byok-api-key"
              type="password"
              value={apiKey}
              onChange={(changeEvent) =>
                dispatch({ type: 'apiKeyChanged', apiKey: changeEvent.target.value })
              }
              disabled={busy}
              autoComplete="off"
              required={!hasSavedRow || editingKey}
            />
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="byok-model">{t('ai.byok.model.label')}</Label>
          <Select
            value={modelId}
            onValueChange={(nextModelId) => {
              if (nextModelId) void handleModelChange(nextModelId);
            }}
          >
            <SelectTrigger id="byok-model" className="w-full" disabled={!canSelectModel || busy}>
              <SelectValue>{modelId || modelPlaceholder}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {modelOptions.map((modelOption) => (
                <SelectItem key={modelOption} value={modelOption}>
                  {modelOption}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
        {statusBadge}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">{t('ai.byok.active.title')}</span>
            {activeGateSatisfied ? (
              activeSwitch
            ) : (
              <Tooltip>
                <TooltipTrigger render={<span className="inline-flex" />}>
                  {activeSwitch}
                </TooltipTrigger>
                <TooltipContent>{t('ai.byok.active.disabledTooltip')}</TooltipContent>
              </Tooltip>
            )}
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
        {hasSavedRow ? (
          <ConfirmDialog
            title={t('ai.actions.delete')}
            description={t('ai.byok.delete.confirm')}
            confirmLabel={t('ai.actions.delete')}
            onConfirm={handleDelete}
            trigger={
              <Button type="button" variant="outline" disabled={busy}>
                <Trash2 className="size-4" aria-hidden="true" />
                {t('ai.actions.delete')}
              </Button>
            }
          />
        ) : null}
        {testDisabled && !busy ? (
          <Tooltip>
            <TooltipTrigger render={<span className="inline-flex" />}>{testButton}</TooltipTrigger>
            <TooltipContent>{t('ai.byok.test.disabledTooltip')}</TooltipContent>
          </Tooltip>
        ) : (
          testButton
        )}
        <Button type="submit" disabled={saveDisabled}>
          {saveByok.isPending ? <Spinner /> : null}
          {t('ai.actions.save')}
        </Button>
      </div>
    </form>
  );
}

function defaultBaseUrlFor(provider: ByokProvider): string {
  return (
    BYOK_PROVIDER_OPTIONS.find((providerOption) => providerOption.provider === provider)
      ?.defaultBaseUrl ?? DEFAULT_PROVIDER.defaultBaseUrl
  );
}

function providerLabel(provider: ByokProvider): string {
  switch (provider) {
    case 'ANTHROPIC':
      return 'Anthropic';
    case 'GOOGLE':
      return 'Google';
    case 'DEEPSEEK':
      return 'DeepSeek';
    case 'OPENAI':
    default:
      return 'OpenAI';
  }
}

function uniqueModelOptions(models: string[], selectedModelId: string): string[] {
  return Array.from(new Set([selectedModelId, ...models].filter(Boolean)));
}

const usdFormatters = new Map<string, Intl.NumberFormat>();
const timeFormatters = new Map<string, Intl.DateTimeFormat>();

function resolveLocale(locale: string): string {
  return locale === 'vi' ? 'vi-VN' : 'en-US';
}

function formatUsd(locale: string, usd: number): string {
  const resolvedLocale = resolveLocale(locale);
  let formatter = usdFormatters.get(resolvedLocale);
  if (!formatter) {
    formatter = new Intl.NumberFormat(resolvedLocale, { style: 'currency', currency: 'USD' });
    usdFormatters.set(resolvedLocale, formatter);
  }
  return formatter.format(usd);
}

function formatTime(locale: string, value: string): string {
  const resolvedLocale = resolveLocale(locale);
  let formatter = timeFormatters.get(resolvedLocale);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(resolvedLocale, { hour: '2-digit', minute: '2-digit' });
    timeFormatters.set(resolvedLocale, formatter);
  }
  return formatter.format(new Date(value));
}
