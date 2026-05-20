import { Outlet, createFileRoute, useNavigate } from '@tanstack/react-router';
import { KeyRoundIcon } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { LlmProvider, MasterKeyFeature, MasterKeyRow } from '@/features/master-keys/master-keys-api';
import { useMasterKeys } from '@/features/master-keys/use-master-keys';
import { useSetFeatureDefault } from '@/features/master-keys/use-set-feature-default';

export const Route = createFileRoute('/_authenticated/master-keys')({
  component: MasterKeysRoute,
});

const features: Array<{ id: MasterKeyFeature; label: string; field: keyof MasterKeyRow }> = [
  { id: 'CHAT', label: 'Chat', field: 'featureDefaultProviderChat' },
  { id: 'TRIAGE', label: 'Phân loại', field: 'featureDefaultProviderTriage' },
  { id: 'DRAFT', label: 'Soạn nháp', field: 'featureDefaultProviderDraft' },
];

function MasterKeysRoute() {
  const navigate = useNavigate();
  const masterKeys = useMasterKeys();
  const setFeatureDefault = useSetFeatureDefault();
  const rows = masterKeys.data?.rows ?? [];

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Vận hành LLM</p>
          <h1 className="text-xl font-semibold text-ink">Khóa nền tảng</h1>
        </div>
      </header>
      <Outlet />
      <Card>
        <CardHeader>
          <CardTitle>Khóa nhà cung cấp</CardTitle>
          <CardDescription>{masterKeys.isLoading ? 'Đang tải nhà cung cấp.' : `${rows.length} nhà cung cấp`}</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nhà cung cấp</TableHead>
                <TableHead>Khóa</TableHead>
                <TableHead>Định dạng</TableHead>
                <TableHead>Phụ thuộc</TableHead>
                <TableHead>Lần xoay gần nhất</TableHead>
                <TableHead>Trạng thái</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow
                  key={row.provider}
                  className="cursor-pointer"
                  onClick={() => void navigate({ to: '/master-keys/$provider', params: { provider: row.provider } })}
                >
                  <TableCell className="font-medium">
                    <span className="inline-flex items-center gap-2">
                      <KeyRoundIcon className="size-4 text-muted-foreground" />
                      {row.displayName}
                    </span>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{row.maskedKey ?? 'Chưa thiết lập'}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{row.keyFormat ?? 'Chưa đặt'}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{row.dependentsCount}</Badge>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{row.lastRotatedAt ?? '-'}</TableCell>
                  <TableCell>
                    {row.rotationRecommended ? (
                      <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">Nên xoay khóa</Badge>
                    ) : (
                      <Badge variant={row.maskedKey ? 'default' : 'secondary'}>{row.maskedKey ? 'Đã đặt' : 'Chưa đặt'}</Badge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Mặc định theo tính năng</CardTitle>
          <CardDescription>Cấu hình định tuyến tạm thời cho 8B trong khi chờ bảng feature-default của 8D.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          {features.map((feature) => (
            <div key={feature.id} className="space-y-2">
              <Label>{feature.label}</Label>
              <Select
                value={(rows.find((row) => Boolean(row[feature.field]))?.provider ?? 'OPENROUTER') as LlmProvider}
                onValueChange={(provider) =>
                  setFeatureDefault.mutate({
                    feature: feature.id,
                    provider: provider as LlmProvider,
                    reason: `Đặt nhà cung cấp mặc định cho ${feature.label}`,
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {rows.map((row) => (
                    <SelectItem key={row.provider} value={row.provider}>
                      {row.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
