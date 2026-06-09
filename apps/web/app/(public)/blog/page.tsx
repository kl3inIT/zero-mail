import type { Metadata } from 'next';
import Image from 'next/image';
import Link from 'next/link';
import { getLocale, getTranslations } from 'next-intl/server';

import { Card, CardContent } from '@/components/ui/card';
import { listPublishedPosts } from '@/lib/blog/loader';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('blog');
  return {
    title: { absolute: t('indexTitle') },
    description: t('indexDescription'),
    alternates: { canonical: '/blog' },
    openGraph: { title: t('indexTitle'), description: t('indexDescription') },
    twitter: { title: t('indexTitle'), description: t('indexDescription') },
  };
}

/**
 * Blog index — a responsive card grid of published (non-draft) posts for the
 * active locale, newest first. Each card leads with the post's 16:9 cover image.
 * The locale resolves from the NEXT_LOCALE cookie behind the single /blog URL.
 */
export default async function BlogIndexPage() {
  const locale = await getLocale();
  const [t, posts] = await Promise.all([
    getTranslations('blog'),
    listPublishedPosts(locale === 'vi' ? 'vi' : 'en'),
  ]);

  return (
    <main className="mx-auto max-w-5xl px-4 py-12">
      <header className="mb-10">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">{t('indexHeading')}</h1>
        <p className="text-muted-foreground mt-3 max-w-2xl text-base">{t('indexDescription')}</p>
      </header>

      {posts.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
            <p className="text-muted-foreground text-sm">{t('empty')}</p>
          </CardContent>
        </Card>
      ) : (
        <ul className="grid grid-cols-1 gap-8 sm:grid-cols-2">
          {posts.map((post, index) => (
            <li key={post.slug}>
              <Link
                href={`/blog/${post.slug}`}
                className="group border-border bg-card hover:border-primary/40 block overflow-hidden rounded-2xl border shadow-sm transition-all hover:shadow-md"
              >
                {post.coverImage ? (
                  <div className="relative aspect-video overflow-hidden">
                    <Image
                      src={post.coverImage}
                      alt={post.title}
                      fill
                      sizes="(max-width: 640px) 100vw, 50vw"
                      className="object-cover transition-transform duration-300 group-hover:scale-[1.03]"
                      priority={index < 2}
                    />
                  </div>
                ) : null}
                <div className="p-5">
                  <h2 className="text-foreground group-hover:text-primary text-lg font-semibold tracking-tight">
                    {post.title}
                  </h2>
                  <p className="text-muted-foreground mt-2 line-clamp-2 text-sm">
                    {post.description}
                  </p>
                  <p className="text-muted-foreground/80 mt-4 text-xs">
                    {t('publishedOn', { date: formatPostDate(post.datePublished, locale) })}
                    {' · '}
                    {t('by', { author: post.author })}
                  </p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
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
