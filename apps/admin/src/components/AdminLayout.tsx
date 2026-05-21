import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, Outlet, useNavigate } from '@tanstack/react-router';
import {
  ActivityIcon,
  Building2Icon,
  ClipboardListIcon,
  DollarSignIcon,
  GaugeIcon,
  KeyRoundIcon,
  Loader2Icon,
  LogOutIcon,
  UsersIcon,
} from 'lucide-react';

import { logoutAdmin, type AdminMe } from '@/lib/admin-session';

import { AdminBreadcrumb } from './AdminBreadcrumb';
import { ThemeToggle } from './ThemeToggle';
import { Button } from './ui/button';

type AdminLayoutProps = {
  admin: AdminMe;
};


type NavigationItem = {
  to: string;
  label: string;
  icon: typeof GaugeIcon;
  disabled?: boolean;
};

const navigationItems: ReadonlyArray<NavigationItem> = [
  { to: '/', label: 'Bảng điều khiển', icon: GaugeIcon },
  { to: '/audit', label: 'Nhật ký audit', icon: ClipboardListIcon },
  { to: '/role-grants', label: 'Phân quyền admin', icon: UsersIcon },
  { to: '/master-keys', label: 'Quản lý LLM', icon: KeyRoundIcon },
  { to: '/tenants', label: 'Khách hàng', icon: Building2Icon },
  { to: '/queue', label: 'Hàng đợi', icon: ActivityIcon },
  { to: '/spend', label: 'Chi phí', icon: DollarSignIcon },
];

export function AdminLayout({ admin: _admin }: AdminLayoutProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logoutMutation = useMutation({
    mutationFn: logoutAdmin,
    onSuccess: async () => {
      queryClient.clear();
      await navigate({ to: '/login' });
    },
    meta: {
      successMessage: 'Đã đăng xuất.',
      errorMessage: 'Không thể đăng xuất. Vui lòng thử lại.',
    },
  });

  return (
    <div className="bg-background text-foreground min-h-screen">
      <div className="grid min-h-screen grid-cols-[240px_1fr]">
        <aside className="border-border bg-secondary border-r px-4 py-4">
          <div className="border-border mb-4 flex items-center gap-2 border-b pb-4">
            <div className="bg-ink text-background grid size-8 place-items-center rounded-md text-sm font-semibold">
              Z
            </div>
            <div>
              <div className="text-ink font-semibold">Zero Mail</div>
              <div className="text-muted-foreground font-mono text-[10px] tracking-wider uppercase">
                admin
              </div>
            </div>
          </div>
          <nav className="space-y-1">
            {navigationItems.map((navigationItem) => {
              const Icon = navigationItem.icon;
              const className =
                'flex h-9 items-center gap-2 rounded-md px-2 text-sm font-medium text-ink-2 hover:bg-card';
              if (navigationItem.disabled) {
                return (
                  <span key={navigationItem.to} className={`${className} opacity-45`}>
                    <Icon className="size-4" />
                    {navigationItem.label}
                  </span>
                );
              }
              return (
                <Link
                  key={navigationItem.to}
                  to={navigationItem.to}
                  className={className}
                  activeProps={{
                    className:
                      'flex h-9 items-center gap-2 rounded-md border-l-2 border-primary bg-violet-soft px-2 text-sm font-semibold text-primary',
                  }}
                >
                  <Icon className="size-4" />
                  {navigationItem.label}
                </Link>
              );
            })}
          </nav>
          <Button
            variant="ghost"
            className="text-ink-2 mt-8 w-full justify-start"
            disabled={logoutMutation.isPending}
            onClick={() => logoutMutation.mutate()}
          >
            {logoutMutation.isPending ? (
              <Loader2Icon className="size-4 animate-spin" />
            ) : (
              <LogOutIcon className="size-4" />
            )}
            Đăng xuất
          </Button>
        </aside>
        <main className="min-w-0">
          <div className="border-border flex items-center justify-between gap-3 border-b px-8 py-3">
            <AdminBreadcrumb />
            <ThemeToggle />
          </div>
          <div className="max-w-[1280px] px-8 py-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
