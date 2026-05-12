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
      <SidebarProvider defaultOpen={defaultSidebarOpen} data-testid="app-shell">
        <AppSidebar />
        <SidebarInset className="bg-background min-w-0">
          <ChromeHeader />
          <PauseBanner />
          <div className="min-w-0 flex-1" data-testid="app-shell-content">
            {children}
          </div>
        </SidebarInset>
        <Toaster />
      </SidebarProvider>
    </TooltipProvider>
  );
}
