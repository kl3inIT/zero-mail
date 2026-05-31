import { promises as fs } from 'node:fs';
import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { compileMDX } from 'next-mdx-remote/rsc';
import { getLocale, getTranslations } from 'next-intl/server';

import { FrontmatterSchema, SLUG_RE, buildDocPath } from '@/lib/docs/loader';

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  if (!SLUG_RE.test(slug)) return {};

  const locale = await getLocale();
  if (locale !== 'vi' && locale !== 'en') return {};

  try {
    const source = await fs.readFile(buildDocPath(slug, locale), 'utf8');
    const { frontmatter } = await compileMDX({ source, options: { parseFrontmatter: true } });
    const fm = FrontmatterSchema.safeParse(frontmatter);
    return fm.success ? { title: fm.data.title } : {};
  } catch {
    return {};
  }
}

/**
 * Single doc page (Phase 1.3 Plan 06 — D-D1..D-D5, REVIEWS Revision 6).
 * Phase 01.5 Plan 02 — deflated from PageShell to raw <main> (D-C1, D-C2).
 *
 * Next 16 contract: `params` is a Promise and MUST be awaited.
 *
 * Hardening:
 *  - Validate slug against SLUG_RE (/^[a-z0-9-]+$/) BEFORE any path join
 *  - Validate locale ∈ {'vi','en'} before invoking buildDocPath
 *  - fs.readFile failure → notFound()
 *  - compileMDX uses next-mdx-remote v6 defaults
 *  - Runtime zod safeParse on compileMDX-returned frontmatter; failure → notFound()
 *  - slug+locale consistency: notFound() when fm.data.slug !== params.slug
 */
export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  if (!SLUG_RE.test(slug)) notFound();

  const locale = await getLocale();
  if (locale !== 'vi' && locale !== 'en') notFound();

  const filePath = buildDocPath(slug, locale);

  let source: string | null = null;
  try {
    source = await fs.readFile(filePath, 'utf8');
  } catch {
    source = null;
  }
  if (source === null) notFound();

  const [t, compiled] = await Promise.all([
    getTranslations(),
    compileMDX({
      source,
      options: { parseFrontmatter: true },
    }),
  ]);
  const { content, frontmatter } = compiled;

  const fm = FrontmatterSchema.safeParse(frontmatter);
  if (!fm.success) notFound();

  if (fm.data.slug !== slug || fm.data.locale !== locale) notFound();

  return (
    <main className="mx-auto max-w-3xl px-4 py-8">
      <article className="prose">
        <h1>{fm.data.title}</h1>
        {content}
        <p className="mt-8">
          <Link href="/docs" className="text-primary underline">
            {t('docs.backToList')}
          </Link>
        </p>
      </article>
    </main>
  );
}
