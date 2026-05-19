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
  turbopack: {
    root: workspaceRoot,
  },
  transpilePackages: ['next-mdx-remote'],
  compiler: {
    removeConsole: { exclude: ['error', 'warn'] },
  },
};

export default withNextIntl(nextConfig);
