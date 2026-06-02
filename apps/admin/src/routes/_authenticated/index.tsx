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
        <h1 className="text-xl font-semibold text-ink">Bảng điều khiển quản trị</h1>
      </header>
      <section className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Bản ghi audit</CardDescription>
            <CardTitle className="tabular-nums text-3xl">{auditPage.data?.totalEstimate ?? 0}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Phiên đăng nhập admin</CardDescription>
            <CardTitle className="tabular-nums text-3xl">1</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Tình trạng hàng đợi</CardDescription>
            <CardTitle className="text-3xl">Sẵn sàng</CardTitle>
          </CardHeader>
        </Card>
      </section>
      <section className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <Card>
          <CardHeader>
            <CardTitle>Sự kiện audit gần đây</CardTitle>
            <CardDescription>Các thao tác và lượt đọc mới nhất của quản trị viên.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Người thao tác</TableHead>
                  <TableHead>Hành động</TableHead>
                  <TableHead>Đối tượng</TableHead>
                  <TableHead>Thời điểm</TableHead>
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
            <CardTitle>Lối tắt</CardTitle>
            <CardDescription>Các kiểm tra vận hành thường dùng.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2">
            <Link to="/audit" className="flex items-center gap-2 rounded-md border border-border p-3 text-sm hover:bg-secondary">
              <ClipboardListIcon className="size-4 text-primary" />
              Nhật ký audit
            </Link>
            <Link
              to="/role-grants"
              className="flex items-center gap-2 rounded-md border border-border p-3 text-sm hover:bg-secondary"
            >
              <UsersIcon className="size-4 text-primary" />
              Phân quyền admin
            </Link>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
