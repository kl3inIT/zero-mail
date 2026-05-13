'use client';

import { useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname, useRouter } from 'next/navigation';
import {
  CreditCard,
  Languages,
  LogOut,
  MailCheck,
  MailWarning,
  MailX,
  Menu,
  Pause,
  Play,
  RefreshCw,
  Settings,
} from 'lucide-react';
import { toast } from 'sonner';
import Link from 'next/link';

import ZMLogoMark from '@/features/landing/components/ZMLogoMark';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useSidebar } from '@/components/ui/sidebar';
import { Skeleton } from '@/components/ui/skeleton';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useUpdateLanguage } from '@/features/account/hooks/useUpdateLanguage';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';
import { useTenantStatus } from '@/features/gmail/hooks/useTenantStatus';
import { useTriagePauseState } from '@/features/triage/hooks/useTriagePauseState';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';
import type { AppLocale } from '@/i18n/routing';
import { getApiUrl } from '@/lib/api/base-url';
import { cn } from '@/lib/utils';

type PageTitleKey =
  | 'nav.triage'
  | 'nav.needsReply'
  | 'nav.rules'
  | 'nav.billing'
  | 'nav.settings'
  | 'nav.onboardingProgress'
  | 'nav.dashboard';
type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';

function pageTitleKey(pathname: string): PageTitleKey {
  if (pathname.startsWith('/needs-reply')) return 'nav.needsReply';
  if (pathname.startsWith('/rules')) return 'nav.rules';
  if (pathname.startsWith('/billing')) return 'nav.billing';
  if (pathname.startsWith('/settings')) return 'nav.settings';
  if (pathname.startsWith('/onboarding')) return 'nav.onboardingProgress';
  if (pathname.startsWith('/dashboard')) return 'nav.dashboard';
  return 'nav.triage';
}

function connectionPresentation(status: GmailConnectionStatus): {
  labelKey:
    | 'shell.connection.connected'
    | 'shell.connection.degraded'
    | 'shell.connection.disconnected';
  dotClassName: string;
  icon: typeof MailCheck;
} {
  if (status === 'CONNECTED') {
    return {
      labelKey: 'shell.connection.connected',
      dotClassName: 'bg-green ring-green-soft',
      icon: MailCheck,
    };
  }
  if (status === 'DISCONNECTED') {
    return {
      labelKey: 'shell.connection.disconnected',
      dotClassName: 'bg-red ring-red-soft',
      icon: MailX,
    };
  }
  return {
    labelKey: 'shell.connection.degraded',
    dotClassName: 'bg-amber ring-amber-soft',
    icon: MailWarning,
  };
}

function BalancePill() {
  const t = useTranslations();
  const locale = useLocale();
  const balance = useBillingBalance();
  const formattedBalance = useMemo(() => {
    const availableCredits = balance.data?.availableCredits ?? 0;
    return new Intl.NumberFormat(locale === 'vi' ? 'vi-VN' : 'en-US').format(availableCredits);
  }, [balance.data?.availableCredits, locale]);

  if (balance.isPending) {
    return <Skeleton className="h-8 w-24 rounded-full" data-testid="balance-pill-loading" />;
  }

  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <div
            className="bg-background flex h-9 cursor-default items-center gap-1.5 rounded-full border border-[#0a3d3a]/20 px-3"
            aria-label={`${t('shell.balance.label')}: ${formattedBalance}`}
            data-testid="balance-pill"
          />
        }
      >
        <CreditCard className="text-primary size-3.5 shrink-0" aria-hidden="true" />
        <span className="font-mono text-sm font-medium tabular-nums">{formattedBalance}</span>
      </TooltipTrigger>
      <TooltipContent>{t('shell.balance.label')}</TooltipContent>
    </Tooltip>
  );
}

function ConnectionHealth() {
  const t = useTranslations();
  const statusQuery = useTenantStatus();
  const status = (statusQuery.data?.connectionStatus ?? 'PENDING') as GmailConnectionStatus;
  const presentation = connectionPresentation(status);
  const Icon = presentation.icon;
  const label = t(presentation.labelKey);

  const iconColorClass =
    status === 'CONNECTED'
      ? 'text-green-500'
      : status === 'DISCONNECTED'
        ? 'text-red-500'
        : 'text-amber-500';

  return (
    <div className="flex items-center gap-1">
      <Tooltip>
        <TooltipTrigger
          render={
            <button
              type="button"
              className={cn(
                'hover:bg-accent flex h-9 items-center gap-1.5 rounded-full border border-[#0a3d3a]/20 px-3 transition-colors',
              )}
              aria-label={label}
              data-testid="connection-health-dot"
              data-status={status}
            />
          }
        >
          <Icon className={cn('size-[18px]', iconColorClass)} aria-hidden="true" />
        </TooltipTrigger>
        <TooltipContent>{label}</TooltipContent>
      </Tooltip>
      {status === 'DISCONNECTED' && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-8 rounded-full px-3 text-xs"
          onClick={() => {
            window.location.href = getApiUrl('/tenant/connect-gmail');
          }}
          aria-label={t('shell.connection.reconnect')}
          data-testid="reconnect-gmail-button"
        >
          <RefreshCw className="size-3" aria-hidden="true" />
          <span className="hidden sm:inline">{t('shell.connection.reconnect')}</span>
        </Button>
      )}
    </div>
  );
}

