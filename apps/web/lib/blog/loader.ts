import { existsSync, promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';

/**
 * Blog content loader — mirrors lib/docs/loader.ts (same cwd-probing strategy,
 * same `<slug>.<locale>.mdx` filename contract, same fail-closed zod parsing) so
 * the blog inherits the docs system's deployment-correctness guarantees.
 *
 * Posts live at apps/web/blog/<slug>.<locale>.mdx. The dir is forced into the
 * standalone trace via outputFileTracingIncludes in next.config.ts (the path is
 * built dynamically at runtime, so file tracing cannot follow it otherwise).
 *
 * Server-only (uses fs/path); never import from a client component.
 */
const FILE_RELATIVE_HERE =
  typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url));

function resolveBlogDir(): string {
  const candidates = [
    path.join(process.cwd(), 'apps', 'web', 'blog'),
    path.join(process.cwd(), 'blog'),
    path.resolve(FILE_RELATIVE_HERE, '../../blog'),
  ];
  return candidates.find((candidate) => existsSync(candidate)) ?? candidates[candidates.length - 1];
}

export const BLOG_DIR = resolveBlogDir();

/** Resolve the public cover-image dir (apps/web/public/images/blog) across runtime layouts. */
function resolveBlogImageDir(): string {
  const candidates = [
    path.join(process.cwd(), 'apps', 'web', 'public', 'images', 'blog'),
    path.join(process.cwd(), 'public', 'images', 'blog'),
    path.resolve(FILE_RELATIVE_HERE, '../../public/images/blog'),
  ];
  return candidates.find((candidate) => existsSync(candidate)) ?? candidates[candidates.length - 1];
}

const BLOG_IMAGE_DIR = resolveBlogImageDir();

/** Public URL for a slug's cover image by convention, or undefined when the file is absent. */
export function coverImageForSlug(slug: string): string | undefined {
  if (!BLOG_SLUG_RE.test(slug)) return undefined;
  const fileName = `${slug}-cover.png`;
  return existsSync(path.join(BLOG_IMAGE_DIR, fileName)) ? `/images/blog/${fileName}` : undefined;
}

export const BlogFrontmatterSchema = z.object({
  title: z.string().min(1),
  slug: z.string().regex(/^[a-z0-9-]+$/),
  locale: z.enum(['vi', 'en']),
  /** SERP snippet + og:description. Keep 120–155 chars for best display. */
  description: z.string().min(1),
  /** ISO-8601 publication date (YYYY-MM-DD) → BlogPosting.datePublished. */
  datePublished: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  /** Optional ISO-8601 last-revision date → BlogPosting.dateModified. */
  dateModified: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .optional(),
  /** Display name of the author → BlogPosting.author. */
  author: z.string().min(1).default('Zero Mail'),
  /** Topical tags → keywords + cluster grouping. */
  tags: z.array(z.string().min(1)).default([]),
  /** When true, the post is excluded from the index, sitemap, and static params. */
  draft: z.boolean().optional(),
  /**
   * Public URL of the post's 16:9 cover image. Authors may set it explicitly;
   * otherwise the loader fills it by convention (`/images/blog/<slug>-cover.png`)
   * when that file exists. Drives the index card thumbnail, the post hero, and
   * the per-post `og:image`.
   */
  coverImage: z.string().optional(),
});

export type BlogFrontmatter = z.infer<typeof BlogFrontmatterSchema>;

export const BLOG_FILENAME_RE = /^([a-z0-9-]+)\.(vi|en)\.mdx$/;
export const BLOG_SLUG_RE = /^[a-z0-9-]+$/;

/** Lists MDX filenames matching <slug>.<locale>.mdx. Silent-empty on read failure. */
export async function listBlogFilenames(): Promise<string[]> {
  try {
    const entries = await fs.readdir(BLOG_DIR);
    return entries.filter((name) => BLOG_FILENAME_RE.test(name));
  } catch {
    return [];
  }
}

/**
 * Build a blog-dir absolute path from slug + locale. Caller MUST validate slug
 * against BLOG_SLUG_RE before invoking; re-asserted here as defense-in-depth
 * against path traversal.
 */
export function buildBlogPath(slug: string, locale: 'vi' | 'en'): string {
  if (!BLOG_SLUG_RE.test(slug)) {
    throw new Error(`invalid slug: ${slug}`);
  }
  return path.join(BLOG_DIR, `${slug}.${locale}.mdx`);
}

/**
 * Parsed, validated, non-draft posts for the active locale, newest first.
 * Reads each matching bundle and fail-skips any that don't parse — one bad post
 * never takes down the index or sitemap.
 */
export async function listPublishedPosts(locale: 'vi' | 'en'): Promise<BlogFrontmatter[]> {
  const matter = (await import('gray-matter')).default;
  const filenames = await listBlogFilenames();
  const posts: BlogFrontmatter[] = [];

  for (const filename of filenames) {
    const match = filename.match(BLOG_FILENAME_RE);
    if (!match || match[2] !== locale) continue;
    try {
      const source = await fs.readFile(path.join(BLOG_DIR, filename), 'utf8');
      const parsed = BlogFrontmatterSchema.safeParse(matter(source).data);
      if (parsed.success && parsed.data.draft !== true) {
        posts.push({
          ...parsed.data,
          coverImage: parsed.data.coverImage ?? coverImageForSlug(parsed.data.slug),
        });
      }
    } catch {
      // Unreadable/invalid bundle → skip; never fail the whole listing.
    }
  }

  return posts.sort((left, right) => right.datePublished.localeCompare(left.datePublished));
}

/** Slugs that have a published (non-draft) `vi` bundle — drives sitemap + static params. */
export async function listPublishedSlugs(): Promise<string[]> {
  const posts = await listPublishedPosts('vi');
  return posts.map((post) => post.slug).sort();
}
