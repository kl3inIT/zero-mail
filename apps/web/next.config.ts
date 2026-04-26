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
  // Phase 1.3 Plan 02 (Pitfall 6 in 01.3-RESEARCH.md): Turbopack's stricter
  // ESM expectations require explicit transpilation of next-mdx-remote (which
  // has unist/remark CJS edges). Documented temporary workaround per the
  // hashicorp/next-mdx-remote README. Plan 06 installs the runtime dep.
  transpilePackages: ['next-mdx-remote'],
};

export default withNextIntl(nextConfig);
