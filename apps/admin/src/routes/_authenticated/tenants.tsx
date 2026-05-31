import { createFileRoute, Link, Outlet, useLocation, useNavigate } from '@tanstack/react-router';
import { Building2Icon, FilterIcon } from 'lucide-react';
import { useMemo, useState } from 'react';
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

const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

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
  // Mirror URL search params into the form draft when the URL changes (browser
  // back, deep links). Uses React 19's "adjust state during render" pattern —
  // tracks the previous search value and resets the draft inline rather than
  // via useEffect, so there is no cascading render and no setState-in-effect.
  // https://react.dev/reference/react/useState#storing-information-from-previous-renders
  const [prevSearch, setPrevSearch] = useState(search);
  if (prevSearch !== search) {
    setPrevSearch(search);
    setStatus(search.status ?? 'ALL');
    setFrom(search.from ?? '');
    setTo(search.to ?? '');
  }
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
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Vận hành</p>
          <h1 className="text-xl font-semibold text-ink">Khách hàng</h1>
        </div>
        <Badge variant="secondary">{tenantList.isLoading ? 'Đang tải' : `${rows.length} hiển thị`}</Badge>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Bộ lọc</CardTitle>
          <CardDescription>Chỉ siêu dữ liệu khách hàng. Không hiển thị chi phí chính xác từng khách hàng.</CardDescription>
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
              <Label>Trạng thái</Label>
              <Select value={status} onValueChange={(value) => setStatus(value as TenantStatusFilter)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">Tất cả trạng thái</SelectItem>
                  <SelectItem value="ACTIVE">Đang hoạt động</SelectItem>
                  <SelectItem value="PAUSED">Tạm dừng</SelectItem>
                  <SelectItem value="DISCONNECTED">Đã ngắt kết nối</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="tenant-from">Từ ngày</Label>
              <Input id="tenant-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tenant-to">Đến ngày</Label>
              <Input id="tenant-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit">
                <FilterIcon className="size-4" />
                Áp dụng
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
                Xóa bộ lọc
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Danh sách khách hàng</CardTitle>
          <CardDescription>
            Chi phí từng khách hàng được nhóm theo k-anonymity 7 ngày (k&gt;=5). Không hiển thị chi phí chính xác.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Khách hàng</TableHead>
                <TableHead>Ngày tạo</TableHead>
                <TableHead>Gmail</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead>Nhóm chi phí 7 ngày</TableHead>
                <TableHead className="text-right">Hành động</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tenantList.isLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                    Đang tải danh sách khách hàng.
                  </TableCell>
                </TableRow>
              )}
              {!tenantList.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="h-24 text-center">
                    <div className="font-medium">Không có khách hàng nào trong nhóm này</div>
                    <div className="text-sm text-muted-foreground">Điều chỉnh khoảng ngày hoặc bộ lọc trạng thái.</div>
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
              Trang đầu
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={!tenantList.data?.hasNextPage || !tenantList.data.nextCursor}
              onClick={() => applyFilters(tenantList.data?.nextCursor)}
            >
              Trang sau
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
          Xem chi tiết
        </Link>
      </TableCell>
    </TableRow>
  );
}

export function StatusBadge({ status }: { status: TenantListRow['status'] }) {
  if (status === 'ACTIVE') {
    return <Badge>Đang hoạt động</Badge>;
  }
  if (status === 'PAUSED') {
    return <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">Tạm dừng</Badge>;
  }
  return <Badge variant="secondary">Đã ngắt kết nối</Badge>;
}

export function SpendBucketBadge({ bucket }: { bucket: TenantListRow['spendBucket7d'] }) {
  if (bucket === 'HIGH') {
    return <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">Cao</Badge>;
  }
  if (bucket === 'MEDIUM') {
    return <Badge variant="outline">Trung bình</Badge>;
  }
  return <Badge variant="secondary">Thấp</Badge>;
}

export function formatDateTime(value?: string): string {
  if (!value) {
    return '-';
  }
  return dateTimeFormatter.format(new Date(value));
}

function shortId(value: string): string {
  return value.slice(0, 8);
}
