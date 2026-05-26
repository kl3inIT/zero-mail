'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { Menu } from 'lucide-react';

import { Toaster } from '@/components/ui/sonner';
import { SidebarInset, SidebarProvider, useSidebar } from '@/components/ui/sidebar';
import { TooltipProvider } from '@/components/ui/tooltip';
import { PauseBanner } from '@/features/triage/components/PauseBanner';
import { AppSidebar } from './AppSidebar';

function MobileSidebarTrigger() {
  const t = useTranslations();
  const { isMobile, openMobile, setOpenMobile } = useSidebar();
  if (!isMobile || openMobile) return null;

  return (
    <button
      type="button"
      onClick={() => setOpenMobile(true)}
      className="bg-card text-foreground hover:bg-accent border-border absolute top-3 left-3 z-30 flex h-9 w-9 items-center justify-center rounded-full border shadow-sm transition-colors"
      aria-label={t('shell.sidebar.toggle')}
      data-testid="mobile-sidebar-trigger"
    >
      <Menu className="size-4" aria-hidden="true" />
    </button>
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
            <PauseBanner />
            <MobileSidebarTrigger />
            <div
              className="border-border bg-card flex flex-1 flex-col overflow-hidden rounded-xl border"
              data-testid="app-shell-content"
            >
              <div className="flex-1 overflow-auto">{children}</div>
            </div>
          </SidebarInset>
        </div>
      </SidebarProvider>
      <Toaster />
    </TooltipProvider>
  );
}
