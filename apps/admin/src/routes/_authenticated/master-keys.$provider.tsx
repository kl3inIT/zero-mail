import { createFileRoute } from '@tanstack/react-router';
import {
  ChevronDownIcon,
  ChevronUpIcon,
  KeyRoundIcon,
  PencilIcon,
  PlusIcon,
  TrashIcon,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';

import { AddProviderKeyDialog } from '@/components/AddProviderKeyDialog';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useCatalog } from '@/features/catalog/use-catalog';
import type { CatalogModel, CatalogProvider } from '@/features/catalog/catalog-api';
import type {
  LlmProvider,
  ProviderKey,
  ProviderKeyStatus,
} from '@/features/master-keys/master-keys-api';
import { useMasterKey, useProviderKeys } from '@/features/master-keys/use-master-keys';
import { useReorderProviderKeys } from '@/features/master-keys/use-reorder-provider-keys';
import { useRevokeProviderKey } from '@/features/master-keys/use-revoke-provider-key';

export const Route = createFileRoute('/_authenticated/master-keys/$provider')({
  component: MasterKeyProviderRoute,
});

type ProviderMeta = {
  initials: string;
  displayName: string;
  kindLabel: string;
  defaultBaseUrl: string | null;
  defaultKeyFormat: string;
  avatarSrc?: string;
};

const PROVIDER_META: Record<LlmProvider, ProviderMeta> = {
  OPENROUTER: {
    initials: 'OR',
    displayName: 'OpenRouter',
    kindLabel: 'OAuth bundle',
    defaultBaseUrl: 'openrouter.ai/api/v1',
    defaultKeyFormat: 'OPENAI_FORMAT',
  },
  ROUTER_9R: {
    initials: '9R',
    displayName: '9Router',
    kindLabel: 'OAuth bundle',
    defaultBaseUrl: null,
    defaultKeyFormat: 'OPENAI_FORMAT',
  },
  OPENAI: {
    initials: 'OA',
    displayName: 'OpenAI',
    kindLabel: 'API key',
    defaultBaseUrl: 'api.openai.com/v1',
    defaultKeyFormat: 'OPENAI_FORMAT',
  },
  ANTHROPIC: {
    initials: 'AN',
    displayName: 'Anthropic',
    kindLabel: 'API key',
    defaultBaseUrl: 'api.anthropic.com',
    defaultKeyFormat: 'ANTHROPIC_FORMAT',
  },
  GOOGLE: {
    initials: 'GO',
    displayName: 'Google',
    kindLabel: 'API key',
    defaultBaseUrl: 'generativelanguage.googleapis.com',
    defaultKeyFormat: 'GOOGLE_FORMAT',
  },
  DEEPSEEK: {
    initials: 'DS',
    displayName: 'DeepSeek',
    kindLabel: 'API key',
    defaultBaseUrl: 'api.deepseek.com',
    defaultKeyFormat: 'OPENAI_FORMAT',
  },
};

function metaFor(provider: string): ProviderMeta {
  return (
    PROVIDER_META[provider as LlmProvider] ?? {
      initials: provider.slice(0, 2),
      displayName: provider,
      kindLabel: 'Provider',
      defaultBaseUrl: null,
      defaultKeyFormat: 'OPENAI_FORMAT',
    }
  );
}

