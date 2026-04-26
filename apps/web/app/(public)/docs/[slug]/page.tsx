import { promises as fs } from 'node:fs';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { compileMDX } from 'next-mdx-remote/rsc';
import { getLocale, getTranslations } from 'next-intl/server';

import { FrontmatterSchema, SLUG_RE, buildDocPath } from '@/lib/docs/loader';

/**
 * Single doc page (Phase 1.3 Plan 06 — D-D1..D-D5, REVIEWS Revision 6).
 *
 * Next 16 contract: `params` is a Promise and MUST be awaited.
 *
 * Hardening:
 *  - Validate slug against SLUG_RE (/^[a-z0-9-]+$/) BEFORE any path join
 *    (T-1.3.06-01 path traversal).
 *  - Validate locale ∈ {'vi','en'} before invoking buildDocPath.
 *  - fs.readFile failure → notFound() (T-1.3.06-03 path-info disclosure).
 *  - compileMDX uses next-mdx-remote v6 defaults — blockJS / blockDangerousJS
 *    are intentionally NOT overridden (T-1.3.06-02 RSC-side JS execution).
 *  - REVIEWS Revision 6 — runtime zod safeParse on the compileMDX-returned
 *    frontmatter; failure → notFound() (T-1.3.06-04 trusting cast).
 *  - REVIEWS Revision 6 — slug+locale consistency: notFound() when
 *    fm.data.slug !== params.slug or fm.data.locale !== currentLocale
 *    (T-1.3.06-05 frontmatter lying about file identity).
 */
export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  if (!SLUG_RE.test(slug)) notFound();

  const locale = await getLocale();
  if (locale !== 'vi' && locale !== 'en') notFound();

  const filePath = buildDocPath(slug, locale);

  let source: string;
  try {
    source = await fs.readFile(filePath, 'utf8');
  } catch {
    notFound();
  }

  const t = await getTranslations();
  const { content, frontmatter } = await compileMDX({
    source: source!,
    options: { parseFrontmatter: true },
  });

  const fm = FrontmatterSchema.safeParse(frontmatter);
  if (!fm.success) notFound();

  if (fm.data.slug !== slug || fm.data.locale !== locale) notFound();

  return (
    <article className="prose mx-auto max-w-3xl px-4 py-8">
      <h1>{fm.data.title}</h1>
      {content}
      <p className="mt-8">
        <Link href="/docs" className="text-primary underline">
          {t('docs.backToList' as never)}
        </Link>
      </p>
    </article>
  );
}
