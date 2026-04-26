import { promises as fs } from 'node:fs';
import path from 'node:path';
import matter from 'gray-matter';
import Link from 'next/link';
import { getLocale, getTranslations } from 'next-intl/server';

import {
  DOCS_DIR,
  FILENAME_RE,
  FrontmatterSchema,
  listDocFilenames,
  type Frontmatter,
} from '@/lib/docs/loader';

/**
 * Docs index (Phase 1.3 Plan 06 — D-D3, D-D4).
 *
 * Reads apps/web/content/docs/ via the deterministic resolver in
 * @/lib/docs/loader (REVIEWS Revision 6, OpenCode MEDIUM). Filename listing
 * flows through fs.readdir inside loader.listDocFilenames; gray-matter parses
 * the frontmatter; FrontmatterSchema.safeParse validates each entry and SKIPS
 * malformed files rather than crashing the whole listing. Filters by current
 * locale (D-D5 filename suffix) and sorts by `order`.
 *
 * Empty-state copy uses keys under the `docs` namespace (e.g. docs.indexHeading,
 * docs.empty.heading, docs.empty.body). Plan 07 lands these keys; until then
 * runtime falls back to the key path itself, matching the cast-to-never bypass
 * that Plan 05 already established.
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
    <section className="mx-auto max-w-3xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-semibold">{t('docs.indexHeading')}</h1>
      {docs.length === 0 ? (
        <div>
          <p className="text-base font-semibold">{t('docs.empty.heading')}</p>
          <p className="text-muted-foreground text-sm">{t('docs.empty.body')}</p>
        </div>
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
    </section>
  );
}
