import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import {
  ArrowLeftIcon,
  CheckCircle2Icon,
  CircleIcon,
  Loader2Icon,
  XCircleIcon,
} from 'lucide-react';
import { z } from 'zod';

import { JsonDiffViewer } from '@/components/JsonDiffViewer';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  catalogFeatures,
  featureLabel,
  providerLabel,
  type CatalogFeature,
  type CatalogProvider,
} from '@/features/catalog/catalog-api';
import { useSyncCancel } from '@/features/catalog/use-sync-cancel';
import { useSyncConfirm } from '@/features/catalog/use-sync-confirm';
import { useSyncDiff } from '@/features/catalog/use-sync-diff';

const catalogSyncSearchSchema = z.object({
  provider: z
    .enum(['OPENAI', 'GOOGLE', 'DEEPSEEK', 'OPENROUTER', 'ROUTER_9R'])
    .default('OPENAI')
    .catch('OPENAI'),
});

export const Route = createFileRoute('/_authenticated/catalog-sync/$jobId')({
  validateSearch: catalogSyncSearchSchema,
  component: CatalogSyncRoute,
});

function CatalogSyncRoute() {
  const { jobId } = Route.useParams();
  const { provider } = Route.useSearch();
  const navigate = useNavigate();
  const syncDiff = useSyncDiff(jobId);
  const confirmSync = useSyncConfirm();
  const cancelSync = useSyncCancel();
  const diff = syncDiff.data;
  const ready = diff?.status === 'AWAITING_CONFIRM';
  const totalChanges =
    (diff?.added.length ?? 0) + (diff?.removed.length ?? 0) + (diff?.changed.length ?? 0);

  async function confirm() {
    await confirmSync.mutateAsync({
      jobId,
      reason: `Xác nhận đồng bộ danh mục ${providerLabel(provider as CatalogProvider)}`,
    });
    await navigate({
      to: '/catalog',
      search: {
        provider: provider as CatalogProvider,
        feature: 'CHAT',
        syncOk: '1',
      },
    });
  }

  async function cancel() {
    await cancelSync.mutateAsync(jobId);
    await navigate({
      to: '/catalog',
      search: {
        provider: provider as CatalogProvider,
        feature: 'CHAT',
      },
    });
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <Link
            to="/catalog"
            search={{ provider: provider as CatalogProvider, feature: 'CHAT' }}
            className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'mb-2 px-0' })}
          >
            <ArrowLeftIcon className="size-4" />
            Danh mục mô hình
          </Link>
          <p className="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            Đồng bộ danh mục
          </p>
          <h1 className="text-ink text-xl font-semibold">
            Đồng bộ {providerLabel(provider as CatalogProvider)}
          </h1>
        </div>
        <Badge variant={ready ? 'default' : 'secondary'}>
          {ready ? `${totalChanges} thay đổi` : 'Đang lấy dữ liệu'}
        </Badge>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Đồng bộ từ /models</CardTitle>
          <CardDescription>
            Xem lại đầu ra từ nhà cung cấp trước khi commit bất kỳ thay đổi nào vào danh mục.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <ol className="grid gap-3 md:grid-cols-3">
            <Step done label="1. Lấy dữ liệu" />
            <Step active={!ready} done={ready} label="2. So sánh" />
            <Step active={ready} label="3. Xác nhận" />
          </ol>

          {!ready && (
            <div className="border-border bg-secondary text-muted-foreground rounded-md border px-4 py-8 text-sm">
              <Loader2Icon className="mr-2 inline size-4 animate-spin" />
              Đang chờ worker lấy và xác thực các mô hình của nhà cung cấp.
            </div>
          )}

          {syncDiff.isError && (
            <div className="border-border bg-secondary text-destructive rounded-md border px-4 py-8 text-sm">
              Không tải được bản so sánh đồng bộ danh mục.
            </div>
          )}

          {ready && diff && (
            <div className="space-y-5">
              <div className="grid gap-3 md:grid-cols-3">
                <ChangeCount label="Thêm" value={diff.added.length} />
                <ChangeCount label="Xóa" value={diff.removed.length} />
                <ChangeCount label="Thay đổi" value={diff.changed.length} />
              </div>
              <JsonDiffViewer
                before={{ removed: diff.removed, changed: diff.changed }}
                after={{ added: diff.added, changed: diff.changed }}
              />
              <div className="space-y-3">
                {catalogFeatures.map((feature) => (
                  <FeatureChangePreview key={feature} feature={feature} diff={diff} />
                ))}
              </div>
              <div className="border-border flex justify-end gap-2 border-t pt-4">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void cancel()}
                  disabled={cancelSync.isPending || confirmSync.isPending}
                >
                  <XCircleIcon className="size-4" />
                  Hủy
                </Button>
                <Button
                  type="button"
                  onClick={() => void confirm()}
                  disabled={confirmSync.isPending}
                >
                  <CheckCircle2Icon className="size-4" />
                  Xác nhận đồng bộ
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function Step({
  label,
  done = false,
  active = false,
}: {
  label: string;
  done?: boolean;
  active?: boolean;
}) {
  return (
    <li
      className={`flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${active ? 'border-primary bg-violet-soft text-primary' : 'border-border bg-secondary text-ink-2'}`}
    >
      {done ? (
        <CheckCircle2Icon className="text-green size-4" />
      ) : (
        <CircleIcon className="size-4" />
      )}
      {label}
    </li>
  );
}

function ChangeCount({ label, value }: { label: string; value: number }) {
  return (
    <div className="border-border bg-secondary rounded-md border px-3 py-2">
      <div className="text-muted-foreground text-xs">{label}</div>
      <div className="text-ink font-mono text-lg">{value}</div>
    </div>
  );
}

function FeatureChangePreview({
  feature,
  diff,
}: {
  feature: CatalogFeature;
  diff: NonNullable<ReturnType<typeof useSyncDiff>['data']>;
}) {
  const rows = [...diff.added, ...diff.changed, ...diff.removed].filter(
    (model) => !model.defaultModel || model.provider,
  );
  return (
    <section className="border-border rounded-md border px-3 py-2">
      <div className="text-muted-foreground mb-2 text-xs font-semibold tracking-wide uppercase">
        Ảnh hưởng tới {featureLabel(feature)}
      </div>
      <div className="flex flex-wrap gap-2">
        {rows.length === 0 ? (
          <span className="text-muted-foreground text-sm">Không có thay đổi mô hình.</span>
        ) : (
          rows.slice(0, 4).map((model) => (
            <Badge key={`${feature}:${model.modelId}`} variant="secondary" className="font-mono">
              {model.modelId}
            </Badge>
          ))
        )}
      </div>
    </section>
  );
}
