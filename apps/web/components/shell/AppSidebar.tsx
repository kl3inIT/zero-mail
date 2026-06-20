'use client';

import type { CSSProperties } from 'react';
import type { Route } from 'next';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import {
  BarChart3,
  Blocks,
  Bot,
  Check,
  ChevronsUpDown,
  CreditCard,
  Crown,
  Gift,
  HelpCircle,
  Inbox,
  ListChecks,
  LogOut,
  MailX,
  Moon,
  Plus,
  PanelLeft,
  RefreshCw,
  Reply,
  Settings,
  Sparkles,
  Sun,
} from 'lucide-react';

import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
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
  SidebarFooter,
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
import { useBillingPlans } from '@/features/billing/hooks/useBillingPlans';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import {
  getConnectMailboxUrl,
  getReconnectMailboxUrl,
  type MailboxSummary,
} from '@/features/mailbox/api/mailbox-api';
import { useActiveMailbox } from '@/features/mailbox/hooks/useActiveMailbox';
import { useMailboxList } from '@/features/mailbox/hooks/useMailboxList';
import { useSetActiveMailbox } from '@/features/mailbox/hooks/useSetActiveMailbox';
import { cn } from '@/lib/utils';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';

type NavItem = {
  href: Route;
  labelKey:
    | 'nav.chat'
    | 'nav.inbox'
    | 'nav.needsReply'
    | 'nav.ai'
    | 'nav.analytics'
    | 'nav.rules'
    | 'nav.cleanupUnsubscribe'
    | 'nav.integrations'
    | 'nav.referrals'
    | 'nav.support';
  icon: typeof Inbox;
};

type AccountNavItem = {
  href: Route;
  labelKey: 'nav.settings' | 'nav.billing' | 'nav.upgradePlan';
  icon: typeof Settings;
};

const DAILY_NAV: NavItem[] = [
  { href: '/chat', labelKey: 'nav.chat', icon: Sparkles },
  { href: '/inbox' as Route, labelKey: 'nav.inbox', icon: Inbox },
  { href: '/needs-reply' as Route, labelKey: 'nav.needsReply', icon: Reply },
];

const AUTOMATION_NAV: NavItem[] = [
  { href: '/rules', labelKey: 'nav.rules', icon: ListChecks },
  { href: '/ai' as Route, labelKey: 'nav.ai', icon: Bot },
];

const TOOLS_NAV: NavItem[] = [
  { href: '/cleanup/bulk-unsubscribe' as Route, labelKey: 'nav.cleanupUnsubscribe', icon: MailX },
  { href: '/analytics', labelKey: 'nav.analytics', icon: BarChart3 },
  { href: '/referrals' as Route, labelKey: 'nav.referrals', icon: Gift },
  { href: '/integrations' as Route, labelKey: 'nav.integrations', icon: Blocks },
  { href: '/support' as Route, labelKey: 'nav.support', icon: HelpCircle },
];

const ACCOUNT_NAV: AccountNavItem[] = [
  { href: '/settings' as Route, labelKey: 'nav.settings', icon: Settings },
  { href: '/credits' as Route, labelKey: 'nav.billing', icon: CreditCard },
  { href: '/upgrade-plan' as Route, labelKey: 'nav.upgradePlan', icon: Crown },
];

