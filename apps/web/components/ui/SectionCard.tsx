import * as React from 'react';

import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { cn } from '@/lib/utils';

interface SectionCardProps extends React.ComponentProps<'div'> {
  /**
   * Pre-resolved heading text (already translated by caller via
   * `getTranslations()` server-side or `useTranslations()` client-side).
   * Keeping this as a resolved string lets SectionCard stay RSC-compatible.
   */
  heading?: string;
  /** Pre-resolved description text. */
  description?: string;
  /** Optional footer content rendered inside the Card footer slot. */
  footer?: React.ReactNode;
}

/**
 * SectionCard — composes shadcn Card primitives (Card + CardHeader +
 * CardTitle + CardDescription + CardContent + CardFooter) with optional
 * heading / description / footer slots. Header region is omitted entirely
 * when both heading and description are absent.
 *
 * RSC-compatible by design (no `'use client'`); the primitive receives
 * already-resolved strings and never calls i18n hooks itself.
 */
export function SectionCard({
  heading,
  description,
  footer,
  className,
  children,
  ...props
}: SectionCardProps) {
  const showHeader = Boolean(heading || description);
  return (
    <Card data-slot="section-card" className={cn(className)} {...props}>
      {showHeader ? (
        <CardHeader>
          {heading ? <CardTitle>{heading}</CardTitle> : null}
          {description ? <CardDescription>{description}</CardDescription> : null}
        </CardHeader>
      ) : null}
      <CardContent>{children}</CardContent>
      {footer ? <CardFooter>{footer}</CardFooter> : null}
    </Card>
  );
}
