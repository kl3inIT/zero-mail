import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const DOCS_DIR = resolve(APP_WEB, 'docs');
const DOCS_INDEX = resolve(APP_WEB, 'app/(public)/docs/page.tsx');
const DOCS_SLUG = resolve(APP_WEB, 'app/(public)/docs/[slug]/page.tsx');
const DOCS_LOADING = resolve(APP_WEB, 'app/(public)/docs/[slug]/loading.tsx');
const DOCS_LOADER = resolve(APP_WEB, 'lib/docs/loader.ts');
const FILENAME_RE = /^([a-z0-9-]+)\.(vi|en)\.mdx$/;

// REVIEWS Revision 4 (Codex MEDIUM): replace beforeAll + dynamic-import-with-skipIf
// pattern (which evaluates skipIf BEFORE beforeAll runs, so `matter` is always undefined
// at gate time) with a STATIC existsSync predicate evaluated at MODULE LOAD time. This
// means skipIf is decided correctly at test-define time. The dynamic import inside the
// it body is fine — it only runs when not skipped, and only after gray-matter is
// installed by Plan 06.
// pnpm hoist may place gray-matter under apps/web/node_modules/ OR the workspace
// root node_modules/. Check both so the gate flips correctly under either layout.
const grayMatterInstalled =
  existsSync(resolve(process.cwd(), 'node_modules/gray-matter/package.json')) ||
  existsSync(resolve(process.cwd(), '../../node_modules/gray-matter/package.json'));

describe('Phase 1.3 — MDX docs pipeline (D-D1..D-D5)', () => {
  it('docs/ exists with at least 4 MDX files (2 slugs x 2 locales)', () => {
    expect(existsSync(DOCS_DIR)).toBe(true);
    const entries = readdirSync(DOCS_DIR).filter((n) => n.endsWith('.mdx'));
    expect(entries.length).toBeGreaterThanOrEqual(4);
  });

  it('every MDX filename matches <slug>.<locale>.mdx (D-D5)', () => {
    const entries = readdirSync(DOCS_DIR).filter((n) => n.endsWith('.mdx'));
    for (const name of entries) {
      expect(name).toMatch(FILENAME_RE);
    }
  });

  // Frontmatter-shape: GATE on grayMatterInstalled (static existsSync at module-load).
  // SKIP until Plan 06 installs gray-matter; PASS once installed + sample MDX files land.
  describe('frontmatter shape (D-D3) — gated on gray-matter availability', () => {
    it.skipIf(!grayMatterInstalled)(
      'all sample MDX files have valid gray-matter frontmatter',
      async () => {
        // Defer module resolution to runtime by hopping the spec through a
        // non-literal expression — Vite's import-analysis only pre-resolves
        // dynamic imports whose spec is a literal string. The static
        // existsSync gate above (REVIEWS Revision 4) already guarantees this
        // body only runs when the dependency is present (Plan 06 installs it).
        const moduleId = ['gray', 'matter'].join('-');
        const matter = (await import(moduleId)).default;
        const entries = readdirSync(DOCS_DIR).filter((n) => n.endsWith('.mdx'));
        for (const name of entries) {
          const raw = readFileSync(resolve(DOCS_DIR, name), 'utf8');
          const { data } = matter(raw);
          expect(typeof data.title).toBe('string');
          expect(typeof data.slug).toBe('string');
          expect(typeof data.order).toBe('number');
          expect(['vi', 'en']).toContain(data.locale);
          const m = name.match(FILENAME_RE);
          expect(m).not.toBeNull();
          expect(data.slug).toBe(m![1]);
          expect(data.locale).toBe(m![2]);
        }
      },
    );
  });

  it('docs index page reads filesystem with gray-matter', () => {
    expect(existsSync(DOCS_INDEX)).toBe(true);
    const src = readFileSync(DOCS_INDEX, 'utf8');
    expect(src).toMatch(/gray-matter|from\s+['"]gray-matter['"]/);
    // Phase 01.5 Plan 02 deflation: readdir is now abstracted behind
    // listDocFilenames() from @/lib/docs/loader. Check for either pattern.
    expect(src).toMatch(/readdir|listDocFilenames/);
  });

  it('docs slug page uses compileMDX with await params (Next 16 Pattern 2)', () => {
    expect(existsSync(DOCS_SLUG)).toBe(true);
    const src = readFileSync(DOCS_SLUG, 'utf8');
    expect(src).toMatch(/compileMDX/);
    expect(src).toMatch(/await\s+params/);
    expect(src).toMatch(/from\s+['"]next-mdx-remote\/rsc['"]/);
  });

  it('docs slug page enforces slug regex ^[a-z0-9-]+$ (Pitfall 4 path traversal)', () => {
    const src = readFileSync(DOCS_SLUG, 'utf8');
    expect(src).toMatch(/SLUG_RE\.test\(slug\)/);
    expect(src).toMatch(/notFound\(\)/);

    const loaderSrc = readFileSync(DOCS_LOADER, 'utf8');
    expect(loaderSrc).toMatch(/export const SLUG_RE\s*=\s*\/\^\[a-z0-9-]\+\$\/;/);
  });

  // REVIEWS Revision 6 (OpenCode MEDIUM, Codex LOW)
  it('docs slug page uses zod safeParse for frontmatter validation', () => {
    const src = readFileSync(DOCS_SLUG, 'utf8');
    expect(src).toMatch(/safeParse/);
    expect(src).toMatch(/from\s+['"]zod['"]|FrontmatterSchema/);
  });

  it('docs slug page enforces slug+locale consistency (fm.data.slug !== params.slug -> notFound)', () => {
    const src = readFileSync(DOCS_SLUG, 'utf8');
    // Either an inline comparison `fm.data.slug !== slug` / `fm.data.locale !== locale`
    // OR a helper extracted to lib/docs/loader.ts that performs the same check.
    // Acceptable shapes: any of `fm.data.slug !==`, `frontmatter.slug !==`, or a call
    // to a loader helper that contains the consistency comparison.
    const consistencyCheck =
      /(fm\.data|frontmatter|parsed\.data)\.slug\s*!==/.test(src) ||
      /(fm\.data|frontmatter|parsed\.data)\.locale\s*!==/.test(src) ||
      /assertSlugAndLocaleMatch|loadDoc|getDocBySlug/.test(src);
    expect(consistencyCheck).toBe(true);
    // notFound() must still be the fail-closed branch
    expect(src).toMatch(/notFound\(\)/);
  });

  it('lib/docs/loader.ts resolves the docs dir via cwd candidates + a file-relative fallback', () => {
    expect(existsSync(DOCS_LOADER)).toBe(true);
    const src = readFileSync(DOCS_LOADER, 'utf8');
    // Turbopack (Next 16 default) inlines import.meta.url/__dirname to a literal
    // /ROOT path that does not exist in the standalone runtime, so the loader must
    // anchor on process.cwd() candidates first. The file-relative path stays only
    // as the webpack-dev fallback. See loader.ts resolveDocsDir() for the rationale.
    expect(src).toMatch(/process\.cwd\(\)/);
    const fileRelativeFallback =
      /path\.resolve\(\s*__dirname/.test(src) ||
      /fileURLToPath\(\s*import\.meta\.url/.test(src) ||
      /path\.resolve\(\s*FILE_RELATIVE_HERE/.test(src);
    expect(fileRelativeFallback).toBe(true);
  });

  it('docs/[slug]/loading.tsx exists', () => {
    expect(existsSync(DOCS_LOADING)).toBe(true);
  });
});
