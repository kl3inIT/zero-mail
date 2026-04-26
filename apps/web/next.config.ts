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
};

export default withNextIntl(nextConfig);
