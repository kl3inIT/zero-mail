import { promises as fs } from 'node:fs';
import type { Metadata } from 'next';
import { getLocale } from 'next-intl/server';

import { parseFrontmatter } from '@/lib/content/frontmatter';
import { FrontmatterSchema, buildDocPath } from '@/lib/docs/loader';

export type LegalDocSlug = 'privacy' | 'terms';

export type LegalHeading = {
  id: string;
  text: string;
};

export function stripMarkdownSyntax(text: string): string {
  return text
    .replace(/\[([^\][]+)]\([^)]+\)/g, '$1')
    .replace(/[`*_~]/g, '')
    .trim();
}

export function slugifyHeading(text: string): string {
  const normalizedText = text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return normalizedText || 'section';
}

export function extractLevelTwoHeadings(source: string): LegalHeading[] {
  const contentWithoutFrontmatter = parseFrontmatter(source).content;

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

export async function readLegalDocSource(slug: LegalDocSlug) {
  const locale = await resolveLegalLocale();

  const filePath = buildDocPath(slug, locale);

  try {
    const source = await fs.readFile(filePath, 'utf8');
    const parsedFrontmatter = FrontmatterSchema.safeParse(parseFrontmatter(source).data);
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

export function fallbackLegalTitle(slug: LegalDocSlug): string {
  return slug === 'privacy' ? 'Chính sách bảo mật' : 'Điều khoản dịch vụ';
}

export function fallbackLegalDescription(slug: LegalDocSlug): string {
  return slug === 'privacy'
    ? 'How Zero Mail collects, uses, protects, and limits access to Gmail and account data.'
    : 'The terms that govern use of Zero Mail, including Gmail authorization, AI actions, credits, and acceptable use.';
}

export function fallbackLegalBody(slug: LegalDocSlug): string {
  return slug === 'privacy'
    ? 'This page explains how Zero Mail handles account data, Gmail access, automation, and privacy safeguards.'
    : 'These terms describe how Zero Mail can be used, including Gmail authorization, automation controls, credits, and acceptable use.';
}
