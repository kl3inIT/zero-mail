import { createFileRoute, Link, Outlet, useLocation, useNavigate } from '@tanstack/react-router';
import {
  CalendarDaysIcon,
  CheckCircle2Icon,
  ChevronDownIcon,
  FilterIcon,
  SearchIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import type { DateRange } from 'react-day-picker';
import { z } from 'zod';

import { KpiCard } from '@/components/KpiCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Calendar } from '@/components/ui/calendar';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type {
  TenantListFilters,
  TenantListResponse,
  TenantListRow,
  TenantStatusFilter,
} from '@/features/tenants/tenants-api';
import { useTenantList } from '@/features/tenants/use-tenant-list';

const tenantListSearchSchema = z.object({
  status: z.enum(['ACTIVE', 'PAUSED', 'DISCONNECTED']).optional().catch(undefined),
  email: z.string().optional().catch(undefined),
  from: z.string().optional().catch(undefined),
  to: z.string().optional().catch(undefined),
  cursor: z.string().optional().catch(undefined),
});

const integerFormatter = new Intl.NumberFormat();

type TenantDateRange = {
  from: string;
  to: string;
};

type TenantDatePreset = '7d' | '30d' | '3m' | 'custom';

export const Route = createFileRoute('/_authenticated/tenants')({
  validateSearch: tenantListSearchSchema,
  component: TenantsRoute,
});

