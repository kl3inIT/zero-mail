'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import {
  BarChart3,
  CircleDotDashed,
  CreditCard,
  Inbox,
  ListChecks,
  MailQuestion,
  Settings,
  Sparkles,
} from 'lucide-react';

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  SidebarSeparator,
  useSidebar,
} from '@/components/ui/sidebar';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useToReplyCount } from '@/features/needs-reply/hooks/useToReplyCount';
import { useHydrated } from '@/lib/use-hydrated';
import { cn } from '@/lib/utils';

type NavItem = {
  href: string;
  labelKey:
    | 'nav.chat'
    | 'nav.ai'
    | 'nav.analytics'
    | 'nav.needsReply'
    | 'nav.rules'
    | 'nav.billing'
    | 'nav.settings'
    | 'nav.onboardingProgress';
  icon: typeof Inbox;
  badge?: 'needs-reply';
};

const MAIL_NAV: NavItem[] = [
  { href: '/chat', labelKey: 'nav.chat', icon: Sparkles },
  { href: '/rules', labelKey: 'nav.rules', icon: ListChecks },
  { href: '/analytics', labelKey: 'nav.analytics', icon: BarChart3 },
  { href: '/needs-reply', labelKey: 'nav.needsReply', icon: MailQuestion, badge: 'needs-reply' },
];

const MANAGE_NAV: NavItem[] = [
  { href: '/ai', labelKey: 'nav.ai', icon: Sparkles },
  { href: '/billing', labelKey: 'nav.billing', icon: CreditCard },
  { href: '/settings', labelKey: 'nav.settings', icon: Settings },
];

function isActivePath(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppSidebar() {
  const pathname = usePathname();
  const t = useTranslations();
  const currentUser = useCurrentUser();
  const toReplyCount = useToReplyCount();
  const hydrated = useHydrated();
  const { state } = useSidebar();
  const isCollapsed = state === 'collapsed';
  const visibleToReplyCount = hydrated ? (toReplyCount.data ?? 0) : 0;
  const onboardingStep = currentUser.data?.onboardingStep;
  const showOnboarding = Boolean(onboardingStep && onboardingStep !== 'COMPLETE');

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

  const email =
    currentUser.data?.gmailConnectionStatus?.googleEmail ?? currentUser.data?.email ?? '';
  const initial = email.charAt(0).toUpperCase();

  function renderNavItem(item: NavItem) {
    const Icon = item.icon;
    const label = t(item.labelKey);
    const active = isActivePath(pathname, item.href);
    return (
      <SidebarMenuItem key={item.href}>
        <SidebarMenuButton
          tooltip={label}
          className={cn(
            'flex h-9 items-center gap-4 transition-all duration-300 ease-in-out',
            isCollapsed
              ? 'mx-auto h-9 w-9 justify-center rounded-full p-0'
              : 'w-full rounded-l-none rounded-r-full pr-4 pl-7',
            active
              ? 'bg-sidebar-accent! text-sidebar-accent-foreground! hover:bg-sidebar-accent/80! font-bold'
              : 'text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-foreground font-medium',
          )}
          render={<Link href={item.href} aria-label={label} />}
        >
          <Icon className="size-5 shrink-0" aria-hidden="true" />
          <span className="truncate text-sm group-data-[collapsible=icon]:hidden">{label}</span>
          {item.badge === 'needs-reply' && visibleToReplyCount > 0 && (
            <span
              aria-label={`${visibleToReplyCount}`}
              className="bg-primary/10 text-primary ml-auto flex h-[22px] min-w-[22px] items-center justify-center rounded-full px-1 font-mono text-xs font-semibold group-data-[collapsible=icon]:hidden"
            >
              {visibleToReplyCount}
            </span>
          )}
        </SidebarMenuButton>
      </SidebarMenuItem>
    );
  }

  return (
    <Sidebar
      collapsible="icon"
      data-testid="app-sidebar"
      className="!top-12 !h-[calc(100vh-48px)] border-none bg-transparent"
      style={{ '--sidebar-width': '240px', '--sidebar-width-icon': '72px' } as React.CSSProperties}
    >
      <SidebarContent className="flex flex-col gap-0 pt-2 pr-0 pl-0 transition-all duration-300 group-data-[collapsible=icon]:!p-0">
        <SidebarGroup className="p-0 py-1">
          <SidebarGroupLabel className="text-sidebar-foreground/50 mb-1 px-6 text-xs font-medium">
            {t('nav.sectionMail')}
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">{MAIL_NAV.map(renderNavItem)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup className="mt-auto p-0 py-1">
          <SidebarSeparator className="bg-sidebar-border/50 mx-3 mb-2" />
          <SidebarGroupLabel className="text-sidebar-foreground/50 mb-1 px-6 text-xs font-medium group-data-[collapsible=icon]:hidden">
            {t('nav.sectionManage')}
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">{manageItems.map(renderNavItem)}</SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarRail />

      <SidebarFooter className="border-none p-0 group-data-[collapsible=icon]:items-center">
        <div
          className={cn(
            'hover:bg-sidebar-accent group/user flex shrink-0 cursor-pointer items-center gap-2.5 rounded-full px-2 py-1.5 transition-all duration-300 ease-in-out',
            isCollapsed ? 'h-12 w-[72px] justify-center px-0' : 'mx-2 mb-2 w-full',
          )}
        >
          <div className="bg-primary text-primary-foreground flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold transition-transform duration-300 group-hover/user:scale-105">
            {initial || '?'}
          </div>
          {!isCollapsed && (
            <div className="min-w-0 flex-1">
              <p className="text-sidebar-foreground truncate text-xs leading-tight font-medium">
                {email || t('shell.userMenu.label')}
              </p>
            </div>
          )}
        </div>
      </SidebarFooter>
    </Sidebar>
  );
}
