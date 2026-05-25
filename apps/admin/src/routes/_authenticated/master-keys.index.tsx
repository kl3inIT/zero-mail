import {createFileRoute, useNavigate} from '@tanstack/react-router';
import {PlusIcon} from 'lucide-react';
import {useState} from 'react';

import {AddProviderDialog} from '@/components/AddProviderDialog';
import {RoutingMatrixCard} from '@/components/RoutingMatrixCard';
import {TierPickerDialog, type TierPickerState} from '@/components/TierPickerDialog';
import {Avatar, AvatarFallback, AvatarImage} from '@/components/ui/avatar';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import type {LlmProvider, MasterKeyRow} from '@/features/master-keys/master-keys-api';
import {useMasterKeys} from '@/features/master-keys/use-master-keys';

export const Route = createFileRoute('/_authenticated/master-keys/')({
  component: MasterKeysListRoute,
});

const PROVIDER_LABELS: Partial<
  Record<
    LlmProvider,
    { name: string; kind: string; initials: string; avatarSrc?: string }
  >
> = {
  OPENAI: {name: 'OpenAI', kind: 'API key', initials: 'OA'},
  ANTHROPIC: {name: 'Anthropic', kind: 'API key', initials: 'AN'},
  GOOGLE: {name: 'Google', kind: 'API key', initials: 'GO'},
  DEEPSEEK: {name: 'DeepSeek', kind: 'API key', initials: 'DS'},
};

const SPRING_AI_BUILT_IN_PROVIDERS = new Set<string>([
  'OPENAI',
  'ANTHROPIC',
  'GOOGLE',
  'DEEPSEEK',
]);

function MasterKeysListRoute() {
  const masterKeys = useMasterKeys();
  const [pickerState, setPickerState] = useState<TierPickerState>(null);
  const [addProviderOpen, setAddProviderOpen] = useState(false);

  const rows = masterKeys.data?.rows ?? [];
  const builtInRows = rows.filter(
    (row) =>
      row.providerKind === 'SPRING_AI_BUILT_IN' ||
      (!row.providerKind && SPRING_AI_BUILT_IN_PROVIDERS.has(row.provider)),
  );
  const openAiCompatibleRows = rows.filter(
    (row) => !builtInRows.includes(row) && compatibleTypeFor(row) === 'OPENAI_FORMAT',
  );
  const anthropicCompatibleRows = rows.filter(
    (row) => !builtInRows.includes(row) && compatibleTypeFor(row) === 'ANTHROPIC_FORMAT',
  );
  const otherCompatibleRows = rows.filter(
    (row) =>
      !builtInRows.includes(row) &&
      compatibleTypeFor(row) !== 'OPENAI_FORMAT' &&
      compatibleTypeFor(row) !== 'ANTHROPIC_FORMAT',
  );

  return (
    <div className="space-y-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Quản lý LLM</h1>
          <p className="text-muted-foreground text-sm">
            Provider, key và routing mặc định cho toàn hệ thống.
          </p>
        </div>
        <Button onClick={() => setAddProviderOpen(true)}>
          <PlusIcon className="size-4"/>
          Thêm provider
        </Button>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Spring AI</h2>
        {masterKeys.isPending ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {Array.from({length: 6}).map((_, index) => (
              <Skeleton key={index} className="h-32 w-full"/>
            ))}
          </div>
        ) : (
          <ProviderGrid rows={builtInRows} emptyMessage="Chưa có provider mặc định."/>
        )}
      </section>

      {!masterKeys.isPending && openAiCompatibleRows.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">OpenAI-compatible</h2>
          <ProviderGrid rows={openAiCompatibleRows} emptyMessage="Chưa có provider nào."/>
        </section>
      )}

      {!masterKeys.isPending && anthropicCompatibleRows.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">Anthropic-compatible</h2>
          <ProviderGrid rows={anthropicCompatibleRows} emptyMessage="Chưa có provider nào."/>
        </section>
      )}

      {!masterKeys.isPending && otherCompatibleRows.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">Chưa phân loại</h2>
          <ProviderGrid rows={otherCompatibleRows} emptyMessage="Chưa có provider nào."/>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Model cho tác vụ AI</h2>
        <RoutingMatrixCard
          onCellClick={(feature, tier, current) =>
            setPickerState({feature, tier, current})
          }
        />
      </section>

      <TierPickerDialog state={pickerState} onClose={() => setPickerState(null)}/>
      <AddProviderDialog open={addProviderOpen} onOpenChange={setAddProviderOpen}/>
    </div>
  );
}

function compatibleTypeFor(row: MasterKeyRow) {
  return row.compatibleType ?? row.keyFormat;
}

function ProviderGrid({
                        rows,
                        emptyMessage,
                      }: {
  rows: MasterKeyRow[];
  emptyMessage: string;
}) {
  const navigate = useNavigate();
  if (rows.length === 0) {
    return (
      <Card>
        <CardContent className="text-muted-foreground py-10 text-center text-sm">
          {emptyMessage}
        </CardContent>
      </Card>
    );
  }
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {rows.map((row) => (
        <ProviderCard
          key={row.provider}
          row={row}
          onClick={() =>
            void navigate({
              to: '/master-keys/$provider',
              params: {provider: row.provider},
            })
          }
        />
      ))}
    </div>
  );
}

function ProviderCard({row, onClick}: { row: MasterKeyRow; onClick: () => void }) {
  const meta = PROVIDER_LABELS[row.provider as LlmProvider] ?? {
    name: row.displayName,
    kind:
      row.providerKind === 'SPRING_AI_BUILT_IN'
        ? 'Spring AI'
        : row.compatibleType === 'ANTHROPIC_FORMAT'
          ? 'Anthropic-compatible'
          : 'OpenAI-compatible',
    initials: initialsFor(row.displayName || row.provider),
    avatarSrc: undefined,
  };
  const activeKeyCount = row.activeKeyCount ?? 0;
  return (
    <Card
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onClick();
        }
      }}
      className="hover:border-primary/40 cursor-pointer transition-colors"
    >
      <CardHeader>
        <div className="flex items-center gap-3">
          <Avatar size="lg">
            {meta.avatarSrc && <AvatarImage src={meta.avatarSrc} alt={meta.name}/>}
            <AvatarFallback>{meta.initials}</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <CardTitle>{meta.name}</CardTitle>
            <CardDescription>{meta.kind}</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2">
        <Badge
          className={
            activeKeyCount > 0
              ? 'bg-green-soft text-green border-transparent'
              : 'bg-muted text-muted-foreground border-transparent'
          }
        >
          {activeKeyCount} key đang hoạt động
        </Badge>
        {row.rotationRecommended && (
          <Badge className="bg-amber-soft text-amber border-transparent">Nên xoay khóa</Badge>
        )}
      </CardContent>
    </Card>
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
