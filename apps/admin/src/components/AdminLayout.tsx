import { Link, Outlet } from '@tanstack/react-router';
import {
  ActivityIcon,
  BookOpenIcon,
  Building2Icon,
  ClipboardListIcon,
  GaugeIcon,
  KeyRoundIcon,
  LogOutIcon,
  UsersIcon,
} from 'lucide-react';

import type { AdminMe } from '@/lib/admin-session';

import { AdminModeBanner } from './AdminModeBanner';
import { Button } from './ui/button';

type AdminLayoutProps = {
  admin: AdminMe;
};

const navigationItems = [
  { to: '/', label: 'Dashboard', icon: GaugeIcon },
  { to: '/audit', label: 'Audit log', icon: ClipboardListIcon },
  { to: '/role-grants', label: 'Role grants', icon: UsersIcon },
  { to: '/master-keys', label: 'Master keys', icon: KeyRoundIcon },
  { to: '/tenants', label: 'Tenants', icon: Building2Icon },
  { to: '/catalog', label: 'Catalog', icon: BookOpenIcon },
  { to: '/queue', label: 'Queue', icon: ActivityIcon },
] as const;

export function AdminLayout({ admin }: AdminLayoutProps) {
  return (
    <div className="bg-background text-foreground min-h-screen">
      <AdminModeBanner email={admin.email} env={admin.env} />
      <div className="grid min-h-[calc(100vh-40px)] grid-cols-[240px_1fr]">
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
          <Button variant="ghost" className="text-ink-2 mt-8 w-full justify-start">
            <LogOutIcon className="size-4" />
            Sign out
          </Button>
        </aside>
        <main className="min-w-0">
          <div className="mx-auto max-w-[1280px] px-8 py-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
