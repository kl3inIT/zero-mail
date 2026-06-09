import { promises as fs } from 'node:fs';
import type { Metadata } from 'next';
import Image from 'next/image';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { compileMDX } from 'next-mdx-remote/rsc';
import { getLocale, getTranslations } from 'next-intl/server';

import {
  BLOG_SLUG_RE,
  BlogFrontmatterSchema,
  buildBlogPath,
  coverImageForSlug,
  listPublishedSlugs,
} from '@/lib/blog/loader';
import { getSiteUrl, SITE_NAME, siteUrl } from '@/lib/site';

/** Pre-render the published posts at build time (slugs share across locales). */
export async function generateStaticParams(): Promise<{ slug: string }[]> {
  const slugs = await listPublishedSlugs();
  return slugs.map((slug) => ({ slug }));
}

async function readPost(slug: string, locale: 'vi' | 'en') {
  if (!BLOG_SLUG_RE.test(slug)) return null;
  let source: string;
  try {
    source = await fs.readFile(buildBlogPath(slug, locale), 'utf8');
  } catch {
    return null;
  }
  const compiled = await compileMDX({ source, options: { parseFrontmatter: true } });
  const parsed = BlogFrontmatterSchema.safeParse(compiled.frontmatter);
  if (!parsed.success) return null;
  if (parsed.data.slug !== slug || parsed.data.locale !== locale || parsed.data.draft === true) {
    return null;
  }
  const frontmatter = {
    ...parsed.data,
    coverImage: parsed.data.coverImage ?? coverImageForSlug(slug),
  };
  return { frontmatter, content: compiled.content };
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const locale = (await getLocale()) === 'vi' ? 'vi' : 'en';
  const post = await readPost(slug, locale);
  if (!post) return {};

  const { title, description, datePublished, dateModified, author, tags, coverImage } =
    post.frontmatter;
  const ogImages = coverImage ? [coverImage] : undefined;
  return {
    title: { absolute: `${title} · ${SITE_NAME}` },
    description,
    keywords: tags,
    alternates: { canonical: `/blog/${slug}` },
    openGraph: {
      type: 'article',
      title,
      description,
      publishedTime: datePublished,
      modifiedTime: dateModified ?? datePublished,
      authors: [author],
      tags,
      images: ogImages,
    },
    twitter: { title, description, images: ogImages },
  };
}

/**
 * Single blog post. Mirrors the docs/[slug] MDX pipeline (compileMDX, fail-closed
 * zod + slug/locale consistency → notFound) and adds the full SEO surface a doc
 * page omits: BlogPosting JSON-LD, an article OG card, and a visible byline/date.
 */
export default async function BlogPostPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const localeRaw = await getLocale();
  const locale = localeRaw === 'vi' ? 'vi' : 'en';

  const [t, post] = await Promise.all([getTranslations('blog'), readPost(slug, locale)]);
  if (!post) notFound();

  const { title, description, datePublished, dateModified, author, tags, coverImage } =
    post.frontmatter;
  const site = getSiteUrl();
  const postUrl = siteUrl(`/blog/${slug}`);
  const imageUrl = coverImage ? `${site}${coverImage}` : siteUrl('/opengraph-image');

  const structuredData = {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    '@id': `${postUrl}#post`,
    mainEntityOfPage: { '@type': 'WebPage', '@id': postUrl },
    headline: title,
    description,
    inLanguage: locale,
    datePublished,
    dateModified: dateModified ?? datePublished,
    keywords: tags.join(', '),
    author: { '@type': 'Organization', name: author, url: site },
    publisher: { '@id': `${site}/#organization` },
    image: imageUrl,
  };

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }}
      />
      {coverImage ? (
        <div className="border-border relative mb-8 aspect-video overflow-hidden rounded-2xl border">
          <Image
            src={coverImage}
            alt={title}
            fill
            sizes="(max-width: 768px) 100vw, 768px"
            className="object-cover"
            priority
          />
        </div>
      ) : null}
      <header className="mb-8">
        <h1 className="text-3xl leading-tight font-extrabold tracking-tight text-(--ink) md:text-4xl">
          {title}
        </h1>
        <p className="mt-4 text-sm text-(--text-muted)">
          {t('publishedOn', { date: formatPostDate(datePublished, locale) })}
          {' · '}
          {t('by', { author })}
        </p>
      </header>
      <article className="prose prose-zinc dark:prose-invert prose-headings:text-(--ink) prose-headings:tracking-tight prose-a:text-(--accent) max-w-none">
        {post.content}
      </article>
      <p className="mt-10">
        <Link href="/blog" className="font-medium text-(--accent) underline">
          {t('backToList')}
        </Link>
      </p>
    </main>
  );
}

function formatPostDate(isoDate: string, locale: string): string {
  const parsed = new Date(`${isoDate}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return isoDate;
  return new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(parsed);
}
