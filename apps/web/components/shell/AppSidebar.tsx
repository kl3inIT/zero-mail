'use client';

import type { Route } from 'next';
import Link from 'next/link';
import { useRouter, usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import {
  BarChart3,
  Bot,
  ChevronsUpDown,
  CircleDotDashed,
  CreditCard,
  Inbox,
  ListChecks,
  LogOut,
  MailX,
  PanelLeft,
  RefreshCw,
  Settings,
  Sparkles,
} from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  SidebarSeparator,
  useSidebar,
} from '@/components/ui/sidebar';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useLogout } from '@/features/account/hooks/useLogout';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import { shouldShowBetaOnboarding } from '@/features/onboarding/config';
import { getApiUrl } from '@/lib/api/base-url';
import { cn } from '@/lib/utils';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';

type NavItem = {
  href: Route;
  labelKey:
    | 'nav.chat'
    | 'nav.inbox'
    | 'nav.ai'
    | 'nav.analytics'
    | 'nav.rules'
    | 'nav.billing'
    | 'nav.settings'
    | 'nav.onboardingProgress'
    | 'nav.cleanupUnsubscribe';
  icon: typeof Inbox;
};

const CORE_NAV: NavItem[] = [
  { href: '/inbox' as Route, labelKey: 'nav.inbox', icon: Inbox },
  { href: '/chat', labelKey: 'nav.chat', icon: Sparkles },
];

const MANAGE_NAV: NavItem[] = [
  { href: '/rules', labelKey: 'nav.rules', icon: ListChecks },
  { href: '/cleanup/unsubscribe-campaign', labelKey: 'nav.cleanupUnsubscribe', icon: MailX },
  { href: '/analytics', labelKey: 'nav.analytics', icon: BarChart3 },
  { href: '/ai', labelKey: 'nav.ai', icon: Bot },
  { href: '/billing', labelKey: 'nav.billing', icon: CreditCard },
  { href: '/settings', labelKey: 'nav.settings', icon: Settings },
];

