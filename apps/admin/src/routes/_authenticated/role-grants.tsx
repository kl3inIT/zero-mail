import { createFileRoute } from '@tanstack/react-router';
import { CopyIcon, UserPlusIcon } from 'lucide-react';
import { useState } from 'react';

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

function RoleGrantsRoute() {
  const admins = useAdmins();
  const grantAdmin = useGrantAdmin();
  const revokeAdmin = useRevokeAdmin();
  const [grantDialogOpen, setGrantDialogOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [grantedEnrollmentUrl, setGrantedEnrollmentUrl] = useState<string | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<{ id: string; email: string } | null>(null);

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Audit & access</p>
          <h1 className="text-xl font-semibold text-ink">Role grants</h1>
        </div>
        <Button onClick={() => setGrantDialogOpen(true)}>
          <UserPlusIcon className="size-4" />
          Grant admin
        </Button>
      </header>
      <Card>
        <CardHeader>
          <CardTitle>Admins</CardTitle>
          <CardDescription>Passkey-backed operator accounts.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Email</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Last used</TableHead>
                <TableHead>Credential</TableHead>
                <TableHead className="text-right">Actions</TableHead>
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
                  <TableCell>{adminUser.hasCredential ? 'Registered' : 'Pending'}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => setRevokeTarget({ id: adminUser.adminUserId, email: adminUser.email })}
                    >
                      Revoke admin
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={grantDialogOpen} onOpenChange={setGrantDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Grant admin</DialogTitle>
            <DialogDescription>Creates a one-time enrollment URL for another operator.</DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              setEmailError(null);
              if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                setEmailError('Enter a valid admin email.');
                return;
              }
              void grantAdmin.mutateAsync(email).then((result) => {
                setGrantedEnrollmentUrl(result.enrollmentUrl);
              });
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="grant-admin-email">Admin email</Label>
              <Input
                id="grant-admin-email"
                value={email}
                type="email"
                onChange={(event) => setEmail(event.target.value)}
              />
              {emailError && <p className="text-sm text-destructive">{emailError}</p>}
            </div>
            <Button type="submit" disabled={grantAdmin.isPending}>
              Grant admin
            </Button>
          </form>
          {grantedEnrollmentUrl && (
            <div className="rounded-md border border-border bg-secondary p-3">
              <Label>One-time enrollment URL</Label>
              <div className="mt-2 flex gap-2">
                <Input readOnly value={grantedEnrollmentUrl} className="font-mono text-xs" />
                <Button
                  variant="secondary"
                  type="button"
                  onClick={() => void navigator.clipboard.writeText(grantedEnrollmentUrl)}
                >
                  <CopyIcon className="size-4" />
                  Copy URL
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
          actionLabel="Revoke admin grant"
          targetLabel={revokeTarget.email}
          consequences={['The admin cannot sign in with their passkey.', 'The reason is stored in the audit log.']}
          confirmationToken={revokeTarget.email}
          finalButtonLabel="Revoke admin"
          onConfirm={async (reason) => {
            await revokeAdmin.mutateAsync({ adminUserId: revokeTarget.id, reason });
            return { auditId: 'pending-refresh' };
          }}
        />
      )}
    </div>
  );
}
