import matter from 'gray-matter';
import { promises as fs } from 'node:fs';
import type { MetadataRoute } from 'next';

import { listPublishedPosts } from '@/lib/blog/loader';
import { buildDocPath, FILENAME_RE, listDocFilenames } from '@/lib/docs/loader';
import { siteUrl } from '@/lib/site';

/**
 * Public sitemap served at /sitemap.xml — the URL robots.txt already advertises.
 * Only crawlable, public surfaces belong here; authenticated app routes
 * (/chat, /rules, /settings, …) are Disallow-ed in robots.txt and omitted.
 *
 * URLs are locale-agnostic: each public route resolves its language from the
 * NEXT_LOCALE cookie behind a single canonical URL, so we emit one entry per
 * route rather than per (route × locale).
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  // Stable `lastmod` for the static marketing routes. Using `new Date()` here
  // stamped EVERY page with the deploy timestamp on every build, so a code-only
  // redeploy looked like all content changed — a noisy freshness signal Google
  // learns to distrust. Bump this constant only when the static pages' actual
  // content changes; blog/doc entries below keep their own real per-file dates.
  const staticLastModified = new Date('2026-06-09');

  const staticEntries: MetadataRoute.Sitemap = [
    { url: siteUrl('/'), lastModified: staticLastModified, changeFrequency: 'weekly', priority: 1 },
    {
      url: siteUrl('/features'),
      lastModified: staticLastModified,
      changeFrequency: 'monthly',
      priority: 0.8,
    },
    {
      url: siteUrl('/about'),
      lastModified: staticLastModified,
      changeFrequency: 'monthly',
      priority: 0.6,
    },
    {
      url: siteUrl('/blog'),
      lastModified: staticLastModified,
      changeFrequency: 'weekly',
      priority: 0.7,
    },
    {
      url: siteUrl('/docs'),
      lastModified: staticLastModified,
      changeFrequency: 'monthly',
      priority: 0.6,
    },
    {
      url: siteUrl('/privacy'),
      lastModified: staticLastModified,
      changeFrequency: 'yearly',
      priority: 0.4,
    },
    {
      url: siteUrl('/terms'),
      lastModified: staticLastModified,
      changeFrequency: 'yearly',
      priority: 0.4,
    },
  ];

  const docEntries = await listPublicDocSlugs().then((slugs) =>
    slugs.map<MetadataRoute.Sitemap[number]>((slug) => ({
      url: siteUrl(`/docs/${slug}`),
      lastModified: staticLastModified,
      changeFrequency: 'monthly',
      priority: 0.5,
    })),
  );

  // Blog posts: one canonical URL per slug. The post's own datePublished /
  // dateModified drives lastmod so re-crawls reflect real edits, not deploy time.
  const blogEntries = await listPublishedPosts('vi').then((posts) =>
    posts.map<MetadataRoute.Sitemap[number]>((post) => ({
      url: siteUrl(`/blog/${post.slug}`),
      lastModified: new Date(`${post.dateModified ?? post.datePublished}T00:00:00Z`),
      changeFrequency: 'monthly',
      priority: 0.6,
    })),
  );

  return [...staticEntries, ...docEntries, ...blogEntries];
}

/**
 * Doc slugs that appear in the /docs index (hideFromIndex !== true), so privacy
 * and terms — which keep their own top-level routes — are not duplicated here.
 * Slugs are shared across locales, so reading the `vi` bundle is sufficient.
 */
async function listPublicDocSlugs(): Promise<string[]> {
  const filenames = await listDocFilenames();
  const viSlugs = new Set<string>();
  for (const filename of filenames) {
    const match = filename.match(FILENAME_RE);
    if (match && match[2] === 'vi') {
      viSlugs.add(match[1]);
    }
  }

  const visible: string[] = [];
  for (const slug of viSlugs) {
    try {
      const source = await fs.readFile(buildDocPath(slug, 'vi'), 'utf8');
      const { data } = matter(source);
      if (data?.hideFromIndex !== true) {
        visible.push(slug);
      }
    } catch {
      // Unreadable bundle → skip; never fail the whole sitemap on one doc.
    }
  }
  return visible.sort();
}
