'use client';

import type { ReactNode } from 'react';

import { Toaster } from '@/components/ui/sonner';
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar';
import { TooltipProvider } from '@/components/ui/tooltip';
import { PauseBanner } from '@/features/triage/components/PauseBanner';
import { AppSidebar } from './AppSidebar';
import { ChromeHeader } from './ChromeHeader';

export function AppShell({
  children,
  defaultSidebarOpen,
}: {
  children: ReactNode;
  defaultSidebarOpen: boolean;
}) {
  return (
    <TooltipProvider>
      <SidebarProvider
        defaultOpen={defaultSidebarOpen}
        data-testid="app-shell"
        className="flex-col"
      >
        <div className="flex h-screen w-screen flex-col overflow-hidden bg-white">
          <ChromeHeader />
          <div className="flex flex-1 overflow-hidden bg-[#f6f8fc] dark:bg-zinc-950">
            <AppSidebar />
            <SidebarInset className="min-w-0 bg-[#f6f8fc] transition-all duration-300 dark:bg-zinc-950">
              <div className="flex h-full w-full flex-col p-2 pr-4 pb-4">
                <div className="flex flex-1 flex-col overflow-hidden rounded-2xl border border-[#f6f8fc] bg-white dark:bg-zinc-900">
                  <PauseBanner />
                  <div className="flex-1 overflow-auto p-6" data-testid="app-shell-content">
                    {children}
                  </div>
                </div>
              </div>
            </SidebarInset>
          </div>
        </div>
      </SidebarProvider>
      <Toaster />
    </TooltipProvider>
  );
}
