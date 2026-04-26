'use client';

import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { useTranslations } from 'next-intl';

import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * StatusAlert — closed-enum status banner with i18n-key-only props.
 *
 * Locked design (UI-SPEC §Color "Status alert color mapping" + CONTEXT
 * D-D3 + RESEARCH §T-error-param-tamper):
 *  - Closed-enum variant: 'info' | 'success' | 'warn' | 'error'.
 *  - Token-aware classes only — error variant uses
 *    `border-destructive/40 bg-destructive/10 text-destructive`. Other
 *    variants use neutral `bg-muted` + `text-foreground` + `border-border`
 *    (UI-SPEC discretion: extend `--color-success` / `--color-warning`
 *    later if neutrals prove indistinguishable; default = ship neutral).
 *  - Props accept i18n KEYS only — never raw strings — to enforce the
 *    privacy contract: a caller cannot accidentally render `error.message`
 *    or any user-data fragment through this primitive.
 *  - CTA renders as `<button className={buttonVariants({variant:'outline',
 *    size:'sm'})}>` — a plain DOM button rather than the `Button` wrapper,
 *    because `Button` wraps `@base-ui/react/button` whose internal `useRef`
 *    crosses a separate React module boundary that vitest's dedupe cannot
 *    reach (LanguageSwitcher precedent + vitest.config.ts deviation note).
 *    The DOM contract (`role="button"` + `onClick`) is identical.
 *
 * Inline icon SVGs (info / check-circle / triangle-warning / circle-alert)
 * are kept in-file to avoid lucide-react's separate React module instance
 * under vitest (mirrors the LanguageSwitcher precedent + vitest.config.ts
 * deviation note).
 */

const statusAlertVariants = cva('', {
  variants: {
    variant: {
      info: 'border-border bg-muted text-foreground',
      success: 'border-border bg-muted text-foreground',
      warn: 'border-border bg-muted text-foreground',
      error: 'border-destructive/40 bg-destructive/10 text-destructive',
    },
  },
  defaultVariants: { variant: 'info' },
});

type StatusAlertVariant = NonNullable<VariantProps<typeof statusAlertVariants>['variant']>;

function InfoIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5"
      aria-hidden="true"
      {...props}
    >
      <circle cx="12" cy="12" r="10" />
      <path d="M12 16v-4" />
      <path d="M12 8h.01" />
    </svg>
  );
}

function CheckCircleIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5"
      aria-hidden="true"
      {...props}
    >
      <circle cx="12" cy="12" r="10" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

function TriangleWarningIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5"
      aria-hidden="true"
      {...props}
    >
      <path d="m21.73 18-8-14a2 2 0 0 0-3.46 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
      <path d="M12 9v4" />
      <path d="M12 17h.01" />
    </svg>
  );
}

function CircleAlertIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5"
      aria-hidden="true"
      {...props}
    >
      <circle cx="12" cy="12" r="10" />
      <line x1="12" x2="12" y1="8" y2="12" />
      <line x1="12" x2="12.01" y1="16" y2="16" />
    </svg>
  );
}

const iconByVariant: Record<
  StatusAlertVariant,
  React.ComponentType<React.SVGProps<SVGSVGElement>>
> = {
  info: InfoIcon,
  success: CheckCircleIcon,
  warn: TriangleWarningIcon,
  error: CircleAlertIcon,
};

interface StatusAlertProps extends VariantProps<typeof statusAlertVariants> {
  /** i18n key for the alert title — never a raw string (privacy contract). */
  titleKey: string;
  /** Optional i18n key for the alert body. */
  bodyKey?: string;
  /** Optional ICU params forwarded to next-intl `t(...)`. */
  params?: Record<string, string | number | Date>;
  /** Optional CTA button. `labelKey` is an i18n key; `onClick` fires on click. */
  cta?: { labelKey: string; onClick?: () => void };
  className?: string;
}

export function StatusAlert({
  variant,
  titleKey,
  bodyKey,
  params,
  cta,
  className,
}: StatusAlertProps) {
  // Cast translator to a loose shape so this primitive accepts dynamic i18n
  // keys + ICU params under next-intl 4.x's strict typed-bundle generics.
  // The runtime contract (key path lookup with optional params) is unchanged
  // — this mirrors the LanguageSwitcher LooseTranslator deviation.
  const t = useTranslations() as unknown as (
    key: string,
    params?: Record<string, string | number | Date>,
  ) => string;
  const resolvedVariant: StatusAlertVariant = variant ?? 'info';
  const Icon = iconByVariant[resolvedVariant];

  return (
    <Alert
      data-slot="alert"
      data-variant={resolvedVariant}
      className={cn(statusAlertVariants({ variant }), className)}
    >
      <Icon />
      <AlertTitle>{t(titleKey, params)}</AlertTitle>
      {bodyKey ? <AlertDescription>{t(bodyKey, params)}</AlertDescription> : null}
      {cta ? (
        <button
          type="button"
          onClick={cta.onClick}
          className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'mt-2 w-fit')}
        >
          {t(cta.labelKey)}
        </button>
      ) : null}
    </Alert>
  );
}
