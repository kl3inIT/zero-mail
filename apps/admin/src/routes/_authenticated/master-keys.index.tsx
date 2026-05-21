import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';

import { RoutingMatrixCard } from '@/components/RoutingMatrixCard';
import { TierPickerDialog, type TierPickerState } from '@/components/TierPickerDialog';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import type { LlmProvider, MasterKeyRow } from '@/features/master-keys/master-keys-api';
import { useMasterKeys } from '@/features/master-keys/use-master-keys';

export const Route = createFileRoute('/_authenticated/master-keys/')({
  component: MasterKeysListRoute,
});

const PROVIDER_LABELS: Record<
  LlmProvider,
  { name: string; kind: string; initials: string; avatarSrc?: string }
> = {
  OPENROUTER: { name: 'OpenRouter', kind: 'OAuth bundle', initials: 'OR' },
  ROUTER_9R: { name: '9Router', kind: 'OAuth bundle', initials: '9R' },
  OPENAI: { name: 'OpenAI', kind: 'API key', initials: 'OA' },
  ANTHROPIC: { name: 'Anthropic', kind: 'API key', initials: 'AN' },
  GOOGLE: { name: 'Google', kind: 'API key', initials: 'GO' },
  DEEPSEEK: { name: 'DeepSeek', kind: 'API key', initials: 'DS' },
};

function MasterKeysListRoute() {
  const masterKeys = useMasterKeys();
  const [pickerState, setPickerState] = useState<TierPickerState>(null);

  const rows = masterKeys.data?.rows ?? [];

  return (
    <div className="space-y-8">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Quản lý LLM</h1>
        <p className="text-muted-foreground text-sm">
          Provider, key và routing mặc định cho toàn hệ thống.
        </p>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Nhà cung cấp</h2>
        {masterKeys.isPending ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-32 w-full" />
            ))}
          </div>
        ) : (
          <ProviderGrid rows={rows} />
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Mặc định theo tính năng</h2>
        <RoutingMatrixCard
          onCellClick={(feature, tier, current) =>
            setPickerState({ feature, tier, current })
          }
        />
      </section>

      <TierPickerDialog state={pickerState} onClose={() => setPickerState(null)} />
    </div>
  );
}

function ProviderGrid({ rows }: { rows: MasterKeyRow[] }) {
  const navigate = useNavigate();
  if (rows.length === 0) {
    return (
      <Card>
        <CardContent className="text-muted-foreground py-10 text-center text-sm">
          Chưa có provider nào.
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
              params: { provider: row.provider },
            })
          }
        />
      ))}
    </div>
  );
}

function ProviderCard({ row, onClick }: { row: MasterKeyRow; onClick: () => void }) {
  const meta = PROVIDER_LABELS[row.provider as LlmProvider] ?? {
    name: row.displayName,
    kind: 'Provider',
    initials: row.provider.slice(0, 2),
    avatarSrc: undefined,
  };
  const hasKey = Boolean(row.maskedKey);
  const activeKeyCount = hasKey ? 1 : 0;
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
            {meta.avatarSrc && <AvatarImage src={meta.avatarSrc} alt={meta.name} />}
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
        {row.dependentsCount > 0 && (
          <Badge className="bg-blue-soft text-blue border-transparent">
            {row.dependentsCount} dependents
          </Badge>
        )}
      </CardContent>
    </Card>
  );
}
