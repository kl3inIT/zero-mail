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
            <SidebarInset className="bg-background min-w-0">
              <PauseBanner />
              <div className="flex-1 overflow-auto p-6" data-testid="app-shell-content">
                {children}
              </div>
            </SidebarInset>
          </div>
        </div>
      </SidebarProvider>
      <Toaster />
    </TooltipProvider>
  );
}
