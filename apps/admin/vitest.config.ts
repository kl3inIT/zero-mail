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
