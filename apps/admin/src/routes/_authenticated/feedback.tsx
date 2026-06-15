import { createFileRoute } from '@tanstack/react-router';
import { CheckCircle2Icon, CircleDotIcon, MailIcon, RefreshCwIcon } from 'lucide-react';
import { useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import type { FeedbackRow, FeedbackStatusFilter } from '@/features/feedback/feedback-api';
import { useFeedbackList, useResolveFeedback, useReopenFeedback } from '@/features/feedback/use-feedback-list';

export const Route = createFileRoute('/_authenticated/feedback')({
  component: FeedbackRoute,
});

const TYPE_LABELS: Record<string, string> = {
  BUG_REPORT: 'Bug',
  FEATURE_REQUEST: 'Feature',
  GENERAL: 'General',
};

const TYPE_COLORS: Record<string, string> = {
  BUG_REPORT: 'destructive',
  FEATURE_REQUEST: 'default',
  GENERAL: 'secondary',
};

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

function FeedbackDetailDialog({
  row,
  open,
  onClose,
}: {
  row: FeedbackRow;
  open: boolean;
  onClose: () => void;
}) {
  const [adminNotes, setAdminNotes] = useState(row.adminNotes ?? '');
  const resolveMutation = useResolveFeedback();
  const reopenMutation = useReopenFeedback();

  function handleResolve() {
    resolveMutation.mutate({ id: row.id, adminNotes: adminNotes.trim() || undefined }, { onSuccess: onClose });
  }

  function handleReopen() {
    reopenMutation.mutate(row.id, { onSuccess: onClose });
  }

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Badge variant={TYPE_COLORS[row.type] as 'default' | 'secondary' | 'destructive' | 'outline'}>
              {TYPE_LABELS[row.type] ?? row.type}
            </Badge>
            <span className="truncate">{row.subject}</span>
          </DialogTitle>
          <DialogDescription>
            From {row.contactEmail} — {dateFormatter.format(new Date(row.createdAt))}
            {row.tenantId && <span className="ml-2 font-mono text-xs opacity-60">{row.tenantId}</span>}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="bg-muted rounded-lg p-4">
            <p className="text-sm whitespace-pre-wrap">{row.message}</p>
          </div>

          {row.status === 'OPEN' ? (
            <div className="space-y-2">
              <label className="text-sm font-medium">Internal notes (optional)</label>
              <Textarea
                value={adminNotes}
                onChange={(e) => setAdminNotes(e.target.value)}
                placeholder="Notes visible only to admins…"
                rows={3}
                maxLength={2000}
              />
            </div>
          ) : (
            row.adminNotes && (
              <div className="space-y-1">
                <p className="text-muted-foreground text-xs font-medium uppercase tracking-wide">Admin notes</p>
                <p className="text-sm">{row.adminNotes}</p>
                {row.resolvedAt && (
                  <p className="text-muted-foreground text-xs">
                    Resolved {dateFormatter.format(new Date(row.resolvedAt))}
                  </p>
                )}
              </div>
            )
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={onClose}>
              Close
            </Button>
            {row.status === 'OPEN' ? (
              <Button
                onClick={handleResolve}
                disabled={resolveMutation.isPending}
              >
                <CheckCircle2Icon className="size-4" />
                Mark resolved
              </Button>
            ) : (
              <Button
                variant="outline"
                onClick={handleReopen}
                disabled={reopenMutation.isPending}
              >
                <RefreshCwIcon className="size-4" />
                Reopen
              </Button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function FeedbackRoute() {
  const [statusFilter, setStatusFilter] = useState<FeedbackStatusFilter>('OPEN');
  const [selectedRow, setSelectedRow] = useState<FeedbackRow | null>(null);
  const feedbackQuery = useFeedbackList(statusFilter);
  const rows = feedbackQuery.data?.rows ?? [];
  const openCount = feedbackQuery.data?.openCount ?? 0;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-ink text-2xl font-semibold">Feedback</h1>
          <p className="text-ink-2 mt-0.5 text-sm">User-submitted bug reports, feature requests, and inquiries.</p>
        </div>
        {openCount > 0 && (
          <Badge variant="destructive" className="mt-1 text-sm">
            {openCount} open
          </Badge>
        )}
      </div>

      <div className="flex items-center gap-3">
        <Select
          value={statusFilter}
          onValueChange={(value) => setStatusFilter(value as FeedbackStatusFilter)}
        >
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Tất cả</SelectItem>
            <SelectItem value="OPEN">Chưa xử lý</SelectItem>
            <SelectItem value="RESOLVED">Đã xử lý</SelectItem>
          </SelectContent>
        </Select>

        <Button
          variant="outline"
          size="icon"
          onClick={() => feedbackQuery.refetch()}
          disabled={feedbackQuery.isFetching}
        >
          <RefreshCwIcon className={feedbackQuery.isFetching ? 'size-4 animate-spin' : 'size-4'} />
        </Button>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium">
            {rows.length} submission{rows.length !== 1 ? 's' : ''}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {feedbackQuery.isLoading ? (
            <div className="space-y-2 p-4">
              {Array.from({ length: 5 }).map((_, index) => (
                <Skeleton key={index} className="h-10 w-full" />
              ))}
            </div>
          ) : rows.length === 0 ? (
            <div className="flex flex-col items-center gap-2 py-16 text-center">
              <MailIcon className="text-muted-foreground size-8" />
              <p className="text-muted-foreground text-sm">No submissions yet</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-24">Type</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead className="w-48">Email</TableHead>
                  <TableHead className="w-24">Status</TableHead>
                  <TableHead className="w-36">Received</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    onClick={() => setSelectedRow(row)}
                  >
                    <TableCell>
                      <Badge
                        variant={TYPE_COLORS[row.type] as 'default' | 'secondary' | 'destructive' | 'outline'}
                        className="text-xs"
                      >
                        {TYPE_LABELS[row.type] ?? row.type}
                      </Badge>
                    </TableCell>
                    <TableCell className="font-medium">{row.subject}</TableCell>
                    <TableCell className="text-muted-foreground text-xs">{row.contactEmail}</TableCell>
                    <TableCell>
                      {row.status === 'OPEN' ? (
                        <span className="text-destructive flex items-center gap-1 text-xs font-medium">
                          <CircleDotIcon className="size-3.5" />
                          Open
                        </span>
                      ) : (
                        <span className="text-muted-foreground flex items-center gap-1 text-xs">
                          <CheckCircle2Icon className="size-3.5" />
                          Resolved
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {dateFormatter.format(new Date(row.createdAt))}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {selectedRow && (
        <FeedbackDetailDialog
          row={selectedRow}
          open={selectedRow !== null}
          onClose={() => setSelectedRow(null)}
        />
      )}
    </div>
  );
}
