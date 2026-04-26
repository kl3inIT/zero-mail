import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@/lib/utils';

/**
 * Container max-widths locked per Phase 01.4 UI-SPEC §Spacing.
 *
 *  - app       → max-w-3xl  (default; settings, onboarding, generic protected pages)
 *  - marketing → max-w-5xl  (landing skeleton)
 *  - docs      → max-w-3xl  (docs index + detail reading column)
 *  - auth      → max-w-md   (login; flex-centered viewport)
 *
 * Token-aware classes only — no raw color names.
 */
const pageShellVariants = cva('mx-auto', {
  variants: {
    variant: {
      app: 'max-w-3xl space-y-6 px-4 py-8',
      marketing: 'max-w-5xl px-4 py-12 lg:py-16',
      docs: 'max-w-3xl px-4 py-8',
      auth: 'flex min-h-dvh w-full max-w-md items-center justify-center p-6',
    },
  },
  defaultVariants: { variant: 'app' },
});

interface PageShellProps
  extends React.ComponentProps<'main'>, VariantProps<typeof pageShellVariants> {}

export function PageShell({ className, variant, children, ...props }: PageShellProps) {
  return (
    <main
      data-slot="page-shell"
      className={cn(pageShellVariants({ variant }), className)}
      {...props}
    >
      {children}
    </main>
  );
}
