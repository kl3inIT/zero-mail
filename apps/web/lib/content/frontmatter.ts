import { parse as parseYaml } from 'yaml';

export type ParsedFrontmatter = {
  data: Record<string, unknown>;
  content: string;
};

const FRONTMATTER_BLOCK_RE = /^---[ \t]*\r?\n([\s\S]*?)\r?\n---[ \t]*(?:\r?\n|$)/;

export function parseFrontmatter(source: string): ParsedFrontmatter {
  const normalizedSource = source.startsWith('\uFEFF') ? source.slice(1) : source;
  const match = FRONTMATTER_BLOCK_RE.exec(normalizedSource);
  if (!match) {
    return { data: {}, content: normalizedSource };
  }

  const parsedData = parseYaml(match[1]);
  return {
    data: isRecord(parsedData) ? parsedData : {},
    content: normalizedSource.slice(match[0].length),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
