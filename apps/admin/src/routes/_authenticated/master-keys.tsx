import { Outlet, createFileRoute, useNavigate } from '@tanstack/react-router';
import { useMemo, useState } from 'react';

import { RoutingMatrixCard } from '@/components/RoutingMatrixCard';
import { TierPickerDialog, type TierPickerState } from '@/components/TierPickerDialog';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import type {
  LlmProvider,
  MasterKeyRow,
} from '@/features/master-keys/master-keys-api';
import { useMasterKeys } from '@/features/master-keys/use-master-keys';

export const Route = createFileRoute('/_authenticated/master-keys')({
  component: MasterKeysRoute,
});

type ProviderMeta = {
  provider: LlmProvider;
  initials: string;
  iconTone: string;
  kindLabel: string;
};

const PROVIDER_META: Record<LlmProvider, ProviderMeta> = {
  OPENROUTER: {
    provider: 'OPENROUTER',
    initials: 'OR',
    iconTone: 'bg-violet-soft text-primary',
    kindLabel: 'OAuth bundle',
  },
  ROUTER_9R: {
    provider: 'ROUTER_9R',
    initials: '9R',
    iconTone: 'bg-blue-soft text-blue',
    kindLabel: 'OAuth bundle',
  },
  OPENAI: {
    provider: 'OPENAI',
    initials: 'OA',
    iconTone: 'bg-green-soft text-green',
    kindLabel: 'API key',
  },
  ANTHROPIC: {
    provider: 'ANTHROPIC',
    initials: 'AN',
    iconTone: 'bg-amber-soft text-amber',
    kindLabel: 'API key',
  },
  GOOGLE: {
    provider: 'GOOGLE',
    initials: 'GO',
    iconTone: 'bg-blue-soft text-blue',
    kindLabel: 'API key',
  },
  DEEPSEEK: {
    provider: 'DEEPSEEK',
    initials: 'DS',
    iconTone: 'bg-violet-soft text-violet',
    kindLabel: 'API key',
  },
};

function MasterKeysRoute() {
  const masterKeys = useMasterKeys();
  const [search, setSearch] = useState('');
  const [pickerState, setPickerState] = useState<TierPickerState>(null);

  const filteredRows = useMemo(() => {
    const rows = masterKeys.data?.rows ?? [];
    const needle = search.trim().toLowerCase();
    if (!needle) return rows;
    return rows.filter(
      (row) =>
        row.provider.toLowerCase().includes(needle) ||
        row.displayName.toLowerCase().includes(needle),
    );
  }, [masterKeys.data?.rows, search]);

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            Vận hành LLM
          </p>
          <h1 className="text-ink text-2xl font-semibold tracking-tight">
            Khóa <span className="text-primary">nền tảng</span>
          </h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Quản lý LLM provider, key, model và routing mặc định cho toàn hệ thống.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Input
            value={search}
            onChange={(event) => setSearch(event.currentTarget.value)}
            placeholder="Lọc provider…"
            className="h-8 w-48"
          />
        </div>
      </header>

      <Outlet />

      <section className="space-y-3">
        <SectionHead
          num="01"
          title="Nhà cung cấp"
          subtitle="Click vào card để vào trang config (keys + models). Bật/tắt provider làm trong detail."
        />
        {masterKeys.isPending ? (
          <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-36 w-full" />
            ))}
          </div>
        ) : (
          <ProviderGrid rows={filteredRows} />
        )}
      </section>

      <section className="space-y-3">
        <SectionHead
          num="02"
          title="Mặc định theo tính năng"
          subtitle="Routing GLOBAL — mỗi tính năng có 3 tier, fallback khi tier trên hỏng."
        />
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

function SectionHead({
  num,
  title,
  subtitle,
}: {
  num: string;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="flex flex-wrap items-baseline gap-3">
      <span className="text-muted-foreground font-mono text-[11px] tracking-wider">{num}</span>
      <h2 className="text-ink text-base font-semibold">{title}</h2>
      <span className="text-muted-foreground text-sm">— {subtitle}</span>
    </div>
  );
}

function ProviderGrid({ rows }: { rows: MasterKeyRow[] }) {
  const navigate = useNavigate();
  if (rows.length === 0) {
    return (
      <Card>
        <CardContent className="text-muted-foreground py-8 text-center text-sm">
          Không có provider nào khớp bộ lọc.
        </CardContent>
      </Card>
    );
  }
  return (
    <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
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
  const meta = PROVIDER_META[row.provider as LlmProvider] ?? {
    provider: row.provider as LlmProvider,
    initials: row.provider.slice(0, 2),
    iconTone: 'bg-muted text-muted-foreground',
    kindLabel: 'Provider',
  };
  const hasKey = Boolean(row.maskedKey);
  return (
    <button
      type="button"
      onClick={onClick}
      className="group border-border hover:border-primary/40 bg-card flex w-full flex-col gap-4 rounded-lg border p-4 text-left transition-colors"
    >
      <header className="flex items-center gap-3">
        <span
          className={`inline-flex size-10 shrink-0 items-center justify-center rounded-md text-sm font-semibold ${meta.iconTone}`}
        >
          {meta.initials}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-ink truncate text-sm font-semibold">{row.displayName}</div>
          <div className="text-muted-foreground truncate text-xs">
            {meta.kindLabel} · {hasKey ? row.keyFormat ?? 'không rõ định dạng' : 'chưa cấu hình'}
          </div>
        </div>
      </header>

      <div className="flex flex-wrap items-center gap-2 text-[11px]">
        {hasKey ? (
          <ProviderDot tone="ok">1 key</ProviderDot>
        ) : (
          <ProviderDot tone="empty">No connections</ProviderDot>
        )}
        {row.rotationRecommended && <ProviderDot tone="warn">Nên xoay khóa</ProviderDot>}
        {row.dependentsCount > 0 && (
          <ProviderDot tone="info">{row.dependentsCount} dependents</ProviderDot>
        )}
      </div>

      <footer className="border-border flex items-center justify-between gap-3 border-t pt-3">
        <span className="text-muted-foreground truncate font-mono text-[11px]">
          {row.lastRotatedAt
            ? `Lần xoay · ${formatTimestamp(row.lastRotatedAt)}`
            : 'Chưa từng xoay khóa'}
        </span>
        <StateBadge active={hasKey} />
      </footer>
    </button>
  );
}

function ProviderDot({
  tone,
  children,
}: {
  tone: 'ok' | 'warn' | 'empty' | 'info';
  children: React.ReactNode;
}) {
  const palette: Record<typeof tone, string> = {
    ok: 'bg-green-soft text-green',
    warn: 'bg-amber-soft text-amber',
    empty: 'bg-muted text-muted-foreground',
    info: 'bg-violet-soft text-primary',
  } as const;
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 font-mono text-[10px] tracking-wide uppercase ${palette[tone]}`}
    >
      <span className="size-1.5 rounded-full bg-current opacity-80" aria-hidden />
      {children}
    </span>
  );
}

function StateBadge({ active }: { active: boolean }) {
  if (active) {
    return <Badge className="bg-green-soft text-green hover:bg-green-soft">ACTIVE</Badge>;
  }
  return <Badge variant="secondary">OFF</Badge>;
}

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString('vi-VN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
