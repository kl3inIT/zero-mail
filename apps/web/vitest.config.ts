import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
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
// Fix: dedupe react/react-dom (Vite picks one resolved id for the whole graph).
// We do NOT add explicit path aliases or server.deps.inline directives because
// any combination of those re-introduces the duplicate React graph (some module
// resolves through the alias, others through CJS require, leading to the same
// dispatcher mismatch).
// pnpm hoists `react` and `react-dom` to BOTH apps/web/node_modules and the
// workspace root. Vitest's Vite resolver picks the one nearest the importer,
// which means components in apps/web/components/ get apps/web's react, while
// @testing-library/react (at root) gets root react. Two React module instances
// → null hooks dispatcher → "Cannot read properties of null (reading 'useState')".
//
// Force a single absolute path for both react and react-dom by aliasing every
// known subpath. The aliases are evaluated regardless of the importer location,
// so apps/web's react and root's react converge on the SAME absolute file.
const reactRoot = resolve(__dirname, '../../node_modules/react');
const reactDomRoot = resolve(__dirname, '../../node_modules/react-dom');

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['__tests__/**/*.{test,spec}.{ts,tsx}'],
    // Plan 07: Playwright spec lives under __tests__/e2e/ and uses its own
    // runner. Vitest must NOT try to load it (Playwright's `test()` throws
    // when called outside its runner).
    exclude: ['**/node_modules/**', '__tests__/e2e/**'],
    setupFiles: ['__tests__/setup.ts'],
  },
  resolve: {
    alias: [
      { find: /^@\/(.*)$/, replacement: resolve(__dirname, '.') + '/$1' },
      { find: 'react/jsx-dev-runtime', replacement: resolve(reactRoot, 'jsx-dev-runtime.js') },
      { find: 'react/jsx-runtime', replacement: resolve(reactRoot, 'jsx-runtime.js') },
      { find: 'react-dom/client', replacement: resolve(reactDomRoot, 'client.js') },
      { find: 'react-dom/test-utils', replacement: resolve(reactDomRoot, 'test-utils.js') },
      { find: /^react-dom$/, replacement: resolve(reactDomRoot, 'index.js') },
      { find: /^react$/, replacement: resolve(reactRoot, 'index.js') },
    ],
    dedupe: ['react', 'react-dom'],
  },
});