function isActivePath(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

function planLabel(planCode: string): string {
  switch (planCode) {
    case 'FREE':
      return 'Free';
    case 'PLUS':
      return 'Plus';
    case 'PRO':
      return 'Pro';
    default:
      return planCode;
  }
}

function mailboxDisplayName(mailbox: MailboxSummary): string {
  return (
    mailbox.profileDisplayName?.trim() || mailbox.displayPurpose?.trim() || mailbox.googleEmail
  );
}

// Gmail-style account avatars: a circular tinted initial per address. Colors are drawn from the
// chart-* design tokens (deterministic per email so they stay stable across renders) — never
// hardcoded hex, so palette pivots stay in globals.css.
const MAILBOX_AVATAR_COLORS = [
  'bg-chart-1',
  'bg-chart-2',
  'bg-chart-3',
  'bg-chart-4',
  'bg-chart-5',
] as const;

function mailboxAvatarColor(email: string): string {
  let hash = 0;
  for (let index = 0; index < email.length; index += 1) {
    hash = (hash * 31 + email.charCodeAt(index)) >>> 0;
  }
  return MAILBOX_AVATAR_COLORS[hash % MAILBOX_AVATAR_COLORS.length];
}

function mailboxInitial(mailbox: MailboxSummary): string {
  return (mailboxDisplayName(mailbox) || mailbox.googleEmail).charAt(0).toUpperCase() || '?';
}

function mailboxStatusLabel(status: string): string {
  return status
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

function isDisconnectedMailbox(mailbox: MailboxSummary): boolean {
  return mailbox.status.toUpperCase() === 'DISCONNECTED';
}

function AccountMenu({ collapsed }: { collapsed: boolean }) {
  const t = useTranslations();
  const router = useRouter();
  const currentUser = useCurrentUser();
  const billingPlans = useBillingPlans();
  const mailboxList = useMailboxList();
  const activeMailbox = useActiveMailbox();
  const setActiveMailbox = useSetActiveMailbox();
  const logout = useLogout();
  const signedInEmail = currentUser.data?.email ?? '';
  // Prefer the real Google profile name (read transiently from the OAuth session,
  // never persisted); fall back to the email local-part, then a generic label.
  const signedInDisplayName =
    currentUser.data?.displayName?.trim() ||
    (signedInEmail ? signedInEmail.split('@')[0] : t('shell.userMenu.label'));
  const avatarUrl = currentUser.data?.avatarUrl ?? undefined;
  const currentPlanLabel = billingPlans.data?.currentPlanCode
    ? planLabel(billingPlans.data.currentPlanCode)
    : t('shell.userMenu.planLoading');
  const mailboxes = mailboxList.data ?? [];
  const activeMailboxData = activeMailbox.data;
  const activeMailboxId = activeMailboxData?.gmailConnectionId;
  // Gmail-style: the current account sits at the TOP and the list below holds only the OTHER
  // accounts you can switch to — never the one you are already on.
  const otherMailboxes = mailboxes.filter(
    (mailbox) => mailbox.gmailConnectionId !== activeMailboxId,
  );
  // The dropdown header reflects the mailbox you are CURRENTLY using (active-mailbox endpoint),
  // not the login identity. Falls back to the signed-in user only while it is still loading.
  const activeAccountTitle = activeMailboxData
    ? activeMailboxData.profileDisplayName?.trim() ||
      activeMailboxData.displayPurpose?.trim() ||
      activeMailboxData.email
    : signedInDisplayName;
  const activeAccountEmail = activeMailboxData?.email ?? (signedInEmail || null);
  const activeAccountSubtitle =
    activeAccountEmail && activeAccountEmail !== activeAccountTitle ? activeAccountEmail : null;
  const activeAccountInitial =
    (activeAccountTitle || activeAccountEmail || '?').charAt(0).toUpperCase() || '?';
  const activeAccountAvatarUrl = activeMailboxData
    ? (activeMailboxData.profilePictureUrl ?? undefined)
    : avatarUrl;
  const activeAccountAvatarColor = activeMailboxData
    ? mailboxAvatarColor(activeMailboxData.email)
    : 'bg-primary';
  const triggerClassName = cn(
    'group/account hover:bg-sidebar-accent/60 focus-visible:ring-ring flex w-full items-center gap-3 rounded-xl text-left transition-colors focus-visible:ring-2 focus-visible:outline-none',
    collapsed ? 'mx-auto size-10 justify-center p-0' : 'px-2 py-2',
  );

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button
            type="button"
            className={triggerClassName}
            aria-label={t('shell.userMenu.label')}
            data-testid="sidebar-footer-account"
          />
        }
      >
        <Avatar className={collapsed ? 'size-9' : 'size-10'}>
          {activeAccountAvatarUrl ? (
            <AvatarImage src={activeAccountAvatarUrl} alt="" referrerPolicy="no-referrer" />
          ) : null}
          <AvatarFallback
            className={cn(activeAccountAvatarColor, 'text-primary-foreground font-semibold')}
          >
            {activeAccountInitial}
          </AvatarFallback>
        </Avatar>
        {!collapsed && (
          <>
            <div className="min-w-0 flex-1">
              <p className="text-sidebar-foreground truncate text-sm font-semibold">
                {activeAccountTitle}
              </p>
              <p className="text-sidebar-foreground/60 truncate text-xs">{currentPlanLabel}</p>
            </div>
            <ChevronsUpDown
              className="text-sidebar-foreground/45 group-hover/account:text-sidebar-foreground size-4 shrink-0 transition-colors"
              aria-hidden="true"
            />
          </>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent side={collapsed ? 'right' : 'top'} align="start" className="w-64">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="font-normal" data-testid="active-mailbox-header">
            <div className="flex items-center gap-3">
              <Avatar className="ring-primary ring-offset-popover size-10 ring-2 ring-offset-2">
                {activeAccountAvatarUrl ? (
                  <AvatarImage src={activeAccountAvatarUrl} alt="" referrerPolicy="no-referrer" />
                ) : null}
                <AvatarFallback
                  className={cn(
                    activeAccountAvatarColor,
                    'text-primary-foreground text-sm font-semibold',
                  )}
                >
                  {activeAccountInitial}
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <p className="min-w-0 truncate text-sm font-semibold">{activeAccountTitle}</p>
                  <Check
                    className="text-primary size-3.5 shrink-0"
                    aria-label={t('shell.accounts.active')}
                    data-testid="mailbox-active-marker"
                  />
                  {activeMailboxData?.isPrimary && (
                    <span className="bg-primary/10 text-primary shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium">
                      {t('shell.accounts.primary')}
                    </span>
                  )}
                </div>
                {activeAccountSubtitle && (
                  <p className="text-muted-foreground truncate text-xs">{activeAccountSubtitle}</p>
                )}
                <p className="text-muted-foreground truncate text-xs">{currentPlanLabel}</p>
              </div>
            </div>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuLabel className="text-muted-foreground px-2 py-1.5 text-xs font-semibold">
            {t('shell.accounts.title')}
          </DropdownMenuLabel>
          {otherMailboxes.map((mailbox) => (
            <DropdownMenuItem
              key={mailbox.gmailConnectionId}
              disabled={setActiveMailbox.isPending}
              data-testid={`mailbox-switch-${mailbox.gmailConnectionId}`}
              onClick={() => setActiveMailbox.mutate(mailbox.gmailConnectionId)}
              className="items-center gap-3 py-2"
            >
              <Avatar className="size-9">
                {mailbox.profilePictureUrl ? (
                  <AvatarImage
                    src={mailbox.profilePictureUrl}
                    alt=""
                    referrerPolicy="no-referrer"
                  />
                ) : null}
                <AvatarFallback
                  className={cn(
                    mailboxAvatarColor(mailbox.googleEmail),
                    'text-primary-foreground text-sm font-semibold',
                  )}
                >
                  {mailboxInitial(mailbox)}
                </AvatarFallback>
              </Avatar>
              <span className="min-w-0 flex-1 space-y-0.5">
                <span className="flex items-center gap-1.5">
                  <span className="truncate text-sm font-medium">
                    {mailboxDisplayName(mailbox)}
                  </span>
                  {mailbox.isPrimary && (
                    <span className="bg-primary/10 text-primary shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium">
                      {t('shell.accounts.primary')}
                    </span>
                  )}
                </span>
                <span className="text-muted-foreground block truncate text-xs">
                  {mailbox.googleEmail}
                </span>
                <span className="text-muted-foreground flex flex-wrap items-center gap-1 text-[11px] leading-4">
                  <span className="bg-muted text-muted-foreground rounded px-1.5 py-0.5 font-medium">
                    {mailboxStatusLabel(mailbox.status)}
                  </span>
                  <span>{t('shell.accounts.switch')}</span>
                </span>
              </span>
            </DropdownMenuItem>
          ))}
          <DropdownMenuItem
            data-testid="mailbox-add-gmail"
            onClick={() => {
              window.location.href = getConnectMailboxUrl();
            }}
            className="gap-2 p-2"
          >
            <span className="border-border bg-background flex size-6 shrink-0 items-center justify-center rounded-md border">
              <Plus className="size-4" aria-hidden="true" />
            </span>
            <span className="text-muted-foreground font-medium">
              {t('shell.accounts.addGmail')}
            </span>
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          {ACCOUNT_NAV.map((accountNavItem) => {
            const Icon = accountNavItem.icon;
            return (
              <DropdownMenuItem
                key={accountNavItem.href}
                onClick={() => router.push(accountNavItem.href)}
              >
                <Icon className="size-4" aria-hidden="true" />
                {t(accountNavItem.labelKey)}
              </DropdownMenuItem>
            );
          })}
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
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
  const mailboxList = useMailboxList();
  const activeMailbox = useActiveMailbox();
  const status = (statusQuery.data?.connectionStatus ?? 'PENDING') as GmailConnectionStatus;
  const disconnectedMailbox =
    mailboxList.data?.find(
      (mailbox) =>
        mailbox.gmailConnectionId === activeMailbox.data?.gmailConnectionId &&
        isDisconnectedMailbox(mailbox),
    ) ?? mailboxList.data?.find(isDisconnectedMailbox);
  if (status !== 'DISCONNECTED' || collapsed) return null;

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="border-destructive/40 text-destructive hover:bg-destructive/10 mx-3 h-8 justify-start rounded-lg text-xs"
        onClick={() => {
          window.location.href = disconnectedMailbox
            ? getReconnectMailboxUrl(disconnectedMailbox.gmailConnectionId)
            : getConnectMailboxUrl();
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

function SidebarThemeToggle({ collapsed }: { collapsed: boolean }) {
  const t = useTranslations();
  const buttonClassName = cn(
    'text-sidebar-foreground/60 hover:text-sidebar-foreground hover:bg-sidebar-accent flex size-8 items-center justify-center rounded-md transition-colors',
    collapsed && 'mx-auto',
  );

  return (
    <>
      <form action="/actions/theme" method="post" className="dark:hidden">
        <input type="hidden" name="theme" value="dark" />
        <button
          type="submit"
          className={buttonClassName}
          aria-label={t('nav.themeToDark')}
          title={t('nav.themeToDark')}
        >
          <Moon className="size-4" aria-hidden="true" />
        </button>
      </form>
      <form action="/actions/theme" method="post" className="hidden dark:block">
        <input type="hidden" name="theme" value="light" />
        <button
          type="submit"
          className={buttonClassName}
          aria-label={t('nav.themeToLight')}
          title={t('nav.themeToLight')}
        >
          <Sun className="size-4" aria-hidden="true" />
        </button>
      </form>
    </>
  );
}

export function AppSidebar() {
  const pathname = usePathname();
  const t = useTranslations();
  const { state, toggleSidebar } = useSidebar();
  const isCollapsed = state === 'collapsed';

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
      style={{ '--sidebar-width': '224px', '--sidebar-width-icon': '56px' } as CSSProperties}
    >
      <SidebarHeader className={cn('gap-3', isCollapsed ? 'px-2 py-3' : 'px-3 py-4')}>
        <div
          className={cn(
            'flex items-center gap-2',
            isCollapsed ? 'justify-center' : 'justify-between',
          )}
        >
          {!isCollapsed && (
            <span className="text-sidebar-foreground pl-2 text-lg font-bold tracking-normal">
              ZERO MAIL
            </span>
          )}
          <div className={cn('flex gap-1', isCollapsed ? 'flex-col items-center' : 'items-center')}>
            <SidebarThemeToggle collapsed={isCollapsed} />
            <button
              type="button"
              onClick={toggleSidebar}
              className="text-sidebar-foreground/60 hover:text-sidebar-foreground hover:bg-sidebar-accent flex size-8 items-center justify-center rounded-md transition-colors"
              aria-label={t('shell.sidebar.toggle')}
              data-testid="sidebar-collapse-toggle"
            >
              <PanelLeft className="size-4" aria-hidden="true" />
            </button>
          </div>
        </div>
      </SidebarHeader>

      <SidebarContent className="gap-3 py-1">
        <ReconnectRow collapsed={isCollapsed} />

        <SidebarGroup className="p-0">
          {!isCollapsed && (
            <SidebarGroupLabel className="text-sidebar-foreground/50 mb-1 px-5 text-[11px] font-semibold tracking-normal uppercase">
              {t('nav.sectionDaily')}
            </SidebarGroupLabel>
          )}
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">{DAILY_NAV.map(renderNavItem)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup className="p-0">
          {!isCollapsed && (
            <SidebarGroupLabel className="text-sidebar-foreground/50 mb-1 px-5 text-[11px] font-semibold tracking-normal uppercase">
              {t('nav.sectionAutomation')}
            </SidebarGroupLabel>
          )}
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">{AUTOMATION_NAV.map(renderNavItem)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup className="p-0">
          {!isCollapsed && (
            <SidebarGroupLabel className="text-sidebar-foreground/50 mb-1 px-5 text-[11px] font-semibold tracking-normal uppercase">
              {t('nav.sectionTools')}
            </SidebarGroupLabel>
          )}
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">{TOOLS_NAV.map(renderNavItem)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className={cn('border-sidebar-border/50 border-t', isCollapsed && 'px-2')}>
        <AccountMenu collapsed={isCollapsed} />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
