import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';

// Vitest runs in --run (no watch) mode via the "test" npm script per VALIDATION.md.
// jsdom environment + @testing-library/react allow rendering Next.js client components
// against locale dictionaries.
//
// Plan 06 deviation (Rule 3 — blocking): pnpm hoists react/react-dom to the
// workspace root AND apps/web/node_modules has its own copy. Two React module
// records → null hooks dispatcher when components and the renderer disagree on
// which copy to use ("Cannot read properties of null (reading 'useTransition'/
// 'useRef'/'useContext')").
//
// Fix: dedupe react/react-dom and alias the common React entrypoints to a
// single package instance. Resolve through the app package instead of
// hard-coding ../../node_modules: a fresh pnpm install on CI may not place React
// at the workspace root.
const requireFromApp = createRequire(import.meta.url);

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    pool: 'threads',
    maxWorkers: 4,
    include: ['__tests__/**/*.{test,spec}.{ts,tsx}', 'features/**/*.{test,spec}.{ts,tsx}'],
    // Playwright specs live under e2e/ and use their own runner. Vitest must
    // not load them because Playwright's test() throws outside its runner.
    exclude: ['**/node_modules/**'],
    setupFiles: ['__tests__/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
      include: ['features/**/*.{ts,tsx}', 'app/**/*.{ts,tsx}', 'lib/**/*.{ts,tsx}'],
      exclude: [
        '**/node_modules/**',
        '**/dist/**',
        '**/.next/**',
        'e2e/**',
        '__tests__/**',
        '**/*.d.ts',
        '**/*.config.{ts,js,mjs,mts}',
        'components/ui/**',
        'lib/api/schema.d.ts',
      ],
    },
  },
  resolve: {
    alias: [
      { find: /^@\/(.*)$/, replacement: resolve(__dirname, '.') + '/$1' },
      {
        find: 'react/jsx-dev-runtime',
        replacement: requireFromApp.resolve('react/jsx-dev-runtime'),
      },
      { find: 'react/jsx-runtime', replacement: requireFromApp.resolve('react/jsx-runtime') },
      { find: 'react-dom/client', replacement: requireFromApp.resolve('react-dom/client') },
      { find: 'react-dom/test-utils', replacement: requireFromApp.resolve('react-dom/test-utils') },
      { find: /^react-dom$/, replacement: requireFromApp.resolve('react-dom') },
      { find: /^react$/, replacement: requireFromApp.resolve('react') },
    ],
    dedupe: ['react', 'react-dom'],
  },
});
