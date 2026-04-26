import * as React from 'react';

import { Card, CardContent } from '@/components/ui/card';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * Inline AlertCircle SVG — kept in-file to avoid lucide-react's separate
 * React module instance under vitest (its useContext call hits a null
 * dispatcher, mirrors the LanguageSwitcher precedent + vitest.config.ts
 * deviation note). Any caller wanting a different icon passes their own
 * pre-rendered node via the `icon` prop.
 */
function DefaultEmptyIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-12"
      aria-hidden="true"
      {...props}
    >
      <circle cx="12" cy="12" r="10" />
      <line x1="12" x2="12" y1="8" y2="12" />
      <line x1="12" x2="12.01" y1="16" y2="16" />
    </svg>
  );
}

interface EmptyStateProps {
  /**
   * Pre-rendered icon node. Defaults to an inline AlertCircle-shaped SVG.
   * Accepts any ReactNode to allow custom illustrations later (Phase 5).
   */
  icon?: React.ReactNode;
  /** Pre-resolved title string (caller already translated). */
  title: string;
  /** Pre-resolved body string (caller already translated). */
  body: string;
  /** Optional navigational CTA. Renders as `<a className={buttonVariants()}>`. */
  cta?: { label: string; href: string };
  className?: string;
}

/**
 * EmptyState — centered icon + title + body + optional CTA.
 *
 * Composes shadcn Card with a dashed border to read as a "subdued" surface
 * suitable for 404 pages and "no items yet" placeholders. CTA pattern
 * reaffirmed Phase 1.3 P05 / UI-SPEC §Interaction Contracts:
 * `<a className={buttonVariants()}>` — NEVER `<Button asChild>` (the local
 * Button wraps `@base-ui/react/button` which doesn't support asChild).
 *
 * Note on `<a>` vs `next/link`: vitest's React-dedupe configuration cannot
 * cross the `next/link` boundary (next ships its own React copy → null hooks
 * dispatcher in jsdom; same root cause as the LanguageSwitcher inline-SVG
 * deviation). EmptyState is used in 404 / "no items yet" contexts where
 * full-page navigation is acceptable — a plain anchor preserves the locked
 * `className={buttonVariants()}` contract without breaking the test gate.
 *
 * RSC-compatible (no `'use client'`); CTA is a plain anchor, not an onClick.
 */
export function EmptyState({ icon, title, body, cta, className }: EmptyStateProps) {
  return (
    <Card data-slot="empty-state" className={cn('border-dashed', className)}>
      <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
        <div aria-hidden className="text-muted-foreground">
          {icon ?? <DefaultEmptyIcon />}
        </div>
        <div className="space-y-2">
          <h2 className="text-foreground text-3xl font-semibold tracking-tight">{title}</h2>
          <p className="text-muted-foreground">{body}</p>
        </div>
        {cta ? (
          <a href={cta.href} className={buttonVariants()}>
            {cta.label}
          </a>
        ) : null}
      </CardContent>
    </Card>
  );
}
