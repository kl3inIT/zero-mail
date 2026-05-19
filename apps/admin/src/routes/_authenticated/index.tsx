import { createFileRoute, Link } from '@tanstack/react-router';
import { ClipboardListIcon, UsersIcon } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useAuditPage } from '@/features/audit/use-audit-page';

export const Route = createFileRoute('/_authenticated/')({
  component: DashboardRoute,
});

function DashboardRoute() {
  const auditPage = useAuditPage({ limit: 10 });
  const recentRows = auditPage.data?.rows ?? [];

  return (
    <div className="space-y-8">
      <header>
        <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Overview</p>
        <h1 className="text-xl font-semibold text-ink">Admin dashboard</h1>
      </header>
      <section className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Audit rows</CardDescription>
            <CardTitle className="tabular-nums text-3xl">{auditPage.data?.totalEstimate ?? 0}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Admin sessions</CardDescription>
            <CardTitle className="tabular-nums text-3xl">1</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Queue health</CardDescription>
            <CardTitle className="text-3xl">Ready</CardTitle>
          </CardHeader>
        </Card>
      </section>
      <section className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <Card>
          <CardHeader>
            <CardTitle>Recent audit events</CardTitle>
            <CardDescription>Latest admin mutations and read events.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Actor</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Target</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {recentRows.map((auditRow) => (
                  <TableRow key={auditRow.auditId}>
                    <TableCell>{auditRow.actorEmail}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{auditRow.action}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs">
                      {auditRow.targetKind ?? 'system'}:{auditRow.targetId?.slice(0, 8) ?? '-'}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{auditRow.createdAt}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Quick links</CardTitle>
            <CardDescription>Common operator checks.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2">
            <Link to="/audit" className="flex items-center gap-2 rounded-md border border-border p-3 text-sm hover:bg-secondary">
              <ClipboardListIcon className="size-4 text-primary" />
              Audit log
            </Link>
            <Link
              to="/role-grants"
              className="flex items-center gap-2 rounded-md border border-border p-3 text-sm hover:bg-secondary"
            >
              <UsersIcon className="size-4 text-primary" />
              Role grants
            </Link>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
