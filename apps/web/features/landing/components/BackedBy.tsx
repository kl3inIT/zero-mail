import { getTranslations } from 'next-intl/server';

import { cn } from '@/lib/utils';

const GENAI_FUND_URL = 'https://genaifund.ai';
const SOURCE_URL = 'https://github.com/kl3inIT/zero-mail';

/**
 * "Backed by GenAI Fund · Open source" line shown under the hero CTAs and in the footer.
 * The logo comes in a dark and a light variant (public/partners) and swaps with the theme.
 */
export async function BackedBy({
  stacked = false,
  className,
}: {
  /** One item per line with no divider, for narrow columns such as the footer. */
  stacked?: boolean;
  className?: string;
}) {
  const t = await getTranslations('backedBy');

  return (
    <div
      className={cn(
        'flex text-sm text-(--text-muted)',
        stacked ? 'flex-col items-start gap-3' : 'flex-wrap items-center gap-x-4 gap-y-3',
        className,
      )}
    >
      <a
        href={GENAI_FUND_URL}
        target="_blank"
        rel="noopener"
        className="inline-flex items-center gap-2.5 hover:text-(--ink)"
      >
        <span>{t('label')}</span>
        {/* eslint-disable-next-line @next/next/no-img-element -- Static partner logo. */}
        <img
          src="/partners/genaifund-logo.png"
          alt="GenAI Fund"
          width={1200}
          height={252}
          className="h-5 w-auto dark:hidden"
        />
        {/* eslint-disable-next-line @next/next/no-img-element -- Static partner logo. */}
        <img
          src="/partners/genaifund-logo-white.png"
          alt="GenAI Fund"
          width={1200}
          height={254}
          className="hidden h-5 w-auto dark:block"
        />
      </a>
      {stacked ? null : <span aria-hidden="true" className="h-4 w-px bg-(--line-strong)" />}
      <a
        href={SOURCE_URL}
        target="_blank"
        rel="noopener"
        aria-label={t('openSourceAria')}
        className="inline-flex items-center gap-2 hover:text-(--ink)"
      >
        <svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor" aria-hidden="true">
          <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
        </svg>
        <span>{t('openSource')}</span>
      </a>
    </div>
  );
}
