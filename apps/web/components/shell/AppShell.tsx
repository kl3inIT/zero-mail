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
        <div className="bg-background flex h-screen w-screen flex-col overflow-hidden">
          <ChromeHeader />
          <div className="bg-sidebar flex flex-1 overflow-hidden">
            <AppSidebar />
            <SidebarInset className="bg-sidebar min-w-0 transition-all duration-300">
              <div className="flex h-full w-full flex-col p-2 pr-4 pb-4">
                <div className="bg-card flex flex-1 flex-col overflow-hidden rounded-2xl border">
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
