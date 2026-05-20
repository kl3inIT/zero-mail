import { createFileRoute, Link, Outlet, useLocation, useNavigate } from '@tanstack/react-router';
import { Building2Icon, FilterIcon } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { z } from 'zod';

import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { TenantListFilters, TenantListRow, TenantStatusFilter } from '@/features/tenants/tenants-api';
import { useTenantList } from '@/features/tenants/use-tenant-list';

const tenantListSearchSchema = z.object({
  status: z.enum(['ACTIVE', 'PAUSED', 'DISCONNECTED']).optional().catch(undefined),
  from: z.string().optional().catch(undefined),
  to: z.string().optional().catch(undefined),
  cursor: z.string().optional().catch(undefined),
});

export const Route = createFileRoute('/_authenticated/tenants')({
  validateSearch: tenantListSearchSchema,
  component: TenantsRoute,
});

function TenantsRoute() {
  const location = useLocation();
  const search = Route.useSearch();
  const navigate = useNavigate();
  const [status, setStatus] = useState<TenantStatusFilter>(search.status ?? 'ALL');
  const [from, setFrom] = useState(search.from ?? '');
  const [to, setTo] = useState(search.to ?? '');
  const filters = useMemo<TenantListFilters>(
    () => ({
      status: search.status,
      from: search.from,
      to: search.to,
      cursor: search.cursor,
      limit: 25,
    }),
    [search],
  );
  const isListRoute = location.pathname === '/tenants';
  const tenantList = useTenantList(filters, isListRoute);
  const rows = tenantList.data?.rows ?? [];

  useEffect(() => {
    setStatus(search.status ?? 'ALL');
    setFrom(search.from ?? '');
    setTo(search.to ?? '');
  }, [search]);

  if (!isListRoute) {
    return <Outlet />;
  }

  function applyFilters(nextCursor?: string) {
    void navigate({
      to: '/tenants',
      search: {
        status: status === 'ALL' ? undefined : status,
        from: from || undefined,
        to: to || undefined,
        cursor: nextCursor,
      },
    });
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Operations</p>
          <h1 className="text-xl font-semibold text-ink">Tenants</h1>
        </div>
        <Badge variant="secondary">{tenantList.isLoading ? 'Loading' : `${rows.length} visible`}</Badge>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Tenant metadata only. Exact per-tenant cost is not exposed.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4 md:grid-cols-[180px_180px_180px_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              applyFilters();
            }}
          >
            <div className="space-y-2">
              <Label>Status</Label>
              <Select value={status} onValueChange={(value) => setStatus(value as TenantStatusFilter)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All statuses</SelectItem>
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="PAUSED">Paused</SelectItem>
                  <SelectItem value="DISCONNECTED">Disconnected</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="tenant-from">From</Label>
              <Input id="tenant-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tenant-to">To</Label>
              <Input id="tenant-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit">
                <FilterIcon className="size-4" />
                Apply filters
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setStatus('ALL');
                  setFrom('');
                  setTo('');
                  void navigate({ to: '/tenants', search: {} });
                }}
              >
                Clear
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Tenant directory</CardTitle>
          <CardDescription>
            Per-tenant spend bucketed by 7-day k-anonymity (k&gt;=5). Exact per-tenant cost is not exposed.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tenant</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>Gmail</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>7d spend bucket</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tenantList.isLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                    Loading tenants.
                  </TableCell>
                </TableRow>
              )}
              {!tenantList.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="h-24 text-center">
                    <div className="font-medium">No tenants in this segment</div>
                    <div className="text-sm text-muted-foreground">Adjust the date range or status filter.</div>
                  </TableCell>
                </TableRow>
              )}
              {rows.map((row) => (
                <TenantRow key={row.tenantId} row={row} />
              ))}
            </TableBody>
          </Table>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" disabled={!search.cursor} onClick={() => applyFilters()}>
              First page
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={!tenantList.data?.hasNextPage || !tenantList.data.nextCursor}
              onClick={() => applyFilters(tenantList.data?.nextCursor)}
            >
              Next page
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function TenantRow({ row }: { row: TenantListRow }) {
  return (
    <TableRow>
      <TableCell>
        <span className="inline-flex items-center gap-2">
          <Building2Icon className="size-4 text-muted-foreground" />
          <span className="font-mono text-xs">{shortId(row.tenantId)}</span>
        </span>
      </TableCell>
      <TableCell className="font-mono text-xs">{formatDateTime(row.createdAt)}</TableCell>
      <TableCell>{row.gmailAccountEmail ?? '-'}</TableCell>
      <TableCell>
        <StatusBadge status={row.status} />
      </TableCell>
      <TableCell>
        <SpendBucketBadge bucket={row.spendBucket7d} />
      </TableCell>
      <TableCell className="text-right">
        <Link
          to="/tenants/$tenantId"
          params={{ tenantId: row.tenantId }}
          search={{ tab: 'overview' }}
          className={buttonVariants({ variant: 'outline', size: 'sm' })}
        >
          View details
        </Link>
      </TableCell>
    </TableRow>
  );
}

export function StatusBadge({ status }: { status: TenantListRow['status'] }) {
  if (status === 'ACTIVE') {
    return <Badge>Active</Badge>;
  }
  if (status === 'PAUSED') {
    return <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">Paused</Badge>;
  }
  return <Badge variant="secondary">Disconnected</Badge>;
}

export function SpendBucketBadge({ bucket }: { bucket: TenantListRow['spendBucket7d'] }) {
  if (bucket === 'HIGH') {
    return <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">High</Badge>;
  }
  if (bucket === 'MEDIUM') {
    return <Badge variant="outline">Medium</Badge>;
  }
  return <Badge variant="secondary">Low</Badge>;
}

export function formatDateTime(value?: string): string {
  if (!value) {
    return '-';
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function shortId(value: string): string {
  return value.slice(0, 8);
}