function PauseControl() {
  const t = useTranslations();
  const pauseState = useTriagePauseState();
  const togglePause = useToggleTriagePause();
  const [confirmPauseOpen, setConfirmPauseOpen] = useState(false);
  const paused = pauseState.data === true;
  const running = !paused;

  const persistPauseState = (nextPaused: boolean) => {
    togglePause.mutate(nextPaused, {
      onSuccess: () => {
        toast.success(t(nextPaused ? 'shell.pause.toast.paused' : 'shell.pause.toast.running'));
      },
    });
  };

  return (
    <>
      <button
        type="button"
        className={cn(
          'flex h-9 items-center gap-1.5 rounded-full border px-3 transition-colors',
          paused
            ? 'border-warning/40 text-warning hover:bg-warning/10'
            : 'text-muted-foreground hover:bg-muted hover:text-foreground border-[#0a3d3a]/20',
        )}
        aria-label={t('shell.pause.label')}
        disabled={pauseState.isLoading || togglePause.isPending}
        onClick={() => {
          if (running) {
            setConfirmPauseOpen(true);
          } else {
            persistPauseState(false);
          }
        }}
        data-testid="pause-switch"
      >
        {paused ? (
          <Pause className="size-3.5 shrink-0" aria-hidden="true" />
        ) : (
          <Play className="size-3.5 shrink-0" aria-hidden="true" />
        )}
        <span className="text-xs font-medium">
          {t(paused ? 'shell.pause.paused' : 'shell.pause.running')}
        </span>
      </button>
      <AlertDialog open={confirmPauseOpen} onOpenChange={setConfirmPauseOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('shell.pause.confirm.title')}</AlertDialogTitle>
            <AlertDialogDescription>{t('shell.pause.confirm.body')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('shell.pause.confirm.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              className="bg-warning text-warning-foreground hover:bg-warning/90"
              onClick={() => {
                setConfirmPauseOpen(false);
                persistPauseState(true);
              }}
            >
              {t('shell.pause.confirm.cta')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

function UserMenu() {
  const t = useTranslations();
  const router = useRouter();
  const locale = useLocale();
  const currentUser = useCurrentUser();
  const updateLanguage = useUpdateLanguage();
  const currentLocale = (
    currentUser.data?.preferredLanguage === 'en' || locale === 'en' ? 'en' : 'vi'
  ) as AppLocale;
  const email =
    currentUser.data?.gmailConnectionStatus?.googleEmail ?? currentUser.data?.email ?? '';
  const initial = email.charAt(0).toUpperCase();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button
            type="button"
            className="bg-primary text-primary-foreground hover:bg-primary/90 focus-visible:ring-ring flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold transition-colors focus-visible:ring-2 focus-visible:outline-none"
            aria-label={t('shell.userMenu.label')}
            data-testid="user-menu-trigger"
          />
        }
      >
        {initial || '?'}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
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
          <DropdownMenuLabel className="flex items-center gap-1.5">
            <Languages className="size-3.5" aria-hidden="true" />
            {t('shell.userMenu.language')}
          </DropdownMenuLabel>
          <DropdownMenuCheckboxItem
            checked={currentLocale === 'vi'}
            onClick={() => updateLanguage.mutate('vi')}
          >
            {t('shell.userMenu.vietnamese')}
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem
            checked={currentLocale === 'en'}
            onClick={() => updateLanguage.mutate('en')}
          >
            {t('shell.userMenu.english')}
          </DropdownMenuCheckboxItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem onClick={() => router.push('/settings')}>
            <Settings className="size-4" aria-hidden="true" />
            {t('shell.userMenu.settings')}
          </DropdownMenuItem>
          <DropdownMenuItem
            variant="destructive"
            onClick={() => {
              window.location.href = getApiUrl('/logout');
            }}
          >
            <LogOut className="size-4" aria-hidden="true" />
            {t('shell.userMenu.signOut')}
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function ChromeHeader() {
  const pathname = usePathname();
  const t = useTranslations();
  const { toggleSidebar, state } = useSidebar();
  const isCollapsed = state === 'collapsed';
  const title = t(pageTitleKey(pathname));

  return (
    <header
      className="bg-background flex h-12 shrink-0 items-center px-2"
      data-testid="chrome-header"
    >
      <div className="flex w-60 shrink-0 items-center gap-4 pr-4 pl-3">
        <button
          type="button"
          onClick={toggleSidebar}
          className="text-muted-foreground hover:text-foreground hover:bg-accent flex h-8 w-8 shrink-0 items-center justify-center rounded-full transition-colors"
          aria-label={t('shell.sidebar.toggle')}
        >
          <Menu className="size-5" />
        </button>
        <Link href="/triage" className="flex items-center gap-4 focus-visible:outline-none">
          <span className="bg-sidebar-primary text-sidebar-primary-foreground grid size-8 shrink-0 place-items-center rounded-full shadow-sm">
            <ZMLogoMark size={16} />
          </span>
          <span className="text-foreground text-xl font-bold tracking-tight whitespace-nowrap">
            Zero Mail
          </span>
        </Link>
      </div>

      <div className="flex min-w-0 flex-1 items-center px-4" />

      <div className="ml-auto flex shrink-0 items-center gap-3">
        <BalancePill />
        <div className="bg-border hidden h-5 w-px sm:block" aria-hidden="true" />
        <ConnectionHealth />
        <PauseControl />
        <div className="bg-border hidden h-5 w-px sm:block" aria-hidden="true" />
        <UserMenu />
      </div>
    </header>
  );
}
