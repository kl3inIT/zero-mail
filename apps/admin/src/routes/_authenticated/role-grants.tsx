import { createFileRoute } from '@tanstack/react-router';
import { CopyIcon, UserPlusIcon } from 'lucide-react';
import { useReducer, useState } from 'react';

import { ConfirmTwiceDialog } from '@/components/ConfirmTwiceDialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useAdmins } from '@/features/role-grants/use-admins';
import { useGrantAdmin } from '@/features/role-grants/use-grant-admin';
import { useRevokeAdmin } from '@/features/role-grants/use-revoke-admin';

export const Route = createFileRoute('/_authenticated/role-grants')({
  component: RoleGrantsRoute,
});

// The grant dialog is a small workflow that opens, validates an email, fires the mutation, then
// reveals the one-time enrollment URL — its fields are reset together, so they live in one reducer.
type GrantDialogState = {
  open: boolean;
  email: string;
  emailError: string | null;
  enrollmentUrl: string | null;
};

type GrantDialogAction =
  | { type: 'opened' }
  | { type: 'closed' }
  | { type: 'emailChanged'; email: string }
  | { type: 'validationFailed'; message: string }
  | { type: 'granted'; enrollmentUrl: string };

const initialGrantDialogState: GrantDialogState = {
  open: false,
  email: '',
  emailError: null,
  enrollmentUrl: null,
};

function grantDialogReducer(
  state: GrantDialogState,
  action: GrantDialogAction,
): GrantDialogState {
  switch (action.type) {
    case 'opened':
      return { ...initialGrantDialogState, open: true };
    case 'closed':
      return initialGrantDialogState;
    case 'emailChanged':
      return { ...state, email: action.email };
    case 'validationFailed':
      return { ...state, emailError: action.message };
    case 'granted':
      return { ...state, emailError: null, enrollmentUrl: action.enrollmentUrl };
  }
}

function RoleGrantsRoute() {
  const admins = useAdmins();
  const grantAdmin = useGrantAdmin();
  const revokeAdmin = useRevokeAdmin();
  const [grantDialog, dispatchGrantDialog] = useReducer(
    grantDialogReducer,
    initialGrantDialogState,
  );
  const [revokeTarget, setRevokeTarget] = useState<{ id: string; email: string } | null>(null);

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Audit và truy cập</p>
          <h1 className="text-xl font-semibold text-ink">Phân quyền admin</h1>
        </div>
        <Button onClick={() => dispatchGrantDialog({ type: 'opened' })}>
          <UserPlusIcon className="size-4" />
          Cấp quyền admin
        </Button>
      </header>
      <Card>
        <CardHeader>
          <CardTitle>Quản trị viên</CardTitle>
          <CardDescription>Tài khoản vận hành xác thực bằng passkey.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Email</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead>Sử dụng gần nhất</TableHead>
                <TableHead>Thông tin xác thực</TableHead>
                <TableHead className="text-right">Hành động</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(admins.data ?? []).map((adminUser) => (
                <TableRow key={adminUser.adminUserId}>
                  <TableCell>{adminUser.email}</TableCell>
                  <TableCell>
                    <Badge variant={adminUser.status === 'ACTIVE' ? 'default' : 'secondary'}>
                      {adminUser.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{adminUser.lastUsedAt ?? '-'}</TableCell>
                  <TableCell>{adminUser.hasCredential ? 'Đã đăng ký' : 'Đang chờ'}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => setRevokeTarget({ id: adminUser.adminUserId, email: adminUser.email })}
                    >
                      Thu hồi quyền
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog
        open={grantDialog.open}
        onOpenChange={(open) => dispatchGrantDialog({ type: open ? 'opened' : 'closed' })}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cấp quyền admin</DialogTitle>
            <DialogDescription>Tạo URL đăng ký một lần cho quản trị viên khác.</DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(grantDialog.email)) {
                dispatchGrantDialog({
                  type: 'validationFailed',
                  message: 'Vui lòng nhập email quản trị viên hợp lệ.',
                });
                return;
              }
              void grantAdmin.mutateAsync(grantDialog.email).then((result) => {
                dispatchGrantDialog({ type: 'granted', enrollmentUrl: result.enrollmentUrl });
              });
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="grant-admin-email">Email quản trị viên</Label>
              <Input
                id="grant-admin-email"
                value={grantDialog.email}
                type="email"
                onChange={(event) =>
                  dispatchGrantDialog({ type: 'emailChanged', email: event.target.value })
                }
              />
              {grantDialog.emailError && (
                <p className="text-sm text-destructive">{grantDialog.emailError}</p>
              )}
            </div>
            <Button type="submit" disabled={grantAdmin.isPending}>
              Cấp quyền admin
            </Button>
          </form>
          {grantDialog.enrollmentUrl && (
            <div className="rounded-md border border-border bg-secondary p-3">
              <Label>URL đăng ký một lần</Label>
              <div className="mt-2 flex gap-2">
                <Input readOnly value={grantDialog.enrollmentUrl} className="font-mono text-xs" />
                <Button
                  variant="secondary"
                  type="button"
                  onClick={() =>
                    void navigator.clipboard.writeText(grantDialog.enrollmentUrl ?? '')
                  }
                >
                  <CopyIcon className="size-4" />
                  Sao chép
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>

      {revokeTarget && (
        <ConfirmTwiceDialog
          open={Boolean(revokeTarget)}
          onOpenChange={(open) => {
            if (!open) setRevokeTarget(null);
          }}
          actionLabel="Thu hồi quyền admin"
          targetLabel={revokeTarget.email}
          consequences={['Quản trị viên này sẽ không thể đăng nhập bằng passkey nữa.', 'Lý do sẽ được lưu vào nhật ký audit.']}
          confirmationToken={revokeTarget.email}
          finalButtonLabel="Thu hồi quyền"
          onConfirm={async (reason) => {
            await revokeAdmin.mutateAsync({ adminUserId: revokeTarget.id, reason });
            return { auditId: 'pending-refresh' };
          }}
        />
      )}
    </div>
  );
}
