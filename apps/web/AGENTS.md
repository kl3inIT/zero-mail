<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.

<!-- END:nextjs-agent-rules -->

## shadcn/ui Primitive Rule

Before building or refactoring UI, check whether shadcn/ui already provides the needed primitive. If it exists and is not already in `components/ui`, install it with `pnpm dlx shadcn@latest add <component>` from `apps/web`, then import from `@/components/ui/*` and compose project-specific components around it.

`components/ui/**` is copied shadcn primitive source and is ignored by ESLint and Prettier. Avoid hand-rolling primitives that shadcn already provides.
