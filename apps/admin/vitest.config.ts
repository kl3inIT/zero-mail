import react from '@vitejs/plugin-react-swc';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { defineConfig } from 'vitest/config';

const requireFromAdmin = createRequire(import.meta.url);

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['**/node_modules/**', '**/e2e/**'],
    setupFiles: ['src/test-setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
      include: [
        'src/features/**/*.{ts,tsx}',
        'src/lib/**/*.{ts,tsx}',
        'src/components/**/*.{ts,tsx}',
      ],
      exclude: [
        '**/node_modules/**',
        '**/dist/**',
        '**/*.d.ts',
        '**/*.config.{ts,js,mjs,mts}',
        'src/components/ui/**',
        'src/lib/api/admin-schema.d.ts',
        'src/routeTree.gen.ts',
      ],
      // Floors set just below current. Admin has 3 tests vs 11 routes + 14
      // mutation hooks — P0 follow-up: tests for security-critical mutations
      // (master-keys, role-grants, tenant-delete) then raise these.
      thresholds: {
        lines: 3,
        functions: 5,
        branches: 15,
        statements: 3,
      },
    },
  },
  resolve: {
    alias: [
      { find: /^@\/(.*)$/, replacement: resolve(__dirname, 'src') + '/$1' },
      { find: 'react/jsx-dev-runtime', replacement: requireFromAdmin.resolve('react/jsx-dev-runtime') },
      { find: 'react/jsx-runtime', replacement: requireFromAdmin.resolve('react/jsx-runtime') },
      { find: 'react-dom/client', replacement: requireFromAdmin.resolve('react-dom/client') },
      { find: /^react-dom$/, replacement: requireFromAdmin.resolve('react-dom') },
      { find: /^react$/, replacement: requireFromAdmin.resolve('react') },
    ],
    dedupe: ['react', 'react-dom'],
  },
});
