import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const withNextIntl = createNextIntlPlugin('./i18n/request.ts');
const appDir = dirname(fileURLToPath(import.meta.url));
const workspaceRoot = join(appDir, '../..');

type WebpackExternalContext = {
  request?: string;
};

type WebpackExternalCallback = (error?: Error | null, result?: string) => void;

const nextConfig: NextConfig = {
  output: 'standalone',
  // Required for standalone output in pnpm monorepo: trace files from workspace root.
  outputFileTracingRoot: workspaceRoot,
  turbopack: {
    root: workspaceRoot,
  },
  transpilePackages: ['next-mdx-remote'],
  // `msw` and `@mswjs/interceptors` are Node-only and use deep subpath exports
  // (`./ClientRequest`) that Next's bundler refuses to follow. They are imported
  // dynamically by `instrumentation.ts` only when `NEXT_PUBLIC_E2E_MSW=1`.
  // Externalizing makes Next leave them as runtime `require` calls.
  serverExternalPackages: ['msw', '@mswjs/interceptors'],
  webpack(configuration, { isServer }) {
    if (!isServer) return configuration;

    const existingExternals = Array.isArray(configuration.externals)
      ? configuration.externals
      : configuration.externals
        ? [configuration.externals]
        : [];

    configuration.externals = [
      ...existingExternals,
      ({ request }: WebpackExternalContext, callback: WebpackExternalCallback) => {
        if (request === 'msw/node' || request?.startsWith('@mswjs/interceptors')) {
          return callback(null, `commonjs ${request}`);
        }

        return callback();
      },
    ];

    return configuration;
  },
  compiler: {
    removeConsole: { exclude: ['error', 'warn'] },
  },
  // Statically type every <Link href>, router.push(), router.replace() against
  // the actual app/ route tree. Build fails on typos.
  typedRoutes: true,
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
