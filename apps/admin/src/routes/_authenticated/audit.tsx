import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { DownloadIcon } from 'lucide-react';
import { useState } from 'react';

import { JsonDiffViewer } from '@/components/JsonDiffViewer';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { auditCsvUrl, type AuditFilters } from '@/features/audit/audit-api';
import { useAuditPage } from '@/features/audit/use-audit-page';

const auditFilterFields = ['actorEmail', 'action', 'targetKind', 'targetId', 'from', 'to'] as const;

type AuditFilterField = (typeof auditFilterFields)[number];
type AuditSearch = Partial<Record<AuditFilterField | 'cursor', string>>;

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

export const Route = createFileRoute('/_authenticated/audit')({
  validateSearch: (search): AuditSearch => ({
    actorEmail: optionalString(search.actorEmail),
    action: optionalString(search.action),
    targetKind: optionalString(search.targetKind),
    targetId: optionalString(search.targetId),
    from: optionalString(search.from),
    to: optionalString(search.to),
    cursor: optionalString(search.cursor),
  }),
  component: AuditRoute,
});

function AuditRoute() {
  const navigate = useNavigate();
  const search = Route.useSearch();
  const filters: AuditFilters = { ...search, limit: 50 };
  const auditPage = useAuditPage(filters);
  const [formValues, setFormValues] = useState<Record<AuditFilterField, string>>({
    actorEmail: search.actorEmail ?? '',
    action: search.action ?? '',
    targetKind: search.targetKind ?? '',
    targetId: search.targetId ?? '',
    from: search.from ?? '',
    to: search.to ?? '',
  });

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Audit & access</p>
          <h1 className="text-xl font-semibold text-ink">Audit log</h1>
        </div>
        <Button
          type="button"
          variant="secondary"
          onClick={() => {
            window.location.assign(auditCsvUrl(filters));
          }}
        >
          <DownloadIcon className="size-4" />
          Export CSV
        </Button>
      </header>
      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Filter rows by actor, action, target, and date range.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4 lg:grid-cols-6"
            onSubmit={(event) => {
              event.preventDefault();
              void navigate({
                to: '/audit',
                search: Object.fromEntries(
                  Object.entries(formValues).filter(([, fieldValue]) => fieldValue !== ''),
                ),
              });
            }}
          >
            {auditFilterFields.map((fieldName) => (
              <div key={fieldName} className="space-y-2">
                <Label htmlFor={fieldName}>{fieldName}</Label>
                <Input
                  id={fieldName}
                  value={formValues[fieldName]}
                  onChange={(event) =>
                    setFormValues((currentFormValues) => ({
                      ...currentFormValues,
                      [fieldName]: event.target.value,
                    }))
                  }
                />
              </div>
            ))}
            <div className="flex items-end">
              <Button type="submit">Apply filters</Button>
            </div>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Events</CardTitle>
          <CardDescription>{auditPage.isLoading ? 'Loading audit rows.' : `${auditPage.data?.rows.length ?? 0} rows`}</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Actor</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Target</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Created</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(auditPage.data?.rows ?? []).map((auditRow) => (
                <TableRow key={auditRow.auditId}>
                  <TableCell>{auditRow.actorEmail}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{auditRow.action}</Badge>
                  </TableCell>
                  <TableCell className="font-mono text-xs">
                    {auditRow.targetKind ?? 'system'}:{auditRow.targetId?.slice(0, 8) ?? '-'}
                  </TableCell>
                  <TableCell>{auditRow.reason ?? '-'}</TableCell>
                  <TableCell className="font-mono text-xs">{auditRow.createdAt}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className="mt-6">
            <JsonDiffViewer before={{ state: 'previous' }} after={{ state: 'current' }} />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