function TenantsRoute() {
  const location = useLocation();
  const search = Route.useSearch();
  const navigate = useNavigate();
  const defaultDateRange = useMemo(() => getDefaultTenantDateRange(), []);
  const effectiveFrom = search.from ?? defaultDateRange.from;
  const effectiveTo = search.to ?? defaultDateRange.to;
  const effectiveEmail = search.email ?? '';
  const effectiveDatePreset = inferDatePreset(effectiveFrom, effectiveTo);
  const [status, setStatus] = useState<TenantStatusFilter>(search.status ?? 'ALL');
  const [email, setEmail] = useState(effectiveEmail);
  const [from, setFrom] = useState(effectiveFrom);
  const [to, setTo] = useState(effectiveTo);
  const [datePreset, setDatePreset] = useState<TenantDatePreset>(effectiveDatePreset);
  // Mirror URL search params into the form draft when the URL changes (browser
  // back, deep links). Uses React 19's "adjust state during render" pattern —
  // tracks the previous search value and resets the draft inline rather than
  // via useEffect, so there is no cascading render and no setState-in-effect.
  // https://react.dev/reference/react/useState#storing-information-from-previous-renders
  const [prevSearch, setPrevSearch] = useState(search);
  if (prevSearch !== search) {
    setPrevSearch(search);
    setStatus(search.status ?? 'ALL');
    setEmail(search.email ?? '');
    setFrom(search.from ?? defaultDateRange.from);
    setTo(search.to ?? defaultDateRange.to);
    setDatePreset(inferDatePreset(search.from ?? defaultDateRange.from, search.to ?? defaultDateRange.to));
  }
  const filters = useMemo<TenantListFilters>(
    () => ({
      status: search.status,
      email: effectiveEmail || undefined,
      from: effectiveFrom,
      to: effectiveTo,
      cursor: search.cursor,
      limit: 25,
    }),
    [search.status, search.cursor, effectiveEmail, effectiveFrom, effectiveTo],
  );
  const isListRoute = location.pathname === '/tenants';
  const tenantList = useTenantList(filters, isListRoute);
  const rows = tenantList.data?.rows ?? [];
  const summary = tenantList.data?.summary;

  if (!isListRoute) {
    return <Outlet />;
  }

  function applyFilters(nextCursor?: string) {
    void navigate({
      to: '/tenants',
      search: {
        status: status === 'ALL' ? undefined : status,
        email: email.trim() || undefined,
        from: from || undefined,
        to: to || undefined,
        cursor: nextCursor,
      },
    });
  }

  function changeDatePreset(nextDatePreset: TenantDatePreset) {
    setDatePreset(nextDatePreset);
    if (nextDatePreset !== 'custom') {
      const nextDateRange = getPresetDateRange(nextDatePreset);
      setFrom(nextDateRange.from);
      setTo(nextDateRange.to);
    }
  }

  return (
    <div className="min-w-0 space-y-6">
      <header className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold text-ink">Khách hàng</h1>
        </div>
        <Badge variant="secondary" className="w-fit">
          {tenantList.isLoading ? 'Đang tải' : `${rows.length} đang hiển thị`}
        </Badge>
      </header>

      <TenantSummaryCards summary={summary} isLoading={tenantList.isLoading} />

      <Card className="min-w-0">
        <CardHeader>
          <CardTitle>Khách hàng</CardTitle>
        </CardHeader>
        <CardContent className="min-w-0 space-y-4">
          <form
            className="rounded-lg border border-border bg-secondary/40 p-3"
            onSubmit={(event) => {
              event.preventDefault();
              applyFilters();
            }}
          >
            <div className="grid gap-3 lg:grid-cols-[minmax(260px,1fr)_180px_350px_auto] lg:items-start">
              <div className="space-y-2">
                <Label htmlFor="tenant-email">Tìm theo email</Label>
                <div className="relative">
                  <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="tenant-email"
                    aria-label="Tìm theo email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="name@company.com"
                    className="pl-8"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label>Trạng thái tenant</Label>
                <Select value={status} onValueChange={(value) => setStatus(value as TenantStatusFilter)}>
                  <SelectTrigger className="h-8 w-full min-w-0">
                    <span data-slot="select-value">{tenantStatusFilterLabel(status)}</span>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">Tất cả</SelectItem>
                    <SelectItem value="ACTIVE">Tenant hoạt động</SelectItem>
                    <SelectItem value="PAUSED">Tạm dừng</SelectItem>
                    <SelectItem value="DISCONNECTED">Đã ngắt kết nối</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <DateRangeField
                preset={datePreset}
                from={from}
                to={to}
                onPresetChange={changeDatePreset}
                onFromChange={setFrom}
                onToChange={setTo}
              />
              <div className="flex flex-wrap gap-2 pt-6">
                <Button type="submit">
                  <FilterIcon className="size-4" />
                  Áp dụng
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setStatus('ALL');
                    setEmail('');
                    setDatePreset('3m');
                    setFrom(defaultDateRange.from);
                    setTo(defaultDateRange.to);
                    void navigate({ to: '/tenants', search: {} });
                  }}
                >
                  Xóa bộ lọc
                </Button>
              </div>
            </div>
          </form>
          <div className="min-w-0 overflow-x-auto">
            <Table className="min-w-[920px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="min-w-[260px]">Email</TableHead>
                  <TableHead className="min-w-[150px]">Gmail</TableHead>
                  <TableHead className="min-w-[150px]">Telegram</TableHead>
                  <TableHead className="min-w-[180px]">Hoạt động gần nhất</TableHead>
                  <TableHead className="text-right">Hành động</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenantList.isLoading && (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                      Đang tải danh sách khách hàng.
                    </TableCell>
                  </TableRow>
                )}
                {!tenantList.isLoading && rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center">
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
          </div>
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
        <div className="truncate text-sm font-medium text-ink">{row.gmailAccountEmail ?? 'Chưa kết nối Gmail'}</div>
      </TableCell>
      <TableCell>
        <GmailConnectionBadge status={row.gmailConnectionStatus} />
      </TableCell>
      <TableCell>
        <TelegramStatusBadge status={row.telegramStatus} compact />
      </TableCell>
      <TableCell>
        <span className="text-sm text-ink">{activityKindLabel(row.lastActivityKind)}</span>
      </TableCell>
      <TableCell className="text-right">
        <Button
          render={
            <Link
              to="/tenants/$tenantId"
              params={{ tenantId: row.tenantId }}
              search={{ tab: 'activity' }}
              aria-label={`Chi tiết ${row.gmailAccountEmail ?? row.tenantId}`}
            />
          }
          variant="outline"
          size="sm"
        >
          Chi tiết
        </Button>
      </TableCell>
    </TableRow>
  );
}

