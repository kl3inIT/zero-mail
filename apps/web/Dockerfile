# syntax=docker/dockerfile:1.7
# Build context: repo root
# docker build -f apps/web/Dockerfile -t zeromail-frontend .

# ============================================================================
# STAGE 1: base — Node + pnpm
# ============================================================================
FROM node:24-alpine AS base

# libc6-compat shims glibc so prebuilt musl-incompatible native modules (sharp,
# better-sqlite3, etc.) keep working on alpine. Next.js image-optimization
# pulls sharp transitively; without this, runtime falls back silently.
RUN apk add --no-cache libc6-compat

RUN corepack enable && corepack prepare pnpm@11.0.8 --activate

# ============================================================================
# STAGE 2: deps — install dependencies (cached until lockfile changes)
# ============================================================================
FROM base AS deps

WORKDIR /app

# Copy workspace manifests only — layer cache busts only when these change
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json                            apps/web/package.json

RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

# ============================================================================
# STAGE 3: builder — compile Next.js app
# ============================================================================
FROM base AS builder

WORKDIR /app

COPY --from=deps /app/node_modules     ./node_modules
COPY --from=deps /app/apps/web/node_modules ./apps/web/node_modules

# Copy full source (workspace root + web app)
COPY . .

ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production

# pnpm 11's runDepsStatusCheck detects the workspace project count mismatch
# between the deps stage (web only) and builder stage (web + admin once
# `COPY . .` lands) and tries to purge node_modules. Without a TTY that purge
# aborts (ERR_PNPM_ABORTED_REMOVE_MODULES_DIR_NO_TTY). Setting CI=true is the
# pnpm-documented opt-out for non-interactive environments.
ENV CI=true

# NEXT_PUBLIC_* are baked in at build time
ARG NEXT_PUBLIC_API_BASE=https://zeromail.vn
ENV NEXT_PUBLIC_API_BASE=${NEXT_PUBLIC_API_BASE}

RUN pnpm --filter web run build

# ============================================================================
# STAGE 4: runner — minimal production image
# ============================================================================
FROM node:24-alpine AS runner

# Patch the image scan surface: upgrade libxml2 (CVE-2026-6732, DoS via crafted
# XSD; base Alpine still ships 2.13.9-r0) and refresh the system npm so bundled
# node_modules (brace-expansion, picomatch, ip-address) stop tripping Trivy.
# Next.js standalone invokes neither at runtime — this is scan-surface only.
RUN apk add --no-cache tini curl && \
    apk upgrade --no-cache libxml2 && \
    rm -rf /var/cache/apk/* && \
    npm install -g npm@latest && \
    npm cache clean --force

RUN addgroup -g 1654 -S appgroup && \
    adduser  -u 1654 -S appuser -G appgroup

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# standalone output contains everything needed to run
COPY --from=builder --chown=1654:1654 /app/apps/web/.next/standalone ./
COPY --from=builder --chown=1654:1654 /app/apps/web/.next/static     ./apps/web/.next/static
COPY --from=builder --chown=1654:1654 /app/apps/web/public           ./apps/web/public
# Legal/docs MDX is read from disk at runtime by lib/docs/loader.ts. The
# Turbopack production build can't trace the dynamic `${slug}.${locale}.mdx`
# path, so copy the bundle explicitly to the cwd-relative location the loader
# probes first (server runs `node apps/web/server.js` from /app → /app/apps/web/docs).
COPY --from=builder --chown=1654:1654 /app/apps/web/docs             ./apps/web/docs

USER 1654:1654

EXPOSE 3000

LABEL org.opencontainers.image.title="ZeroMail Frontend" \
      org.opencontainers.image.description="ZeroMail Next.js 16 frontend" \
      org.opencontainers.image.source="https://github.com/kl3inIT/zero-mail" \
      maintainer="dathip04@gmail.com"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:3000/ || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "apps/web/server.js"]