function MasterKeyProviderRoute() {
  const { provider } = Route.useParams();
  const meta = metaFor(provider);
  const masterKey = useMasterKey(provider);
  const keysQuery = useProviderKeys(provider);
  const catalogQuery = useCatalog(provider as CatalogProvider);
  const reorderMutation = useReorderProviderKeys(provider);
  const revokeMutation = useRevokeProviderKey(provider);
  const [addKeyOpen, setAddKeyOpen] = useState(false);

  const keys = keysQuery.data?.keys ?? [];
  const activeKeyCount = keys.filter((entry) => entry.status === 'ACTIVE').length;

  function moveKey(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= keys.length) return;
    const reordered = keys.slice();
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    reorderMutation.mutate({
      provider,
      orderedKeyIds: reordered.map((entry) => entry.keyId),
    });
  }

  function revokeKey(entry: ProviderKey) {
    if (!window.confirm(`Xoá key ${entry.label ?? entry.maskedKey ?? entry.keyId}?`)) return;
    revokeMutation.mutate({ provider, keyId: entry.keyId });
  }

  const models = useMemo<CatalogModel[]>(() => {
    const featureMap = catalogQuery.data?.features;
    if (!featureMap) return [];
    const seen = new Set<string>();
    const flat: CatalogModel[] = [];
    for (const block of Object.values(featureMap)) {
      for (const model of block.models) {
        if (seen.has(model.modelId)) continue;
        seen.add(model.modelId);
        flat.push(model);
      }
    }
    return flat;
  }, [catalogQuery.data]);

  const baseUrl = masterKey.data?.baseUrl ?? meta.defaultBaseUrl ?? 'chưa cấu hình';

  return (
    <div className="space-y-8">
      <header className="flex items-center gap-4">
        <Avatar size="lg">
          {meta.avatarSrc && <AvatarImage src={meta.avatarSrc} alt={meta.displayName} />}
          <AvatarFallback>{meta.initials}</AvatarFallback>
        </Avatar>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{meta.displayName}</h1>
          <p className="text-muted-foreground text-sm">{baseUrl}</p>
        </div>
      </header>

      <KpiStrip
        activeKeys={activeKeyCount}
        totalKeys={keys.length}
        modelCount={models.length}
        pending={keysQuery.isPending || catalogQuery.isPending}
      />

      <ConnectionsCard
        keys={keys}
        pending={keysQuery.isPending}
        onAddKey={() => setAddKeyOpen(true)}
        onMoveKey={moveKey}
        onRevokeKey={revokeKey}
        mutationPending={reorderMutation.isPending || revokeMutation.isPending}
      />

      <ModelsCard models={models} pending={catalogQuery.isPending} />

      <AddProviderKeyDialog
        provider={provider}
        open={addKeyOpen}
        onOpenChange={setAddKeyOpen}
      />
    </div>
  );
}

function KpiStrip({
  activeKeys,
  totalKeys,
  modelCount,
  pending,
}: {
  activeKeys: number;
  totalKeys: number;
  modelCount: number;
  pending: boolean;
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Kpi label="Keys hoạt động" value={pending ? '—' : `${activeKeys} / ${totalKeys}`} />
      <Kpi label="Đang giới hạn" value="0" />
      <Kpi label="Models đã lưu" value={pending ? '—' : String(modelCount)} />
      <Kpi label="Last test" value="—" />
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-3xl font-semibold tabular-nums">{value}</CardTitle>
      </CardHeader>
    </Card>
  );
}

function ConnectionsCard({
  keys,
  pending,
  onAddKey,
  onMoveKey,
  onRevokeKey,
  mutationPending,
}: {
  keys: ProviderKey[];
  pending: boolean;
  onAddKey: () => void;
  onMoveKey: (index: number, direction: -1 | 1) => void;
  onRevokeKey: (entry: ProviderKey) => void;
  mutationPending: boolean;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Kết nối</CardTitle>
        <CardDescription>
          Priority fallback khi 429/5xx. Không round-robin.
        </CardDescription>
        <CardAction className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={pending || keys.length === 0}
            onClick={() => toast.info('Test all keys — chưa wire endpoint test-per-key.')}
          >
            Test all
          </Button>
          <Button size="sm" onClick={onAddKey} disabled={pending}>
            <PlusIcon className="size-3.5" />
            Thêm key
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        {pending ? (
          <Skeleton className="h-32 w-full" />
        ) : keys.length === 0 ? (
          <EmptyState message='Chưa có key — bấm "Thêm key" để bắt đầu.' />
        ) : (
          keys.map((entry, index) => (
            <ConnectionRow
              key={entry.keyId}
              entry={entry}
              isFirst={index === 0}
              isLast={index === keys.length - 1}
              onMoveUp={() => onMoveKey(index, -1)}
              onMoveDown={() => onMoveKey(index, 1)}
              onRevoke={() => onRevokeKey(entry)}
              disabled={mutationPending}
            />
          ))
        )}
      </CardContent>
    </Card>
  );
}

