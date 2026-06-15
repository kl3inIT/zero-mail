import { promises as fs } from 'node:fs';
import path from 'node:path';
import matter from 'gray-matter';
import type { Metadata } from 'next';
import Link from 'next/link';
import { getLocale, getTranslations } from 'next-intl/server';

import { Card, CardContent } from '@/components/ui/card';
import {
  DOCS_DIR,
  FILENAME_RE,
  FrontmatterSchema,
  listDocFilenames,
  type Frontmatter,
} from '@/lib/docs/loader';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return {
    title: t('docs.indexHeading'),
    description: t('docs.empty.body'),
    alternates: { canonical: '/docs' },
  };
}

/**
 * Docs index (Phase 1.3 Plan 06 — D-D3, D-D4).
 * Phase 01.5 Plan 02 — deflated from PageShell/EmptyState to raw <main>/Card (D-C1, D-C2).
 */
export default async function DocsIndexPage() {
  const [t, locale, entries] = await Promise.all([
    getTranslations(),
    getLocale(),
    listDocFilenames(),
  ]);

  const matchedEntries = entries.flatMap((name) => {
    const match = name.match(FILENAME_RE);
    return match && match[2] === locale ? [{ name, match }] : [];
  });

  const parsed = await Promise.all(
    matchedEntries.map(async ({ name }) => {
      const raw = await fs.readFile(path.join(DOCS_DIR, name), 'utf8');
      const { data } = matter(raw);
      return FrontmatterSchema.safeParse(data);
    }),
  );

  const docs: Frontmatter[] = parsed.flatMap((fm) => (fm.success ? [fm.data] : []));
  docs.sort((a, b) => a.order - b.order);

  return (
    <main className="mx-auto max-w-3xl px-4 py-8">
      <h1 className="mb-6 text-3xl font-semibold tracking-tight">{t('docs.indexHeading')}</h1>
      {docs.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
            <h2 className="text-foreground text-lg font-semibold">{t('docs.empty.heading')}</h2>
            <p className="text-muted-foreground text-sm">{t('docs.empty.body')}</p>
          </CardContent>
        </Card>
      ) : (
        <ul className="space-y-2">
          {docs.map((d) => (
            <li key={d.slug}>
              <Link href={`/docs/${d.slug}`} className="text-primary underline">
                {d.title}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
