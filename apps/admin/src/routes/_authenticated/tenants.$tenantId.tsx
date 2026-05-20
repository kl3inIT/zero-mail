import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import {
  ActivityIcon,
  ArrowLeftIcon,
  BadgeDollarSignIcon,
  CreditCardIcon,
  MailXIcon,
  PauseIcon,
  ShieldCheckIcon,
  Trash2Icon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { z } from 'zod';

import { ConfirmTwiceDialog } from '@/components/ConfirmTwiceDialog';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { TenantDeletionPreviewResponse, TenantDetailTab } from '@/features/tenants/tenants-api';
import {
  useTenantActivity,
  useTenantBilling,
  useTenantDeletionPreview,
  useTenantHealth,
  useTenantOverview,
  useTenantSpend,
} from '@/features/tenants/use-tenant-detail';
import { useTenantDelete } from '@/features/tenants/use-tenant-delete';
import { useTenantDisconnect } from '@/features/tenants/use-tenant-disconnect';
import { useTenantPause } from '@/features/tenants/use-tenant-pause';

import { StatusBadge, formatDateTime } from './tenants';

const tenantDetailSearchSchema = z.object({
  tab: z.enum(['overview', 'health', 'billing', 'spend', 'activity']).default('overview').catch('overview'),
});

type TenantDialogAction = 'pause' | 'disconnect' | 'delete';

export const Route = createFileRoute('/_authenticated/tenants/$tenantId')({
  validateSearch: tenantDetailSearchSchema,
  component: TenantDetailRoute,
});

function TenantDetailRoute() {
  const { tenantId } = Route.useParams();
  const { tab } = Route.useSearch();
  const navigate = useNavigate();
  const [dialogAction, setDialogAction] = useState<TenantDialogAction | null>(null);
  const overview = useTenantOverview(tenantId, { enabled: tab === 'overview' });
  const health = useTenantHealth(tenantId, { enabled: tab === 'health' });
  const billing = useTenantBilling(tenantId, { enabled: tab === 'billing' });
  const spend = useTenantSpend(tenantId, { enabled: tab === 'spend' });
  const activity = useTenantActivity(tenantId, { enabled: tab === 'activity' });
  const deletionPreview = useTenantDeletionPreview(tenantId, dialogAction === 'delete');
  const pauseTenant = useTenantPause();
  const disconnectTenant = useTenantDisconnect();
  const deleteTenant = useTenantDelete();
  const overviewData = overview.data;
  const targetEmail = overviewData?.gmailAccountEmail ?? '';
  const dialogConfig = useMemo(
    () =>
      dialogAction
        ? buildDialogConfig({
            action: dialogAction,
            email: targetEmail,
            deletionPreview: deletionPreview.data,
            deletionPreviewLoading: deletionPreview.isLoading,
          })
        : null,
    [dialogAction, targetEmail, deletionPreview.data, deletionPreview.isLoading],
  );

  function changeTab(nextTab: string) {
    void navigate({
      to: '/tenants/$tenantId',
      params: { tenantId },
      search: { tab: nextTab as TenantDetailTab },
    });
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <Link to="/tenants" className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'mb-2 px-0' })}>
            <ArrowLeftIcon className="size-4" />
            Tenants
          </Link>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Tenant inspection</p>
          <h1 className="break-all text-xl font-semibold text-ink">{targetEmail || tenantId}</h1>
        </div>
        {overviewData && <StatusBadge status={overviewData.status} />}
      </header>

      <Tabs value={tab} onValueChange={changeTab}>
        <TabsList variant="line" className="w-full justify-start">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="health">Health</TabsTrigger>
          <TabsTrigger value="billing">Billing</TabsTrigger>
          <TabsTrigger value="spend">Spend</TabsTrigger>
          <TabsTrigger value="activity">Activity</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <PanelState isLoading={overview.isLoading} isError={overview.isError}>
            {overviewData && (
              <Card>
                <CardHeader>
                  <CardTitle>Overview</CardTitle>
                  <CardDescription>Metadata-only tenant snapshot.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-6">
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Tenant ID" value={overviewData.tenantId} mono />
                    <Fact label="Created" value={formatDateTime(overviewData.createdAt)} mono />
                    <Fact label="Gmail account" value={overviewData.gmailAccountEmail ?? '-'} />
                    <Fact label="Last activity" value={formatDateTime(overviewData.lastActivityAt)} mono />
                    <Fact label="Rules" value={String(overviewData.rulesCount)} />
                  </dl>
                  <div className="flex flex-wrap gap-2 border-t border-border pt-4">
                    <Button variant="destructive" onClick={() => setDialogAction('pause')}>
                      <PauseIcon className="size-4" />
                      Pause
                    </Button>
                    <Button
                      variant="destructive"
                      disabled={!overviewData.gmailAccountEmail}
                      onClick={() => setDialogAction('disconnect')}
                    >
                      <MailXIcon className="size-4" />
                      Disconnect Gmail
                    </Button>
                    <Button
                      variant="destructive"
                      disabled={!overviewData.gmailAccountEmail}
                      onClick={() => setDialogAction('delete')}
                    >
                      <Trash2Icon className="size-4" />
                      Delete tenant
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="health">
          <PanelState isLoading={health.isLoading} isError={health.isError}>
            {health.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <ShieldCheckIcon className="size-4 text-muted-foreground" />
                    Health
                  </CardTitle>
                  <CardDescription>Gmail connection and push metadata.</CardDescription>
                </CardHeader>
                <CardContent>
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Token refresh" value={health.data.tokenRefreshStatus} />
                    <Fact label="Last token refresh" value={formatDateTime(health.data.lastTokenRefreshAt)} mono />
                    <Fact label="Watch" value={health.data.watchStatus} />
                    <Fact label="Last Pub/Sub push" value={formatDateTime(health.data.lastPubSubPushAt)} mono />
                    <Fact label="Pub/Sub backlog" value={String(health.data.pubsubBacklogCount)} />
                  </dl>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="billing">
          <PanelState isLoading={billing.isLoading} isError={billing.isError}>
            {billing.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <CreditCardIcon className="size-4 text-muted-foreground" />
                    Billing
                  </CardTitle>
                  <CardDescription>Credit balance metadata.</CardDescription>
                </CardHeader>
                <CardContent>
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Credits balance" value={formatInteger(billing.data.creditsBalance)} />
                    <Fact label="Plan" value={billing.data.plan} />
                    <Fact label="Last top-up" value={formatDateTime(billing.data.lastTopUpAt)} mono />
                  </dl>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="spend">
          <PanelState isLoading={spend.isLoading} isError={spend.isError}>
            {spend.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <BadgeDollarSignIcon className="size-4 text-muted-foreground" />
                    Spend
                  </CardTitle>
                  <CardDescription>Bucketed call counts, not exact per-tenant cost.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Last 7d calls" value={formatInteger(spend.data.last7dCallCount)} />
                    <Fact label="Last 30d calls" value={formatInteger(spend.data.last30dCallCount)} />
                    <Fact label="7d bucket" value={spend.data.spendBucket7d} />
                    <Fact label="30d bucket" value={spend.data.spendBucket30d} />
                  </dl>
                  <div className="space-y-2">
                    <h2 className="text-sm font-semibold">Per-feature calls</h2>
                    <div className="grid gap-2 md:grid-cols-3">
                      {Object.entries(spend.data.perFeatureCallCount).map(([feature, count]) => (
                        <div key={feature} className="rounded-md border border-border px-3 py-2">
                          <div className="text-xs text-muted-foreground">{feature}</div>
                          <div className="font-mono text-sm">{formatInteger(count)}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="activity">
          <PanelState isLoading={activity.isLoading} isError={activity.isError}>
            {activity.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <ActivityIcon className="size-4 text-muted-foreground" />
                    Activity
                  </CardTitle>
                  <CardDescription>Session metadata only.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="30d rule fires" value={formatInteger(activity.data.last30dRuleFireCount)} />
                    <Fact label="Chat sessions" value={formatInteger(activity.data.chatSessionCount)} />
                    <Fact label="Last chat session" value={formatDateTime(activity.data.lastChatSessionAt)} mono />
                    <Fact label="Last model selection" value={activity.data.lastChatModelSelection ?? '-'} mono />
                  </dl>
                  {activity.data.chatSessionCount === 0 && (
                    <div className="rounded-md border border-border bg-secondary px-3 py-2 text-sm">
                      <div className="font-medium">No activity in the last 30 days</div>
                      <div className="text-muted-foreground">
                        This tenant has not used Zero Mail recently. Inbox push subscriptions remain valid.
                      </div>
                    </div>
                  )}
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger render={<span className="inline-flex w-fit" />}>
                        <Button disabled>Show details</Button>
                      </TooltipTrigger>
                      <TooltipContent>
                        Session detail inspection is deferred to v1.3+ via tenant-bound support ticket grant.
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>
      </Tabs>

      {dialogConfig && (
        <ConfirmTwiceDialog
          open={dialogAction !== null}
          onOpenChange={(open) => {
            if (!open) {
              setDialogAction(null);
            }
          }}
          actionLabel={dialogConfig.actionLabel}
          targetLabel={dialogConfig.targetLabel}
          consequences={dialogConfig.consequences}
          confirmationToken={dialogConfig.confirmationToken}
          finalButtonLabel={dialogConfig.finalButtonLabel}
          onConfirm={async (reason) => {
            if (dialogAction === 'pause') {
              await pauseTenant.mutateAsync({ tenantId, reason });
            } else if (dialogAction === 'disconnect') {
              await disconnectTenant.mutateAsync({ tenantId, reason });
            } else if (dialogAction === 'delete') {
              await deleteTenant.mutateAsync({ tenantId, reason, confirmEmail: targetEmail });
            }
            // WR-10: backend returns 204 No Content; no fabricated audit id.
            return {};
          }}
        />
      )}
    </div>
  );
}

function PanelState({
  isLoading,
  isError,
  children,
}: {
  isLoading: boolean;
  isError: boolean;
  children: ReactNode;
}) {
  if (isLoading) {
    return (
      <Card>
        <CardContent className="py-8 text-sm text-muted-foreground">Loading tenant metadata.</CardContent>
      </Card>
    );
  }
  if (isError) {
    return (
      <Card>
        <CardContent className="py-8 text-sm text-destructive">Unable to load tenant metadata.</CardContent>
      </Card>
    );
  }
  return <>{children}</>;
}

function Fact({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="space-y-1">
      <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd className={mono ? 'break-all font-mono text-sm text-ink' : 'break-words text-sm text-ink'}>{value}</dd>
    </div>
  );
}

function buildDialogConfig({
  action,
  email,
  deletionPreview,
  deletionPreviewLoading,
}: {
  action: TenantDialogAction;
  email: string;
  deletionPreview?: TenantDeletionPreviewResponse;
  deletionPreviewLoading: boolean;
}) {
  if (action === 'pause') {
    return {
      actionLabel: 'Pause tenant',
      targetLabel: email || 'tenant',
      confirmationToken: 'pause',
      finalButtonLabel: 'Pause tenant',
      consequences: [
        'Automatic triage and rule firing will stop for this tenant.',
        'Existing metadata and audit history remain available.',
        'The reason is recorded in the admin audit log.',
      ],
    };
  }
  if (action === 'disconnect') {
    return {
      actionLabel: 'Disconnect Gmail',
      targetLabel: email,
      confirmationToken: email,
      finalButtonLabel: 'Disconnect Gmail',
      consequences: [
        'Gmail OAuth revocation will be queued without exposing token bytes to admin code.',
        'Future Gmail push deliveries for this tenant will stop after revocation succeeds.',
        'The reason is recorded in the admin audit log.',
      ],
    };
  }
  return {
    actionLabel: 'Delete tenant',
    targetLabel: email,
    confirmationToken: email,
    finalButtonLabel: 'Delete tenant',
    consequences: deletionConsequences(deletionPreview, deletionPreviewLoading),
  };
}

function deletionConsequences(preview?: TenantDeletionPreviewResponse, loading = false): string[] {
  if (loading) {
    return ['Deletion preview is loading.', 'This action is irreversible after final confirmation.'];
  }
  if (!preview) {
    return ['Deletion preview is unavailable.', 'This action is irreversible after final confirmation.'];
  }
  return [
    `${preview.gmailConnections} Gmail connection row(s) will be removed.`,
    `${preview.chatSessions} chat session row(s) and ${preview.chatMessages} chat message row(s) will be removed.`,
    `${preview.rules} rule row(s) and ${preview.triageAudits} triage audit row(s) will be removed.`,
    `${preview.byokCredentials} BYOK credential row(s) will be removed.`,
    'The tenant row is deleted after the audit entry is recorded.',
  ];
}

function formatInteger(value: number): string {
  return new Intl.NumberFormat().format(value);
}
