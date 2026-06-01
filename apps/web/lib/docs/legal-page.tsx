import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { compileMDX } from 'next-mdx-remote/rsc';

import {
  extractLevelTwoHeadings,
  fallbackLegalBody,
  fallbackLegalTitle,
  readLegalDocSource,
  slugifyHeading,
  type LegalDocSlug,
} from '@/lib/docs/legal-page-data';

function extractTextFromNode(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(extractTextFromNode).join('');
  return '';
}

const mdxComponents = {
  h2: ({ children, className, ...properties }: ComponentPropsWithoutRef<'h2'>) => {
    const headingText = extractTextFromNode(children);
    const id = typeof properties.id === 'string' ? properties.id : slugifyHeading(headingText);

    return (
      <h2
        {...properties}
        id={id}
        className={[
          'text-foreground mt-12 mb-4 scroll-mt-24 text-2xl font-semibold tracking-tight',
          className,
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {children}
      </h2>
    );
  },
  p: ({ className, ...properties }: ComponentPropsWithoutRef<'p'>) => (
    <p
      {...properties}
      className={['text-foreground/90 my-4 leading-7', className].filter(Boolean).join(' ')}
    />
  ),
  ul: ({ className, ...properties }: ComponentPropsWithoutRef<'ul'>) => (
    <ul
      {...properties}
      className={['text-foreground/90 my-4 list-disc space-y-2 pl-6 leading-7', className]
        .filter(Boolean)
        .join(' ')}
    />
  ),
  strong: ({ className, ...properties }: ComponentPropsWithoutRef<'strong'>) => (
    <strong
      {...properties}
      className={['text-foreground font-semibold', className].filter(Boolean).join(' ')}
    />
  ),
};

export async function LegalDocPage({ slug }: { slug: LegalDocSlug }) {
  const document = await readLegalDocSource(slug);
  if (!document) {
    return (
      <section className="mx-auto grid w-full max-w-6xl grid-cols-1 gap-10 px-4 py-8 sm:py-12 lg:grid-cols-[minmax(0,48rem)_16rem] lg:items-start lg:px-6">
        <article className="min-w-0">
          <h1 className="text-foreground mb-3 text-4xl font-semibold tracking-tight">
            {fallbackLegalTitle(slug)}
          </h1>
          <div className="legal-mdx">
            <p>{fallbackLegalBody(slug)}</p>
          </div>
        </article>
      </section>
    );
  }

  const { frontmatter, source } = document;
  const headings = extractLevelTwoHeadings(source);
  const { content } = await compileMDX({
    source,
    components: mdxComponents,
    options: { parseFrontmatter: true },
  });

  const tocLabel = frontmatter.locale === 'vi' ? 'Trong trang này' : 'On this page';

  return (
    <section className="mx-auto grid w-full max-w-6xl grid-cols-1 gap-10 px-4 py-8 sm:py-12 lg:grid-cols-[minmax(0,48rem)_16rem] lg:items-start lg:px-6">
      <article className="min-w-0">
        <h1 className="text-foreground mb-3 text-4xl font-semibold tracking-tight">
          {frontmatter.title}
        </h1>
        <div className="legal-mdx">{content}</div>
      </article>

      <aside className="order-first lg:sticky lg:top-24 lg:order-none lg:max-h-[calc(100vh-7rem)] lg:overflow-y-auto">
        <nav aria-label={tocLabel} className="border-border lg:border-l lg:pl-5">
          <h2 className="text-foreground mb-3 text-sm font-semibold tracking-wide uppercase">
            {tocLabel}
          </h2>
          <ol className="text-muted-foreground space-y-2 text-sm">
            {headings.map((heading) => (
              <li key={heading.id}>
                <a
                  href={`#${heading.id}`}
                  className="hover:text-foreground block underline-offset-4 hover:underline"
                >
                  {heading.text}
                </a>
              </li>
            ))}
          </ol>
        </nav>
      </aside>
    </section>
  );
}
