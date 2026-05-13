'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import {
  CircleDotDashed,
  CreditCard,
  Inbox,
  ListChecks,
  MailQuestion,
  Settings,
  ShieldCheck,
} from 'lucide-react';

import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useToReplyCount } from '@/features/needs-reply/hooks/useToReplyCount';
import { useHydrated } from '@/lib/use-hydrated';
import { cn } from '@/lib/utils';

type NavItem = {
  href: string;
  labelKey:
    | 'nav.triage'
    | 'nav.needsReply'
    | 'nav.rules'
    | 'nav.billing'
    | 'nav.settings'
    | 'nav.onboardingProgress';
  icon: typeof Inbox;
  badge?: 'needs-reply';
};

const APP_NAV_ITEMS: NavItem[] = [
  { href: '/triage', labelKey: 'nav.triage', icon: Inbox },
  { href: '/needs-reply', labelKey: 'nav.needsReply', icon: MailQuestion, badge: 'needs-reply' },
  { href: '/rules', labelKey: 'nav.rules', icon: ListChecks },
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
  const visibleToReplyCount = hydrated ? (toReplyCount.data ?? 0) : 0;
  const onboardingStep = currentUser.data?.onboardingStep;
  const showOnboarding = Boolean(onboardingStep && onboardingStep !== 'COMPLETE');
  const navItems = showOnboarding
    ? [
        ...APP_NAV_ITEMS,
        {
          href: '/onboarding',
          labelKey: 'nav.onboardingProgress',
          icon: CircleDotDashed,
        } satisfies NavItem,
      ]
    : APP_NAV_ITEMS;

  return (
    <Sidebar collapsible="icon" data-testid="app-sidebar">
      <SidebarHeader className="border-sidebar-border border-b">
        <Link
          href="/triage"
          aria-label={t('nav.logoLabel')}
          className="text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-sidebar-ring flex h-10 min-w-0 items-center gap-2 rounded-md px-2 outline-hidden focus-visible:ring-2"
        >
          <span className="bg-sidebar-primary text-sidebar-primary-foreground grid size-7 shrink-0 place-items-center rounded-md">
            <ShieldCheck className="size-4" aria-hidden="true" />
          </span>
          <span className="min-w-0 truncate text-sm font-semibold group-data-[collapsible=icon]:hidden">
            <span>zero</span>
            <span className="text-muted-foreground font-normal">mail</span>
          </span>
        </Link>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              {navItems.map((item) => {
                const Icon = item.icon;
                const label = t(item.labelKey);
                const active = isActivePath(pathname, item.href);
                return (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton
                      tooltip={label}
                      isActive={active}
                      className={cn(active && 'bg-sidebar-accent text-sidebar-accent-foreground')}
                      render={<Link href={item.href} aria-label={label} />}
                    >
                      <Icon className="size-4" aria-hidden="true" />
                      <span>{label}</span>
                    </SidebarMenuButton>
                    {item.badge === 'needs-reply' && visibleToReplyCount > 0 ? (
                      <SidebarMenuBadge
                        aria-label={`${label}: ${visibleToReplyCount}`}
                        className="bg-primary/10 text-primary font-mono"
                      >
                        {visibleToReplyCount}
                      </SidebarMenuBadge>
                    ) : null}
                  </SidebarMenuItem>
                );
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  );
}
