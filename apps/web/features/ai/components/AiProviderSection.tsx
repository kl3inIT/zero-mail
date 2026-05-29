'use client';

import { CheckCircle2, KeyRound, Pencil, Trash2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState, type FormEvent } from 'react';

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

export const BYOK_PROVIDER_OPTIONS: Array<{
  provider: ByokProvider;
  defaultBaseUrl: string;
}> = [
  { provider: 'OPENAI', defaultBaseUrl: 'https://api.openai.com/v1' },
  { provider: 'ANTHROPIC', defaultBaseUrl: 'https://api.anthropic.com/v1' },
  { provider: 'GOOGLE', defaultBaseUrl: 'https://generativelanguage.googleapis.com/v1beta' },
  { provider: 'DEEPSEEK', defaultBaseUrl: 'https://api.deepseek.com/v1' },
];

const DEFAULT_PROVIDER = BYOK_PROVIDER_OPTIONS[0];

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

function ByokCard({ initialRow }: { initialRow: ByokResponse | null }) {
  const t = useTranslations();
  const locale = useLocale();
  const saveByok = useSaveByok();
  const testConnection = useTestByokConnection();
  const selectModel = useSelectByokModel();
  const activateByok = useActivateByok();
  const deleteByok = useDeleteByok();
  const [savedRow, setSavedRow] = useState(initialRow);
  const [provider, setProvider] = useState<ByokProvider>(
    initialRow?.provider ?? DEFAULT_PROVIDER.provider,
  );
  const [baseUrl, setBaseUrl] = useState(initialRow?.baseUrl ?? DEFAULT_PROVIDER.defaultBaseUrl);
  const [apiKey, setApiKey] = useState('');
  const [editingKey, setEditingKey] = useState(initialRow === null);
  const [models, setModels] = useState<string[]>(initialRow?.modelId ? [initialRow.modelId] : []);
  const [modelId, setModelId] = useState(initialRow?.modelId ?? '');
  const [active, setActive] = useState(initialRow?.active ?? false);
  const [lastTestResult, setLastTestResult] = useState<ByokTestResult | null>(
    initialRow?.lastTestResult ?? null,
  );
  const [lastTestedAt, setLastTestedAt] = useState(initialRow?.lastTestedAt ?? null);

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
    setProvider(nextProvider);
    setBaseUrl(defaultBaseUrlFor(nextProvider));
    setApiKey('');
    setEditingKey(true);
    setModels([]);
    setModelId('');
    setLastTestResult(null);
    setLastTestedAt(null);
    setActive(false);
  }

  function applySavedRow(nextRow: ByokResponse) {
    setSavedRow(nextRow);
    setProvider(nextRow.provider);
    setBaseUrl(nextRow.baseUrl);
    setModelId(nextRow.modelId ?? '');
    setActive(nextRow.active);
    setLastTestResult(nextRow.lastTestResult ?? null);
    setLastTestedAt(nextRow.lastTestedAt ?? null);
  }

  async function handleSave(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const savedByok = await saveByok.mutateAsync({
      provider,
      baseUrl: baseUrl.trim(),
      apiKey: apiKey.trim(),
    });
    applySavedRow(savedByok);
    setApiKey('');
    setEditingKey(false);
    setModels([]);
  }

  async function handleTestConnection() {
    const result = await testConnection.mutateAsync();
    const testedAt = new Date().toISOString();
    setLastTestResult(result.result);
    setLastTestedAt(testedAt);
    setSavedRow((currentRow) =>
      currentRow
        ? {
            ...currentRow,
            active: result.result === 'OK' ? currentRow.active : false,
            lastTestResult: result.result,
            lastTestedAt: testedAt,
            modelId: result.result === 'OK' ? currentRow.modelId : undefined,
          }
        : currentRow,
    );
    if (result.result === 'OK') {
      setModels(result.models ?? []);
      return;
    }
    setModels([]);
    setModelId('');
    setActive(false);
  }

  async function handleModelChange(nextModelId: string) {
    const previousModelId = modelId;
    setModelId(nextModelId);
    try {
      const savedByok = await selectModel.mutateAsync(nextModelId);
      applySavedRow(savedByok);
    } catch (modelSelectionError) {
      setModelId(previousModelId);
      throw modelSelectionError;
    }
  }

  async function handleActiveChange(nextActive: boolean) {
    const previousActive = active;
    setActive(nextActive);
    try {
      const savedByok = await activateByok.mutateAsync(nextActive);
      applySavedRow(savedByok);
    } catch (activationError) {
      setActive(previousActive);
      throw activationError;
    }
  }

  async function handleDelete() {
    await deleteByok.mutateAsync();
    setSavedRow(null);
    setProvider(DEFAULT_PROVIDER.provider);
    setBaseUrl(DEFAULT_PROVIDER.defaultBaseUrl);
    setApiKey('');
    setEditingKey(true);
    setModels([]);
    setModelId('');
    setLastTestResult(null);
    setLastTestedAt(null);
    setActive(false);
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
            onChange={(changeEvent) => setBaseUrl(changeEvent.target.value)}
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
                onConfirm={() => {
                  setApiKey('');
                  setEditingKey(true);
                }}
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
              onChange={(changeEvent) => setApiKey(changeEvent.target.value)}
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

function formatUsd(locale: string, usd: number): string {
  return new Intl.NumberFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(usd);
}

function formatTime(locale: string, value: string): string {
  return new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
