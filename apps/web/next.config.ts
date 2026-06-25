import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const withNextIntl = createNextIntlPlugin('./i18n/request.ts');
const appDir = dirname(fileURLToPath(import.meta.url));
const workspaceRoot = join(appDir, '../..');

const nextConfig: NextConfig = {
  output: 'standalone',
  // Required for standalone output in pnpm monorepo: trace files from workspace root.
  outputFileTracingRoot: workspaceRoot,
  // lib/docs/loader.ts reads `${slug}.${locale}.mdx` from disk at runtime. The
  // path is built dynamically, so file tracing can't follow it and the MDX
  // bundles are otherwise dropped from the standalone output — production
  // /privacy, /terms, and /docs then render placeholder fallback copy. Force
  // the docs dir into the trace for the routes that read it. (Dockerfile also
  // copies apps/web/docs as a deterministic guarantee for the Turbopack build.)
  outputFileTracingIncludes: {
    '/privacy': ['./docs/**/*.mdx'],
    '/terms': ['./docs/**/*.mdx'],
    '/docs': ['./docs/**/*.mdx'],
    '/docs/[slug]': ['./docs/**/*.mdx'],
    // Blog MDX is read at runtime via a dynamically-built path (lib/blog/loader.ts);
    // force it into the trace so /blog and posts don't 404 in standalone output.
    '/blog': ['./blog/**/*.mdx'],
    '/blog/[slug]': ['./blog/**/*.mdx'],
    '/sitemap.xml': ['./blog/**/*.mdx', './docs/**/*.mdx'],
  },
  turbopack: {
    root: workspaceRoot,
  },
  transpilePackages: ['next-mdx-remote'],
  compiler: {
    removeConsole: { exclude: ['error', 'warn'] },
  },
  // Statically type every <Link href>, router.push(), router.replace() against
  // the actual app/ route tree. Build fails on typos.
  typedRoutes: true,
  async rewrites() {
    // Content negotiation (Next.js "Backend for Frontend" pattern): when an agent
    // sends `Accept: text/markdown`, route the public marketing/blog/docs URL to
    // the `/md` handler, which returns a markdown rendition. Browsers never send
    // that header, so they keep getting the normal HTML page. Only public paths
    // are listed — app/authenticated routes are intentionally never negotiable.
    const acceptsMarkdown = [
      { type: 'header' as const, key: 'accept', value: '(.*)text/markdown(.*)' },
    ];
    const markdownRewrite = (source: string, destination: string) => ({
      source,
      destination,
      has: acceptsMarkdown,
    });
    // MUST be `beforeFiles`: every source below (`/`, `/blog`, `/docs`, ...) has a
    // real filesystem page, and the array/`afterFiles` form only applies when NO
    // filesystem route matched — so it would never fire. `beforeFiles` runs ahead
    // of the page lookup, letting the Accept-header match override it.
    return {
      beforeFiles: [
        markdownRewrite('/', '/md'),
        markdownRewrite('/features', '/md/features'),
        markdownRewrite('/about', '/md/about'),
        markdownRewrite('/privacy', '/md/privacy'),
        markdownRewrite('/terms', '/md/terms'),
        markdownRewrite('/blog', '/md/blog'),
        markdownRewrite('/blog/:slug', '/md/blog/:slug'),
        markdownRewrite('/docs', '/md/docs'),
        markdownRewrite('/docs/:slug', '/md/docs/:slug'),
      ],
    };
  },
  async headers() {
    // RFC 8288 Link headers for agent / crawler discovery. Pointing at the
    // RFC-9727 api-catalog under /.well-known/ + the public docs site.
    const agentDiscoveryLinks = [
      '</.well-known/api-catalog>; rel="api-catalog"; type="application/linkset+json"',
      '<https://github.com/kl3inIT/zero-mail>; rel="service-doc"; type="text/html"',
    ].join(', ');
    return [
      {
        source: '/',
        headers: [{ key: 'Link', value: agentDiscoveryLinks }],
      },
      {
        source: '/privacy',
        headers: [{ key: 'Link', value: agentDiscoveryLinks }],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