function ConnectionRow({
  entry,
  isFirst,
  isLast,
  onMoveUp,
  onMoveDown,
  onRevoke,
  disabled,
}: {
  entry: ProviderKey;
  isFirst: boolean;
  isLast: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onRevoke: () => void;
  disabled: boolean;
}) {
  const isActive = entry.status === 'ACTIVE';
  return (
    <div
      className={
        'border-border bg-background flex items-center gap-3 rounded-md border px-3 py-2 ' +
        (isActive ? '' : 'opacity-60')
      }
    >
      <div className="flex flex-col">
        <button
          type="button"
          aria-label="Lên ưu tiên"
          disabled={isFirst || disabled}
          onClick={onMoveUp}
          className="text-muted-foreground hover:text-foreground disabled:opacity-30"
        >
          <ChevronUpIcon className="size-4" />
        </button>
        <button
          type="button"
          aria-label="Xuống ưu tiên"
          disabled={isLast || disabled}
          onClick={onMoveDown}
          className="text-muted-foreground hover:text-foreground disabled:opacity-30"
        >
          <ChevronDownIcon className="size-4" />
        </button>
      </div>

      <Avatar size="default">
        <AvatarFallback>
          <KeyRoundIcon className="size-4" />
        </AvatarFallback>
      </Avatar>

      <div className="min-w-0 flex-1">
        <div className="truncate font-medium">
          {entry.label ?? entry.maskedKey ?? `key #${entry.priority}`}
        </div>
        <div className="text-muted-foreground truncate font-mono text-xs">
          {entry.maskedKey ?? '—'} · priority {entry.priority}
        </div>
      </div>

      <ConnectionStatusBadge status={entry.status} />

      <Button
        variant="ghost"
        size="sm"
        onClick={() => toast.info('Test key — chưa wire endpoint test-per-key.')}
      >
        Test
      </Button>
      <Button
        variant="ghost"
        size="icon"
        aria-label="Sửa key"
        onClick={() => toast.info('Sửa key — chưa wire endpoint.')}
      >
        <PencilIcon className="size-4" />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        aria-label="Xoá key"
        disabled={disabled}
        onClick={onRevoke}
      >
        <TrashIcon className="size-4" />
      </Button>
    </div>
  );
}

function ConnectionStatusBadge({ status }: { status: ProviderKeyStatus }) {
  if (status === 'ACTIVE') return <Badge>ACTIVE</Badge>;
  if (status === 'PENDING') return <Badge variant="secondary">PENDING</Badge>;
  return <Badge variant="outline">REVOKED</Badge>;
}

function ModelsCard({
  models,
  pending,
}: {
  models: CatalogModel[];
  pending: boolean;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Models</CardTitle>
        <CardDescription>
          Chỉ model VERIFIED mới hiện trong dropdown tier routing.
        </CardDescription>
        <CardAction className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={pending || models.length === 0}
            onClick={() => toast.info('Test all models — đang chờ backend endpoint.')}
          >
            Test all
          </Button>
          <Button
            size="sm"
            disabled
            onClick={() => toast.info('Add model — đang chờ backend endpoint.')}
          >
            <PlusIcon className="size-3.5" />
            Thêm model
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        {pending ? (
          <Skeleton className="h-32 w-full" />
        ) : models.length === 0 ? (
          <EmptyState message='Chưa có model — bấm "Thêm model" để bắt đầu.' />
        ) : (
          models.map((model) => <ModelRow key={model.modelId} model={model} />)
        )}
      </CardContent>
    </Card>
  );
}

function ModelRow({ model }: { model: CatalogModel }) {
  const isDeprecated = Boolean(model.deprecatedAt);
  return (
    <div
      className={
        'border-border bg-background flex items-center gap-3 rounded-md border px-3 py-2 ' +
        (isDeprecated ? 'opacity-60' : '')
      }
    >
      <div className="min-w-0 flex-1">
        <div className="truncate font-medium">{model.displayName}</div>
        <div className="text-muted-foreground truncate font-mono text-xs">
          {model.modelId}
        </div>
      </div>
      {model.recommended && <Badge variant="secondary">Đề xuất</Badge>}
      {isDeprecated ? (
        <Badge variant="outline">DEPRECATED</Badge>
      ) : (
        <Badge>VERIFIED</Badge>
      )}
      <Button
        variant="ghost"
        size="sm"
        onClick={() => toast.info('Test model — đang chờ backend endpoint.')}
      >
        Test
      </Button>
      <Button
        variant="ghost"
        size="icon"
        aria-label="Vô hiệu model"
        onClick={() => toast.info('Disable model — đang chờ backend endpoint.')}
      >
        <TrashIcon className="size-4" />
      </Button>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="text-muted-foreground flex items-center justify-center rounded-md py-10 text-sm">
      {message}
    </div>
  );
}
