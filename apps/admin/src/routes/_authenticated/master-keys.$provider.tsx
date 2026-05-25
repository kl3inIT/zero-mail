import {createFileRoute, useNavigate} from '@tanstack/react-router';
import {
  ChevronDownIcon,
  ChevronUpIcon,
  KeyRoundIcon,
  PencilIcon,
  PlusIcon,
  TrashIcon,
} from 'lucide-react';
import {useMemo, useState} from 'react';
import {toast} from 'sonner';

import {AddCatalogModelDialog} from '@/components/AddCatalogModelDialog';
import {AddProviderKeyDialog} from '@/components/AddProviderKeyDialog';
import {EditProviderKeyDialog} from '@/components/EditProviderKeyDialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogMedia,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {Avatar, AvatarFallback, AvatarImage} from '@/components/ui/avatar';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {useCatalog} from '@/features/catalog/use-catalog';
import {useDisableModel} from '@/features/catalog/use-disable-model';
import type {CatalogModel, CatalogProvider} from '@/features/catalog/catalog-api';
import type {
  LlmProvider,
  ProviderKey,
  ProviderKeyStatus,
} from '@/features/master-keys/master-keys-api';
import {useMasterKey, useProviderKeys} from '@/features/master-keys/use-master-keys';
import {useDeleteProvider} from '@/features/master-keys/use-delete-provider';
import {useReorderProviderKeys} from '@/features/master-keys/use-reorder-provider-keys';
import {useRevokeProviderKey} from '@/features/master-keys/use-revoke-provider-key';
import {useTestProviderKey} from '@/features/master-keys/use-test-provider-key';

export const Route = createFileRoute('/_authenticated/master-keys/$provider')({
  component: MasterKeyProviderRoute,
});

type ProviderMeta = {
  initials: string;
  displayName: string;
  kindLabel: string;
  defaultBaseUrl: string | null;
  avatarSrc?: string;
};

const PROVIDER_META: Partial<Record<LlmProvider, ProviderMeta>> = {
  OPENAI: {
    initials: 'OA',
    displayName: 'OpenAI',
    kindLabel: 'Spring AI',
    defaultBaseUrl: 'api.openai.com/v1',
  },
  ANTHROPIC: {
    initials: 'AN',
    displayName: 'Anthropic',
    kindLabel: 'Spring AI',
    defaultBaseUrl: 'api.anthropic.com',
  },
  GOOGLE: {
    initials: 'GO',
    displayName: 'Google',
    kindLabel: 'Spring AI',
    defaultBaseUrl: 'generativelanguage.googleapis.com',
  },
  DEEPSEEK: {
    initials: 'DS',
    displayName: 'DeepSeek',
    kindLabel: 'Spring AI',
    defaultBaseUrl: 'api.deepseek.com',
  },
};

function metaFor(provider: string): ProviderMeta {
  return (
    PROVIDER_META[provider as LlmProvider] ?? {
      initials: initialsFor(provider),
      displayName: provider,
      kindLabel: 'Provider tương thích',
      defaultBaseUrl: null,
    }
  );
}