function isActivePath(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

function UserBlock({ collapsed }: { collapsed: boolean }) {
  const t = useTranslations();
  const router = useRouter();
  const currentUser = useCurrentUser();
  const logout = useLogout();
  const email =
    currentUser.data?.gmailConnectionStatus?.googleEmail ?? currentUser.data?.email ?? '';
  const displayName = email ? email.split('@')[0] : t('shell.userMenu.label');
  const initial = email.charAt(0).toUpperCase();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button
            type="button"
            className={cn(
              'hover:bg-sidebar-accent/60 focus-visible:ring-ring group/userblock flex items-center gap-3 rounded-xl transition-colors focus-visible:ring-2 focus-visible:outline-none',
              collapsed ? 'mx-auto h-9 w-9 justify-center p-0' : 'min-w-0 flex-1 px-2 py-1',
            )}
            aria-label={t('shell.userMenu.label')}
            data-testid="user-menu-trigger"
          />
        }
      >
        <div
          className={cn(
            'bg-primary text-primary-foreground flex shrink-0 items-center justify-center rounded-full font-semibold',
            collapsed ? 'size-9 text-sm' : 'size-10 text-base',
          )}
        >
          {initial || '?'}
        </div>
        {!collapsed && (
          <>
            <div className="flex min-w-0 flex-1 flex-col text-left">
              <p className="text-sidebar-foreground truncate text-sm font-semibold capitalize">
                {displayName}
              </p>
              <p className="text-sidebar-foreground/60 truncate text-xs">{email}</p>
            </div>
            <ChevronsUpDown
              className="text-sidebar-foreground/40 group-hover/userblock:text-sidebar-foreground size-4 shrink-0 transition-colors"
              aria-hidden="true"
            />
          </>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent side={collapsed ? 'right' : 'bottom'} align="start" className="w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="font-normal">
            <div className="flex flex-col gap-0.5">
              <span className="text-xs font-medium">{t('shell.userMenu.label')}</span>
              {email && <span className="text-muted-foreground truncate text-xs">{email}</span>}
            </div>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem onClick={() => router.push('/settings')}>
            <Settings className="size-4" aria-hidden="true" />
            {t('shell.userMenu.settings')}
          </DropdownMenuItem>
          <DropdownMenuItem
            variant="destructive"
            disabled={logout.isPending}
            onClick={() => logout.mutate()}
          >
            <LogOut className="size-4" aria-hidden="true" />
            {t('shell.userMenu.signOut')}
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function ReconnectRow({ collapsed }: { collapsed: boolean }) {
  const t = useTranslations();
  const statusQuery = useTenantStatus();
  const status = (statusQuery.data?.connectionStatus ?? 'PENDING') as GmailConnectionStatus;
  if (status !== 'DISCONNECTED') return null;
  if (collapsed) return null;

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="border-destructive/40 text-destructive hover:bg-destructive/10 mx-3 h-8 justify-start rounded-lg text-xs"
        onClick={() => {
          window.location.href = getApiUrl('/api/tenant/connect-gmail');
        }}
        data-testid="reconnect-gmail-button"
      >
        <RefreshCw className="size-3.5" aria-hidden="true" />
        <span className="truncate">{t('shell.connection.reconnect')}</span>
      </Button>
      <SidebarSeparator className="bg-sidebar-border/50 mx-3 my-1" />
    </>
  );
}

export function AppSidebar() {
  const pathname = usePathname();
  const t = useTranslations();
  const currentUser = useCurrentUser();
  const { state, toggleSidebar } = useSidebar();
  const isCollapsed = state === 'collapsed';
  const onboardingStep = currentUser.data?.onboardingStep;
  const showOnboarding = shouldShowBetaOnboarding(onboardingStep);

  const manageItems: NavItem[] = showOnboarding
    ? [
        ...MANAGE_NAV,
        {
          href: '/onboarding',
          labelKey: 'nav.onboardingProgress',
          icon: CircleDotDashed,
        } satisfies NavItem,
      ]
    : MANAGE_NAV;

  function renderNavItem(item: NavItem) {
    const Icon = item.icon;
    const label = t(item.labelKey);
    const active = isActivePath(pathname, item.href);

    return (
      <SidebarMenuItem key={item.href}>
        <SidebarMenuButton
          tooltip={label}
          className={cn(
            'flex h-9 items-center gap-3 rounded-lg font-medium transition-colors',
            isCollapsed ? 'mx-auto h-9 w-9 justify-center p-0' : 'mx-2 w-[calc(100%-1rem)] px-3',
            active
              ? 'bg-sidebar-accent! text-sidebar-accent-foreground! font-semibold'
              : 'text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground',
          )}
          render={<Link href={item.href} aria-label={label} />}
        >
          <Icon className="size-4 shrink-0" aria-hidden="true" />
          <span className="truncate text-sm group-data-[collapsible=icon]:hidden">{label}</span>
        </SidebarMenuButton>
      </SidebarMenuItem>
    );
  }

  return (
    <Sidebar
      collapsible="icon"
      data-testid="app-sidebar"
      className="border-sidebar-border border-r"
      style={{ '--sidebar-width': '224px', '--sidebar-width-icon': '56px' } as React.CSSProperties}
    >
      <SidebarHeader
        className={cn(
          'border-sidebar-border/50 flex flex-row items-center gap-2 border-b',
          isCollapsed ? 'flex-col gap-2 px-1 py-3' : 'justify-between px-3 py-3',
        )}
      >
        <UserBlock collapsed={isCollapsed} />
        <button
          type="button"
          onClick={toggleSidebar}
          className="text-sidebar-foreground/60 hover:text-sidebar-foreground hover:bg-sidebar-accent flex h-7 w-7 items-center justify-center rounded-md transition-colors"
          aria-label={t('shell.sidebar.toggle')}
          data-testid="sidebar-collapse-toggle"
        >
          <PanelLeft className="size-4" aria-hidden="true" />
        </button>
      </SidebarHeader>

      <SidebarContent className="gap-3 py-3">
        <ReconnectRow collapsed={isCollapsed} />

        <SidebarGroup className="p-0">
          {!isCollapsed && (
            <SidebarGroupLabel className="text-sidebar-foreground/50 mb-1 px-5 text-[11px] font-semibold tracking-wider uppercase">
              {t('nav.sectionMail')}
            </SidebarGroupLabel>
          )}
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">{CORE_NAV.map(renderNavItem)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup className="p-0">
          {!isCollapsed && (
            <SidebarGroupLabel className="text-sidebar-foreground/50 mb-1 px-5 text-[11px] font-semibold tracking-wider uppercase">
              {t('nav.sectionManage')}
            </SidebarGroupLabel>
          )}
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">{manageItems.map(renderNavItem)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarRail />
    </Sidebar>
  );
}
