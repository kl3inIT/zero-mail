import { promises as fs } from 'node:fs';
import path from 'node:path';
import matter from 'gray-matter';
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

/**
 * Docs index (Phase 1.3 Plan 06 — D-D3, D-D4).
 * Phase 01.5 Plan 02 — deflated from PageShell/EmptyState to raw <main>/Card (D-C1, D-C2).
 */
export default async function DocsIndexPage() {
  const t = await getTranslations();
  const locale = await getLocale();

  const entries = await listDocFilenames();
  const docs: Frontmatter[] = [];
  for (const name of entries) {
    const m = name.match(FILENAME_RE);
    if (!m) continue;
    if (m[2] !== locale) continue;
    const raw = await fs.readFile(path.join(DOCS_DIR, name), 'utf8');
    const { data } = matter(raw);
    const fm = FrontmatterSchema.safeParse(data);
    if (!fm.success) continue;
    docs.push(fm.data);
  }
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
