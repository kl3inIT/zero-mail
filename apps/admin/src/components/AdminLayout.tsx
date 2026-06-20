import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, Outlet, useNavigate } from '@tanstack/react-router';
import {
  ActivityIcon,
  BookOpenIcon,
  Building2Icon,
  ClipboardListIcon,
  DollarSignIcon,
  GaugeIcon,
  GiftIcon,
  InboxIcon,
  KeyRoundIcon,
  Loader2Icon,
  LogOutIcon,
  PackageIcon,
  UsersIcon,
} from 'lucide-react';

import { logoutAdmin, type AdminMe } from '@/lib/admin-session';

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

type NavigationGroup = {
  title: string;
  items: NavigationItem[];
};

const navigationGroups: NavigationGroup[] = [
  {
    title: 'Quản lý',
    items: [
      { to: '/', label: 'Dashboard tổng quan', icon: GaugeIcon },
      { to: '/tenants', label: 'Khách hàng', icon: Building2Icon },
      { to: '/feedback', label: 'Phản hồi người dùng', icon: InboxIcon },
    ],
  },
  {
    title: 'Tài chính',
    items: [
      { to: '/billing-packages', label: 'Gói thanh toán', icon: PackageIcon },
      { to: '/spend', label: 'Chi phí', icon: DollarSignIcon },
    ],
  },
  {
    title: 'Hệ thống',
    items: [
      { to: '/queue', label: 'Hàng đợi', icon: ActivityIcon },
      { to: '/referrals', label: 'Sự kiện referral', icon: GiftIcon },
      { to: '/master-keys', label: 'Quản lý LLM', icon: KeyRoundIcon },
      { to: '/audit', label: 'Nhật ký audit', icon: ClipboardListIcon },
      { to: '/role-grants', label: 'Phân quyền admin', icon: UsersIcon },
    ],
  },
  {
    title: 'Công cụ',
    items: [{ to: '/rule-catalog', label: 'Ví dụ tạo quy tắc', icon: BookOpenIcon }],
  },
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
    <div className="bg-background text-foreground min-h-screen font-sans">
      {/* Mobile Navigation */}
      <div className="border-border bg-background border-b px-4 py-3 lg:hidden">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <img src="/images/logo.png" alt="Zero Mail" className="h-6 w-auto" />
            <div className="text-ink text-lg font-bold tracking-tight">Zero Mail</div>
          </div>
          <ThemeToggle />
        </div>
        <nav className="mt-4 flex [scrollbar-width:none] gap-2 overflow-x-auto pb-2 [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
          {navigationGroups
            .flatMap((g) => g.items)
            .map((navigationItem) => {
              const Icon = navigationItem.icon;
              const baseClass =
                'flex h-10 shrink-0 items-center gap-2 rounded-xl px-3 text-sm font-medium transition-colors';
              if (navigationItem.disabled) {
                return (
                  <span
                    key={navigationItem.to}
                    className={`${baseClass} text-muted-foreground opacity-50`}
                  >
                    <Icon className="size-[18px]" />
                    {navigationItem.label}
                  </span>
                );
              }
              return (
                <Link
                  key={navigationItem.to}
                  to={navigationItem.to}
                  className={`${baseClass} text-ink-2 bg-secondary/50`}
                  inactiveProps={{
                    className: 'hover:bg-secondary/80',
                  }}
                  activeProps={{
                    className: `${baseClass} bg-primary text-primary-foreground shadow-sm`,
                  }}
                >
                  <Icon className="size-[18px]" />
                  {navigationItem.label}
                </Link>
              );
            })}
        </nav>
      </div>

      <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[260px_1fr]">
        {/* Desktop Sidebar */}
        <aside className="border-border bg-sidebar sticky top-0 z-10 hidden h-screen border-r p-6 lg:flex lg:flex-col">
          <div className="mb-8 flex items-center justify-between px-2">
            <div className="flex items-center gap-3">
              <img src="/images/logo.png" alt="Zero Mail" className="h-8 w-auto" />
              <div className="text-ink text-xl font-bold tracking-tight">Zero Mail</div>
            </div>
            <ThemeToggle />
          </div>

          <div className="flex-1 [scrollbar-width:none] space-y-8 overflow-y-auto pr-2 [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
            {navigationGroups.map((group) => (
              <div key={group.title}>
                <h3 className="text-muted-foreground mb-3 px-3 text-xs font-semibold tracking-widest uppercase">
                  {group.title}
                </h3>
                <nav className="space-y-1.5">
                  {group.items.map((item) => {
                    const Icon = item.icon;
                    const baseClass =
                      'flex h-[42px] items-center gap-3 rounded-[14px] px-3.5 text-sm font-medium transition-all duration-200';
                    if (item.disabled) {
                      return (
                        <span
                          key={item.to}
                          className={`${baseClass} text-muted-foreground opacity-50`}
                        >
                          <Icon className="size-[18px]" />
                          {item.label}
                        </span>
                      );
                    }
                    return (
                      <Link
                        key={item.to}
                        to={item.to}
                        className={`${baseClass} text-ink-2`}
                        inactiveProps={{
                          className: 'hover:bg-secondary/80',
                        }}
                        activeProps={{
                          className:
                            'bg-primary text-primary-foreground shadow-md shadow-primary/20',
                        }}
                      >
                        <Icon className="size-[18px]" />
                        {item.label}
                      </Link>
                    );
                  })}
                </nav>
              </div>
            ))}
          </div>

          <div className="border-border/40 mt-8 flex flex-col gap-4 border-t pt-6">
            <Button
              variant="ghost"
              className="text-destructive hover:bg-destructive/10 hover:text-destructive flex h-[42px] w-full items-center justify-start gap-3 rounded-[14px] px-3.5 text-sm font-medium transition-colors"
              disabled={logoutMutation.isPending}
              onClick={() => logoutMutation.mutate()}
            >
              {logoutMutation.isPending ? (
                <Loader2Icon className="size-[18px] animate-spin" />
              ) : (
                <LogOutIcon className="size-[18px]" />
              )}
              Đăng xuất
            </Button>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="bg-background flex min-w-0 flex-col">
          <div className="w-full flex-1 px-4 pt-4 pb-8 sm:px-6 lg:px-8 lg:pt-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
