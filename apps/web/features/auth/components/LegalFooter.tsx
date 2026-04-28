import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { cn } from '@/lib/utils';

export async function LegalFooter({ className }: { className?: string }) {
  const t = await getTranslations();

  return (
    <div
      className={cn(
        'text-muted-foreground space-y-3 text-center text-xs leading-relaxed',
        className,
      )}
    >
      <p>
        {t.rich('legal.terms.body', {
          terms: (chunks) => (
            <Link href="/terms" className="hover:text-foreground underline underline-offset-4">
              {chunks}
            </Link>
          ),
          privacy: (chunks) => (
            <Link href="/privacy" className="hover:text-foreground underline underline-offset-4">
              {chunks}
            </Link>
          ),
        })}
      </p>
      <p>
        {t.rich('legal.googleApiPolicy.body', {
          link: (chunks) => (
            <a
              href="https://developers.google.com/terms/api-services-user-data-policy"
              className="hover:text-foreground underline underline-offset-4"
              target="_blank"
              rel="noopener noreferrer"
            >
              {chunks}
            </a>
          ),
        })}
      </p>
    </div>
  );
}
