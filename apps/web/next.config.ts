import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const withNextIntl = createNextIntlPlugin('./i18n/request.ts');
const appDir = dirname(fileURLToPath(import.meta.url));
const workspaceRoot = join(appDir, '../..');

const nextConfig: NextConfig = {
  // Dependencies are hoisted to the workspace root by pnpm's hoisted linker.
  // Pin Turbopack there so imports like next-intl/* resolve in dev/build.
  turbopack: {
    root: workspaceRoot,
  },
  // Turbopack's stricter ESM expectations require explicit transpilation of
  // next-mdx-remote (which has unist/remark CJS edges) — workaround per the
  // hashicorp/next-mdx-remote README.
  transpilePackages: ['next-mdx-remote'],
  // Strip dev console.* calls from production bundles to shave KiB off the
  // landing chunk (Lighthouse Performance) and avoid leaking debug log output
  // to end users. Errors and warnings are preserved for diagnostics.
  compiler: {
    removeConsole: { exclude: ['error', 'warn'] },
  },
};

export default withNextIntl(nextConfig);