function MasterKeyProviderRoute() {
  const {provider} = Route.useParams();
  const navigate = useNavigate();
  const meta = metaFor(provider);
  const masterKey = useMasterKey(provider);
  const keysQuery = useProviderKeys(provider);
  const catalogQuery = useCatalog(provider as CatalogProvider);
  const reorderMutation = useReorderProviderKeys(provider);
  const revokeMutation = useRevokeProviderKey(provider);
  const deleteProviderMutation = useDeleteProvider();
  const testKeyMutation = useTestProviderKey();
  const disableModelMutation = useDisableModel();
  const [addKeyOpen, setAddKeyOpen] = useState(false);
  const [editingKey, setEditingKey] = useState<ProviderKey | null>(null);
  const [keyToDelete, setKeyToDelete] = useState<ProviderKey | null>(null);
  const [addModelOpen, setAddModelOpen] = useState(false);
  const [modelToDisable, setModelToDisable] = useState<CatalogModel | null>(null);
  const [deleteProviderOpen, setDeleteProviderOpen] = useState(false);

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
    setKeyToDelete(entry);
  }

  function testKey(entry: ProviderKey) {
    testKeyMutation.mutate(
      {provider, keyId: entry.keyId},
      {
        onSuccess: (data) => {
          if (data.result === 'OK') {
            toast.success(
              `${entry.label ?? entry.maskedKey ?? entry.keyId}: kết nối OK.`,
            );
          } else {
            toast.error(
              `${entry.label ?? entry.maskedKey ?? entry.keyId}: ${data.result}.`,
            );
          }
        },
      },
    );
  }

  function testAllKeys() {
    keys
      .filter((entry) => entry.status === 'ACTIVE')
      .forEach((entry) => testKey(entry));
  }

  function disableModel(model: CatalogModel) {
    setModelToDisable(model);
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

  const displayName = masterKey.data?.displayName ?? meta.displayName;
  const kindLabel =
    masterKey.data?.providerKind === 'SPRING_AI_BUILT_IN'
      ? 'Spring AI'
      : masterKey.data?.compatibleType === 'ANTHROPIC_FORMAT'
        ? 'Anthropic-compatible'
        : masterKey.data?.compatibleType === 'OPENAI_FORMAT'
          ? 'OpenAI-compatible'
          : meta.kindLabel;
  const baseUrl =
    masterKey.data?.baseUrl ??
    masterKey.data?.defaultBaseUrl ??
    meta.defaultBaseUrl ??
    'chưa cấu hình';
  const canDeleteProvider = masterKey.data?.providerKind === 'COMPATIBLE_GATEWAY';

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-4">
          <Avatar size="lg">
            {meta.avatarSrc && <AvatarImage src={meta.avatarSrc} alt={displayName}/>}
            <AvatarFallback>{initialsFor(displayName)}</AvatarFallback>
          </Avatar>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{displayName}</h1>
            <p className="text-muted-foreground text-sm">
              {kindLabel} · {baseUrl}
            </p>
          </div>
        </div>
        {canDeleteProvider && (
          <Button
            variant="outline"
            className="text-destructive hover:text-destructive sm:self-start"
            onClick={() => setDeleteProviderOpen(true)}
          >
            <TrashIcon className="size-4"/>
            Xoá provider
          </Button>
        )}
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
        onTestKey={testKey}
        onTestAll={testAllKeys}
        onEditKey={setEditingKey}
        mutationPending={reorderMutation.isPending || revokeMutation.isPending}
        testKeyPendingId={testKeyMutation.isPending ? testKeyMutation.variables?.keyId : undefined}
      />

      <ModelsCard
        models={models}
        pending={catalogQuery.isPending}
        onAddModel={() => setAddModelOpen(true)}
        onDisableModel={disableModel}
        disabling={disableModelMutation.isPending}
      />

      <AddProviderKeyDialog
        provider={provider}
        open={addKeyOpen}
        onOpenChange={setAddKeyOpen}
      />

      <EditProviderKeyDialog
        provider={provider}
        entry={editingKey}
        onClose={() => setEditingKey(null)}
      />

      <DeleteProviderKeyDialog
        entry={keyToDelete}
        pending={revokeMutation.isPending}
        onClose={() => setKeyToDelete(null)}
        onConfirm={(entry) =>
          revokeMutation.mutate(
            {provider, keyId: entry.keyId},
            {onSuccess: () => setKeyToDelete(null)},
          )
        }
      />

      <AddCatalogModelDialog
        provider={provider as CatalogProvider}
        open={addModelOpen}
        onOpenChange={setAddModelOpen}
      />

      <DisableModelDialog
        model={modelToDisable}
        pending={disableModelMutation.isPending}
        onClose={() => setModelToDisable(null)}
        onConfirm={(model) =>
          disableModelMutation.mutate(
            {
              modelId: model.modelId,
              reason: '',
              confirmedPinned: model.pinnedTenantCount > 0,
              pinnedCountAcknowledged: model.pinnedTenantCount,
            },
            {onSuccess: () => setModelToDisable(null)},
          )
        }
      />

      <DeleteProviderDialog
        providerName={displayName}
        pending={deleteProviderMutation.isPending}
        open={deleteProviderOpen}
        onClose={() => setDeleteProviderOpen(false)}
        onConfirm={() =>
          deleteProviderMutation.mutate(provider, {
            onSuccess: () => {
              setDeleteProviderOpen(false);
              void navigate({to: '/master-keys'});
            },
          })
        }
      />
    </div>
  );
}

