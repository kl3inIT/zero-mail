import { promises as fs } from 'node:fs';
import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import type { Metadata } from 'next';
import matter from 'gray-matter';
import { compileMDX } from 'next-mdx-remote/rsc';
import { getLocale } from 'next-intl/server';

import { FrontmatterSchema, buildDocPath } from '@/lib/docs/loader';

type LegalDocSlug = 'privacy' | 'terms';

type LegalHeading = {
  id: string;
  text: string;
};

function extractTextFromNode(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(extractTextFromNode).join('');
  return '';
}

function stripMarkdownSyntax(text: string): string {
  return text
    .replace(/\[([^\][]+)]\([^)]+\)/g, '$1')
    .replace(/[`*_~]/g, '')
    .trim();
}

function slugifyHeading(text: string): string {
  const normalizedText = text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return normalizedText || 'section';
}

function extractLevelTwoHeadings(source: string): LegalHeading[] {
  const contentWithoutFrontmatter = source.replace(/^---[\s\S]*?---\s*/, '');

  return contentWithoutFrontmatter
    .split(/\r?\n/)
    .map((line) => line.match(/^##\s+(.+?)\s*$/))
    .filter((match): match is RegExpMatchArray => Boolean(match))
    .map((match) => {
      const text = stripMarkdownSyntax(match[1]);
      return {
        id: slugifyHeading(text),
        text,
      };
    });
}

async function readLegalDocSource(slug: LegalDocSlug) {
  const locale = await resolveLegalLocale();

  const filePath = buildDocPath(slug, locale);

  try {
    const source = await fs.readFile(filePath, 'utf8');
    const parsedFrontmatter = FrontmatterSchema.safeParse(matter(source).data);
    if (!parsedFrontmatter.success) return null;
    if (parsedFrontmatter.data.slug !== slug || parsedFrontmatter.data.locale !== locale)
      return null;

    return {
      frontmatter: parsedFrontmatter.data,
      locale,
      source,
    };
  } catch {
    return null;
  }
}

async function resolveLegalLocale(): Promise<'en' | 'vi'> {
  try {
    const locale = await getLocale();
    return locale === 'vi' ? 'vi' : 'en';
  } catch {
    return 'en';
  }
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

export async function generateLegalDocMetadata(slug: LegalDocSlug): Promise<Metadata> {
  const document = await readLegalDocSource(slug);
  const title = document?.frontmatter.title ?? fallbackLegalTitle(slug);
  const description = fallbackLegalDescription(slug);

  return {
    title,
    description,
    alternates: {
      canonical: `/${slug}`,
    },
    openGraph: {
      title,
      description,
      type: 'article',
    },
  };
}

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

function fallbackLegalTitle(slug: LegalDocSlug): string {
  return slug === 'privacy' ? 'Chính sách bảo mật' : 'Điều khoản dịch vụ';
}

function fallbackLegalDescription(slug: LegalDocSlug): string {
  return slug === 'privacy'
    ? 'How Zero Mail collects, uses, protects, and limits access to Gmail and account data.'
    : 'The terms that govern use of Zero Mail, including Gmail authorization, AI actions, credits, and acceptable use.';
}

function fallbackLegalBody(slug: LegalDocSlug): string {
  return slug === 'privacy'
    ? 'This page explains how Zero Mail handles account data, Gmail access, automation, and privacy safeguards.'
    : 'These terms describe how Zero Mail can be used, including Gmail authorization, automation controls, credits, and acceptable use.';
}
