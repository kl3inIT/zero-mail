import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';

// Vitest runs in --run (no watch) mode via the "test" npm script per VALIDATION.md.
// jsdom environment + @testing-library/react allow rendering Next.js client components
// against locale dictionaries; Wave 0 stubs in __tests__/i18n/ are it.skip until
// Plans 05/06 wire next-intl.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['__tests__/**/*.{test,spec}.{ts,tsx}'],
    setupFiles: ['__tests__/setup.ts'],
  },
  resolve: { alias: { '@': resolve(__dirname, '.') } },
});
