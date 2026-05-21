import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import prettier from 'eslint-config-prettier/flat';

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'src/routeTree.gen.ts',
      'src/lib/api/admin-schema.d.ts',
      'src/components/ui/**',
      'playwright-report/**',
      'test-results/**',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        {
          allowConstantExport: true,
          // TanStack Router file-based routes co-locate `Route = createFileRoute(...)`
          // with their component — required by the framework, not a refactor target.
          allowExportNames: ['Route'],
        },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  // TanStack Router file-based routes are structurally incompatible with the
  // react-refresh/only-export-components rule. Each route file MUST export
  // `Route = createFileRoute(...)` AND co-locate its component (canonical pattern
  // from TanStack Router official examples). Extracting components to separate
  // files just to satisfy the rule fragments routing logic without real HMR gain
  // — the route file would still be re-evaluated whole on edit. Disable the rule
  // for `routes/**` only; everywhere else (components/, features/) the rule still
  // enforces proper Fast Refresh boundaries.
  {
    files: ['src/routes/**/*.tsx'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
  prettier,
);
