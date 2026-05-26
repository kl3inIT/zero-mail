import { existsSync, promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';

/**
 * Deterministic docs-dir resolver + zod frontmatter schema (Phase 1.3 Plan 06).
 *
 * REVIEWS Revision 6 (OpenCode MEDIUM):
 *  - Anchor on this module's file location via __dirname (CJS) or
 *    fileURLToPath(import.meta.url) (ESM, the Next 16 default). From this file
 *    (apps/web/lib/docs/loader.ts) the docs dir is at ../../docs
 *    (apps/web/docs). Resolution is independent of the caller's working
 *    directory, so deployments that change cwd (Cloud Run, Vercel) stay correct.
 *  - Replace `as Frontmatter` TS cast with runtime zod validation
 *    (FrontmatterSchema.safeParse). Fail-closed → caller invokes notFound().
 *
 * This module is server-only (uses fs/path); never import from a client component.
 */
const HERE =
  typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url));

const SOURCE_RELATIVE_DOCS_DIR = path.resolve(HERE, '../../docs');
const CWD_RELATIVE_DOCS_DIR = path.resolve(process.cwd(), 'docs');

export const DOCS_DIR = existsSync(SOURCE_RELATIVE_DOCS_DIR)
  ? SOURCE_RELATIVE_DOCS_DIR
  : CWD_RELATIVE_DOCS_DIR;

export const FrontmatterSchema = z.object({
  title: z.string().min(1),
  slug: z.string().regex(/^[a-z0-9-]+$/),
  order: z.number().int().nonnegative(),
  locale: z.enum(['vi', 'en']),
  /**
   * Optional flag (quick task 260526-r73) — when true, /docs index hides this
   * entry. Used by privacy.* and terms.* MDX bundles, which keep their own
   * top-level routes (/privacy, /terms) and should not appear in /docs index.
   */
  hideFromIndex: z.boolean().optional(),
  /**
   * Optional ISO-8601 date (YYYY-MM-DD). Surfaced inside MDX prose; not
   * rendered structurally by the page chrome.
   */
  lastUpdated: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional(),
});

export type Frontmatter = z.infer<typeof FrontmatterSchema>;

export const FILENAME_RE = /^([a-z0-9-]+)\.(vi|en)\.mdx$/;
export const SLUG_RE = /^[a-z0-9-]+$/;

/** Lists MDX filenames matching <slug>.<locale>.mdx (D-D5). Silent-empty on read failure. */
export async function listDocFilenames(): Promise<string[]> {
  try {
    const entries = await fs.readdir(DOCS_DIR);
    return entries.filter((n) => FILENAME_RE.test(n));
  } catch {
    return [];
  }
}

/**
 * Build a docs-dir absolute path from slug + locale.
 *
 * T-1.3.06-01 mitigation (path traversal): caller MUST validate slug against
 * SLUG_RE before invoking. Re-asserted here as defense-in-depth.
 */
export function buildDocPath(slug: string, locale: 'vi' | 'en'): string {
  if (!SLUG_RE.test(slug)) {
    throw new Error(`invalid slug: ${slug}`);
  }
  return path.join(DOCS_DIR, `${slug}.${locale}.mdx`);
}
