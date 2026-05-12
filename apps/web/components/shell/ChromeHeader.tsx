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
  Pause,
  Play,
  RefreshCw,
  Settings,
  UserCircle,
} from 'lucide-react';
import { toast } from 'sonner';

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
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { SidebarTrigger } from '@/components/ui/sidebar';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
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
  | 'nav.rules'
  | 'nav.billing'
  | 'nav.settings'
  | 'nav.onboardingProgress';
type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';

function pageTitleKey(pathname: string): PageTitleKey {
  if (pathname.startsWith('/rules')) return 'nav.rules';
  if (pathname.startsWith('/billing')) return 'nav.billing';
  if (pathname.startsWith('/settings')) return 'nav.settings';
  if (pathname.startsWith('/onboarding')) return 'nav.onboardingProgress';
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
    return <Skeleton className="h-9 w-20 rounded-md" data-testid="balance-pill-loading" />;
  }

  return (
    <Badge
      variant="outline"
      className="border-border bg-background h-9 gap-1.5 rounded-md px-2.5"
      aria-label={`${t('shell.balance.label')}: ${formattedBalance}`}
      data-testid="balance-pill"
    >
      <CreditCard className="text-primary size-3.5" aria-hidden="true" />
      <span className="hidden sm:inline">{t('shell.balance.label')}</span>
      <span className="font-mono tabular-nums">{formattedBalance}</span>
    </Badge>
  );
}

function ConnectionHealth() {
  const t = useTranslations();
  const statusQuery = useTenantStatus();
  const status = (statusQuery.data?.connectionStatus ?? 'PENDING') as GmailConnectionStatus;
  const presentation = connectionPresentation(status);
  const Icon = presentation.icon;
  const label = t(presentation.labelKey);

  return (
    <div className="flex items-center gap-1.5">
      <Tooltip>
        <TooltipTrigger
          render={
            <button
              type="button"
              className="border-border bg-background text-muted-foreground grid size-9 place-items-center rounded-md border"
              aria-label={label}
              data-testid="connection-health-dot"
              data-status={status}
            />
          }
        >
          <span
            className={cn('size-2.5 rounded-full ring-4', presentation.dotClassName)}
            aria-hidden="true"
          />
        </TooltipTrigger>
        <TooltipContent>{label}</TooltipContent>
      </Tooltip>
      {status === 'DISCONNECTED' && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-9 px-2"
          onClick={() => {
            window.location.href = getApiUrl('/tenant/connect-gmail');
          }}
          aria-label={t('shell.connection.reconnect')}
          data-testid="reconnect-gmail-button"
        >
          <RefreshCw className="size-3.5" aria-hidden="true" />
          <span className="hidden sm:inline">{t('shell.connection.reconnect')}</span>
        </Button>
      )}
      <Icon className="text-muted-foreground hidden size-4 lg:block" aria-hidden="true" />
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
    <div className="border-border bg-background flex min-h-10 items-center gap-2 rounded-md border px-2">
      {paused ? (
        <Pause className="text-warning size-4" aria-hidden="true" />
      ) : (
        <Play className="text-primary size-4" aria-hidden="true" />
      )}
      <div className="hidden min-w-0 lg:block">
        <div className="truncate text-xs font-medium">{t('shell.pause.label')}</div>
        <div className={cn('text-xs', paused ? 'text-warning' : 'text-muted-foreground')}>
          {t(paused ? 'shell.pause.paused' : 'shell.pause.running')}
        </div>
      </div>
      <Switch
        checked={running}
        aria-label={t('shell.pause.label')}
        disabled={pauseState.isLoading || togglePause.isPending}
        onCheckedChange={(nextRunning) => {
          if (nextRunning) {
            persistPauseState(false);
            return;
          }
          setConfirmPauseOpen(true);
        }}
        className="data-unchecked:bg-warning/80"
        data-testid="pause-switch"
      />
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
    </div>
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

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            type="button"
            variant="ghost"
            size="icon-lg"
            aria-label={t('shell.userMenu.label')}
            data-testid="user-menu-trigger"
          />
        }
      >
        <UserCircle className="size-5" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>{t('shell.userMenu.label')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
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
        <DropdownMenuSeparator />
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
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function ChromeHeader() {
  const pathname = usePathname();
  const t = useTranslations();
  const title = t(pageTitleKey(pathname));

  return (
    <header
      className="bg-background/95 sticky top-0 z-20 flex min-h-14 flex-wrap items-center gap-2 border-b px-3 py-2 backdrop-blur sm:flex-nowrap sm:px-4"
      data-testid="chrome-header"
    >
      <div className="flex min-w-0 flex-1 items-center gap-2">
        <SidebarTrigger className="size-10 sm:size-8" aria-label={t('shell.sidebar.toggle')} />
        <div className="hidden min-w-0 min-[420px]:block">
          <div className="text-foreground truncate text-sm font-semibold">{title}</div>
        </div>
      </div>
      <div className="ml-auto flex min-w-0 items-center gap-1.5 sm:gap-2">
        <BalancePill />
        <ConnectionHealth />
        <PauseControl />
        <UserMenu />
      </div>
    </header>
  );
}
