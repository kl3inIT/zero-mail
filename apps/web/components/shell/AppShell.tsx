'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { Menu } from 'lucide-react';

import { Toaster } from '@/components/ui/sonner';
import { SidebarInset, SidebarProvider, useSidebar } from '@/components/ui/sidebar';
import { TooltipProvider } from '@/components/ui/tooltip';
import { AppSidebar } from './AppSidebar';

function MobileTopBar() {
  const t = useTranslations();
  const { isMobile, setOpenMobile } = useSidebar();
  if (!isMobile) return null;

  return (
    <div
      className="border-border bg-card flex h-12 shrink-0 items-center gap-2.5 border-b px-4"
      data-testid="mobile-top-bar"
    >
      <button
        type="button"
        onClick={() => setOpenMobile(true)}
        className="text-foreground hover:bg-accent flex size-8 items-center justify-center rounded-md transition-colors"
        aria-label={t('shell.sidebar.toggle')}
        data-testid="mobile-sidebar-trigger"
      >
        <Menu className="size-5" aria-hidden="true" />
      </button>
      <span className="text-foreground text-base font-semibold tracking-tight">
        Zero<span className="text-muted-foreground font-normal">Mail</span>
      </span>
    </div>
  );
}

export function AppShell({
  children,
  defaultSidebarOpen,
}: {
  children: ReactNode;
  defaultSidebarOpen: boolean;
}) {
  return (
    <TooltipProvider>
      <SidebarProvider defaultOpen={defaultSidebarOpen} data-testid="app-shell">
        <div className="bg-sidebar flex h-screen w-screen overflow-hidden">
          <AppSidebar />
          <SidebarInset className="bg-sidebar relative min-w-0 p-1.5 transition-[margin] sm:p-2 md:peer-data-[state=expanded]:-ml-8">
            <div
              className="border-border bg-card flex flex-1 flex-col overflow-hidden rounded-xl border"
              data-testid="app-shell-content"
            >
              <MobileTopBar />
              <div className="flex-1 overflow-auto">{children}</div>
            </div>
          </SidebarInset>
        </div>
      </SidebarProvider>
      <Toaster />
    </TooltipProvider>
  );
}