function initialsFor(value: string) {
  return value
    .split(/[\s_-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
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
      <Kpi label="Keys hoạt động" value={pending ? '—' : `${activeKeys} / ${totalKeys}`}/>
      <Kpi label="Đang giới hạn" value="0"/>
      <Kpi label="Models đã lưu" value={pending ? '—' : String(modelCount)}/>
      <Kpi label="Last test" value="—"/>
    </div>
  );
}

function Kpi({label, value}: { label: string; value: string }) {
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
                           onTestKey,
                           onTestAll,
                           onEditKey,
                           mutationPending,
                           testKeyPendingId,
                         }: {
  keys: ProviderKey[];
  pending: boolean;
  onAddKey: () => void;
  onMoveKey: (index: number, direction: -1 | 1) => void;
  onRevokeKey: (entry: ProviderKey) => void;
  onTestKey: (entry: ProviderKey) => void;
  onTestAll: () => void;
  onEditKey: (entry: ProviderKey) => void;
  mutationPending: boolean;
  testKeyPendingId?: string;
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
            onClick={onTestAll}
          >
            Test all
          </Button>
          <Button size="sm" onClick={onAddKey} disabled={pending}>
            <PlusIcon className="size-3.5"/>
            Thêm key
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        {pending ? (
          <Skeleton className="h-32 w-full"/>
        ) : keys.length === 0 ? (
          <EmptyState message='Chưa có key — bấm "Thêm key" để bắt đầu.'/>
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
              onTest={() => onTestKey(entry)}
              onEdit={() => onEditKey(entry)}
              testing={testKeyPendingId === entry.keyId}
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
                         onTest,
                         onEdit,
                         testing,
                         disabled,
                       }: {
  entry: ProviderKey;
  isFirst: boolean;
  isLast: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onRevoke: () => void;
  onTest: () => void;
  onEdit: () => void;
  testing: boolean;
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
          <ChevronUpIcon className="size-4"/>
        </button>
        <button
          type="button"
          aria-label="Xuống ưu tiên"
          disabled={isLast || disabled}
          onClick={onMoveDown}
          className="text-muted-foreground hover:text-foreground disabled:opacity-30"
        >
          <ChevronDownIcon className="size-4"/>
        </button>
      </div>

      <Avatar size="default">
        <AvatarFallback>
          <KeyRoundIcon className="size-4"/>
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

      <ConnectionStatusBadge status={entry.status}/>

      <Button variant="ghost" size="sm" onClick={onTest} disabled={testing}>
        {testing ? 'Đang test…' : 'Test'}
      </Button>
      <Button variant="ghost" size="icon" aria-label="Sửa key" onClick={onEdit}>
        <PencilIcon className="size-4"/>
      </Button>
      <Button
        variant="ghost"
        size="icon"
        aria-label="Xoá key"
        disabled={disabled}
        onClick={onRevoke}
      >
        <TrashIcon className="size-4"/>
      </Button>
    </div>
  );
}

function ConnectionStatusBadge({status}: { status: ProviderKeyStatus }) {
  if (status === 'ACTIVE') return <Badge>ACTIVE</Badge>;
  if (status === 'PENDING') return <Badge variant="secondary">PENDING</Badge>;
  return <Badge variant="outline">REVOKED</Badge>;
}

function ModelsCard({
                      models,
                      pending,
                      onAddModel,
                      onDisableModel,
                      disabling,
                    }: {
  models: CatalogModel[];
  pending: boolean;
  onAddModel: () => void;
  onDisableModel: (model: CatalogModel) => void;
  disabling: boolean;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Models</CardTitle>
        <CardDescription>
          Chỉ model VERIFIED hoặc STALE mới hiện trong dropdown tier routing.
        </CardDescription>
        <CardAction>
          <Button size="sm" onClick={onAddModel} disabled={pending}>
            <PlusIcon className="size-3.5"/>
            Thêm model
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        {pending ? (
          <Skeleton className="h-32 w-full"/>
        ) : models.length === 0 ? (
          <EmptyState message='Chưa có model — bấm "Thêm model" để bắt đầu.'/>
        ) : (
          models.map((model) => (
            <ModelRow
              key={model.modelId}
              model={model}
              onDisable={() => onDisableModel(model)}
              disabling={disabling}
            />
          ))
        )}
      </CardContent>
    </Card>
  );
}

function ModelRow({
                    model,
                    onDisable,
                    disabling,
                  }: {
  model: CatalogModel;
  onDisable: () => void;
  disabling: boolean;
}) {
  const isDeprecated = Boolean(model.deprecatedAt);
  const status = model.verificationStatus ?? 'UNTESTED';
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
      <ModelStatusBadge deprecated={isDeprecated} status={status}/>
      {!isDeprecated && (
        <Button
          variant="ghost"
          size="icon"
          aria-label="Vô hiệu model"
          onClick={onDisable}
          disabled={disabling}
        >
          <TrashIcon className="size-4"/>
        </Button>
      )}
    </div>
  );
}

function ModelStatusBadge({
                            deprecated,
                            status,
                          }: {
  deprecated: boolean;
  status: CatalogModel['verificationStatus'];
}) {
  if (deprecated) return <Badge variant="outline">DEPRECATED</Badge>;
  if (status === 'VERIFIED') return <Badge>VERIFIED</Badge>;
  if (status === 'STALE') return <Badge variant="secondary">STALE</Badge>;
  if (status === 'FAILED') return <Badge variant="outline">FAILED</Badge>;
  return <Badge variant="outline">UNTESTED</Badge>;
}

function DeleteProviderKeyDialog({
                                   entry,
                                   pending,
                                   onClose,
                                   onConfirm,
                                 }: {
  entry: ProviderKey | null;
  pending: boolean;
  onClose: () => void;
  onConfirm: (entry: ProviderKey) => void;
}) {
  const label = entry?.label ?? entry?.maskedKey ?? entry?.keyId;
  return (
    <AlertDialog open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      {entry && (
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogMedia>
              <TrashIcon className="size-5"/>
            </AlertDialogMedia>
            <AlertDialogTitle>Xoá key?</AlertDialogTitle>
            <AlertDialogDescription>
              Key {label} sẽ bị xoá khỏi failover chain của provider này.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={pending}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={pending}
              onClick={() => onConfirm(entry)}
            >
              {pending ? 'Đang xoá…' : 'Xoá'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      )}
    </AlertDialog>
  );
}

function DisableModelDialog({
                              model,
                              pending,
                              onClose,
                              onConfirm,
                            }: {
  model: CatalogModel | null;
  pending: boolean;
  onClose: () => void;
  onConfirm: (model: CatalogModel) => void;
}) {
  return (
    <AlertDialog open={model !== null} onOpenChange={(open) => !open && onClose()}>
      {model && (
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogMedia>
              <TrashIcon className="size-5"/>
            </AlertDialogMedia>
            <AlertDialogTitle>Vô hiệu model?</AlertDialogTitle>
            <AlertDialogDescription>
              Model {model.modelId} sẽ không còn được chọn cho routing mới.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={pending}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={pending}
              onClick={() => onConfirm(model)}
            >
              {pending ? 'Đang lưu…' : 'Vô hiệu'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      )}
    </AlertDialog>
  );
}

function DeleteProviderDialog({
  providerName,
  open,
  pending,
  onClose,
  onConfirm,
}: {
  providerName: string;
  open: boolean;
  pending: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  return (
    <AlertDialog open={open} onOpenChange={(next) => !next && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogMedia>
            <TrashIcon className="size-5"/>
          </AlertDialogMedia>
          <AlertDialogTitle>Xoá provider?</AlertDialogTitle>
          <AlertDialogDescription>
            Provider {providerName} sẽ bị xoá cùng key và model đã lưu. Chỉ provider tương thích
            không còn được dùng trong routing mới xoá được.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>Hủy</AlertDialogCancel>
          <AlertDialogAction
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            disabled={pending}
            onClick={onConfirm}
          >
            {pending ? 'Đang xoá…' : 'Xoá'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function EmptyState({message}: { message: string }) {
  return (
    <div className="text-muted-foreground flex items-center justify-center rounded-md py-10 text-sm">
      {message}
    </div>
  );
}