function TenantSummaryCards({
  summary,
  isLoading,
}: {
  summary?: TenantListResponse['summary'];
  isLoading: boolean;
}) {
  const loadingValue = isLoading ? '-' : '0';
  return (
    <div className="grid min-w-0 gap-3 md:grid-cols-3">
      <KpiCard
        testId="tenant-kpi-total"
        label="Tổng khách"
        value={summary ? formatInteger(summary.totalCount) : loadingValue}
        hint="Theo bộ lọc hiện tại"
      />
      <KpiCard
        testId="tenant-kpi-active"
        label="Gmail đã kết nối"
        value={summary ? formatInteger(summary.gmailConnectedCount) : loadingValue}
        hint={`${summary ? formatInteger(summary.disconnectedCount) : loadingValue} đã ngắt Gmail`}
      />
      <KpiCard
        testId="tenant-kpi-recent"
        label="Hoạt động 7 ngày"
        value={summary ? formatInteger(summary.activeLast7dCount) : loadingValue}
        hint={`${summary ? formatInteger(summary.activeLast24hCount) : loadingValue} trong 24h`}
      />
    </div>
  );
}

function DateRangeField({
  preset,
  from,
  to,
  onPresetChange,
  onFromChange,
  onToChange,
}: {
  preset: TenantDatePreset;
  from: string;
  to: string;
  onPresetChange: (value: TenantDatePreset) => void;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [popoverView, setPopoverView] = useState<'options' | 'calendar'>('options');
  const selectedRange: DateRange = {
    from: parseDateInputValue(from),
    to: parseDateInputValue(to),
  };

  function choosePreset(nextPreset: TenantDatePreset) {
    onPresetChange(nextPreset);
    if (nextPreset === 'custom') {
      setPopoverView('calendar');
    } else {
      setPopoverView('options');
      setOpen(false);
    }
  }

  function selectRange(nextRange: DateRange | undefined) {
    if (nextRange?.from) {
      onFromChange(formatDateInputValue(nextRange.from));
    }
    if (nextRange?.to) {
      onToChange(formatDateInputValue(nextRange.to));
      setPopoverView('options');
      setOpen(false);
    }
  }

  return (
    <div className="space-y-2">
      <Label>Khoảng ngày</Label>
      <Popover
        open={open}
        onOpenChange={(nextOpen) => {
          setOpen(nextOpen);
          if (!nextOpen) {
            setPopoverView('options');
          }
        }}
      >
        <PopoverTrigger
          render={
            <Button
              type="button"
              variant="outline"
              className="h-8 w-full justify-between gap-2 px-2.5 text-left font-normal"
              data-testid="tenant-date-preset"
              role="combobox"
              aria-expanded={open}
            />
          }
        >
          <span className="flex min-w-0 items-center gap-2">
            <CalendarDaysIcon className="size-4 shrink-0 text-muted-foreground" />
            <span className="truncate">
              {preset === 'custom'
                ? `Tùy chọn · ${formatDisplayDate(from)} - ${formatDisplayDate(to)}`
                : datePresetLabel(preset)}
            </span>
          </span>
          <ChevronDownIcon className="size-4 shrink-0 text-muted-foreground" />
        </PopoverTrigger>
        <PopoverContent
          align={popoverView === 'calendar' ? 'end' : 'start'}
          side="bottom"
          className={
            popoverView === 'calendar'
              ? 'max-h-[min(440px,calc(100vh-2rem))] w-auto max-w-[calc(100vw-2rem)] overflow-auto p-2'
              : 'w-[min(350px,calc(100vw-2rem))] p-2'
          }
        >
          {popoverView === 'options' ? (
            <div className="grid gap-1" role="listbox" aria-label="Khoảng ngày">
              <DatePresetOption active={preset === '7d'} onClick={() => choosePreset('7d')}>
                7 ngày qua
              </DatePresetOption>
              <DatePresetOption active={preset === '30d'} onClick={() => choosePreset('30d')}>
                30 ngày qua
              </DatePresetOption>
              <DatePresetOption active={preset === '3m'} onClick={() => choosePreset('3m')}>
                3 tháng qua
              </DatePresetOption>
              <DatePresetOption active={preset === 'custom'} onClick={() => choosePreset('custom')}>
                Tùy chọn
              </DatePresetOption>
            </div>
          ) : (
            <div data-testid="tenant-date-range">
              <Calendar
                mode="range"
                selected={selectedRange}
                onSelect={selectRange}
                numberOfMonths={2}
                captionLayout="dropdown"
              />
            </div>
          )}
        </PopoverContent>
      </Popover>
    </div>
  );
}

function DatePresetOption({
  active,
  children,
  onClick,
}: {
  active: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="option"
      aria-selected={active}
      className="flex h-8 w-full items-center justify-between rounded-md px-2 text-left text-sm hover:bg-accent hover:text-accent-foreground aria-selected:bg-accent aria-selected:text-accent-foreground"
      onClick={onClick}
    >
      <span>{children}</span>
      {active && <CheckCircle2Icon className="size-4" />}
    </button>
  );
}

function GmailConnectionBadge({ status }: { status: TenantListRow['gmailConnectionStatus'] }) {
  if (status === 'CONNECTED') {
    return <Badge variant="outline">Đã kết nối</Badge>;
  }
  return <Badge variant="secondary">Đã ngắt kết nối</Badge>;
}

function TelegramStatusBadge({
  status,
  compact = false,
}: {
  status: TenantListRow['telegramStatus'];
  compact?: boolean;
}) {
  if (status === 'CONNECTED') {
    return <Badge variant="outline">{compact ? 'Đã kết nối' : 'Telegram đã kết nối'}</Badge>;
  }
  if (status === 'BLOCKED') {
    return <Badge variant="destructive">Bị chặn</Badge>;
  }
  if (status === 'DISCONNECTED') {
    return <Badge variant="secondary">Đã ngắt kết nối</Badge>;
  }
  return <Badge variant="secondary">Chưa kết nối</Badge>;
}

function getDefaultTenantDateRange(referenceDate = new Date()): TenantDateRange {
  return getPresetDateRange('3m', referenceDate);
}

function getPresetDateRange(datePreset: Exclude<TenantDatePreset, 'custom'>, referenceDate = new Date()): TenantDateRange {
  const endDate = new Date(referenceDate.getFullYear(), referenceDate.getMonth(), referenceDate.getDate());
  const startDate = new Date(endDate);
  if (datePreset === '7d') {
    startDate.setDate(startDate.getDate() - 7);
  } else if (datePreset === '30d') {
    startDate.setDate(startDate.getDate() - 30);
  } else {
    startDate.setMonth(startDate.getMonth() - 3);
  }
  return {
    from: formatDateInputValue(startDate),
    to: formatDateInputValue(endDate),
  };
}

function inferDatePreset(from: string, to: string): TenantDatePreset {
  for (const datePreset of ['7d', '30d', '3m'] as const) {
    const dateRange = getPresetDateRange(datePreset);
    if (dateRange.from === from && dateRange.to === to) {
      return datePreset;
    }
  }
  return 'custom';
}

function formatDateInputValue(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseDateInputValue(value: string): Date | undefined {
  if (!value) {
    return undefined;
  }
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) {
    return undefined;
  }
  return new Date(year, month - 1, day);
}

function formatDisplayDate(value: string): string {
  const date = parseDateInputValue(value);
  if (!date) {
    return value;
  }
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${date.getFullYear()}`;
}

function formatInteger(value: number): string {
  return integerFormatter.format(value);
}

function tenantStatusFilterLabel(status: TenantStatusFilter): string {
  const labelByStatus: Record<TenantStatusFilter, string> = {
    ALL: 'Tất cả',
    ACTIVE: 'Tenant hoạt động',
    PAUSED: 'Tạm dừng',
    DISCONNECTED: 'Đã ngắt kết nối',
  };
  return labelByStatus[status];
}

function datePresetLabel(datePreset: TenantDatePreset): string {
  const labelByDatePreset: Record<TenantDatePreset, string> = {
    '7d': '7 ngày qua',
    '30d': '30 ngày qua',
    '3m': '3 tháng qua',
    custom: 'Tùy chọn',
  };
  return labelByDatePreset[datePreset];
}

function activityKindLabel(kind: TenantListRow['lastActivityKind']): string {
  const labelByKind: Record<TenantListRow['lastActivityKind'], string> = {
    TENANT_CREATED: 'Tạo khách hàng',
    GMAIL_CONNECTION: 'Gmail connection',
    RULE: 'Rule update',
    GMAIL_OBSERVED: 'Gmail observed',
    TRIAGE: 'Triage',
    CHAT: 'Chat',
    TELEGRAM: 'Telegram',
    ASSISTANT_ACTION: 'Assistant action',
    LLM: 'LLM call',
  };
  return labelByKind[kind];
}
