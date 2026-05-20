# syntax=docker/dockerfile:1.7
# Build context: repo root.
# Build: docker build -f apps/admin/Dockerfile -t zeromail-admin .
#
# 2026 best practice:
#   - multi-stage (deps / builder / runner)
#   - node:24-alpine builder (matches root engines >=24)
#   - nginx:alpine runner serving the built Vite SPA as static assets
#   - non-root runtime user (uid 1654)
#   - BuildKit cache mount for the pnpm store
#   - SPA fallback (any unknown path → index.html) so TanStack Router deep-links work
#   - Healthcheck via curl
#   - OCI labels

# ============================================================================
# STAGE 1: base — Node + pnpm
# ============================================================================
FROM node:24-alpine AS base

RUN corepack enable && corepack prepare pnpm@11.0.8 --activate

# ============================================================================
# STAGE 2: deps — install workspace dependencies (cached until lockfile changes)
# ============================================================================
FROM base AS deps

WORKDIR /app

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/admin/package.json apps/admin/package.json

RUN --mount=type=cache,id=pnpm-store-admin,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @zeromail/admin...

# ============================================================================
# STAGE 3: builder — compile the Vite SPA
# ============================================================================
FROM base AS builder

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/apps/admin/node_modules ./apps/admin/node_modules

# Workspace source — apps/admin + root configs (tsconfig, openapi schema if shared).
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/admin ./apps/admin

# Bake the API base URL at build time. Override in compose when needed.
ARG VITE_API_BASE=https://zeromail.com
ENV VITE_API_BASE=${VITE_API_BASE}

RUN pnpm --filter @zeromail/admin run build

# ============================================================================
# STAGE 4: runner — minimal nginx serving the static SPA
# ============================================================================
FROM nginx:1.30-alpine AS runner

RUN apk add --no-cache tini curl && \
    rm -rf /var/cache/apk/*

# Create non-root user. nginx:alpine already creates the "nginx" user (uid 101);
# we add our own to keep parity with the rest of the stack (uid 1654).
RUN addgroup -g 1654 -S appgroup && \
    adduser  -u 1654 -S appuser -G appgroup

WORKDIR /usr/share/nginx/html

# Replace default nginx config with a SPA-friendly one (history-mode fallback).
RUN rm -f /etc/nginx/conf.d/default.conf
COPY <<'NGINX_CONF' /etc/nginx/conf.d/admin.conf
# Hardened nginx config for the ZeroMail admin Vite SPA.
# Security headers per OWASP 2026 secure-headers cheat-sheet.

# Map content-type → cache policy.
map $sent_http_content_type $cache_control {
    default                              "no-store";
    "text/html"                          "no-store";
    "application/json"                   "no-store";
    "~^(font|image|video|audio)/"        "public, max-age=31536000, immutable";
    "~^application/(javascript|wasm)"    "public, max-age=31536000, immutable";
    "~^text/(css|javascript)"            "public, max-age=31536000, immutable";
}

server {
    listen       5174;
    server_name  _;

    root   /usr/share/nginx/html;
    index  index.html;

    # ─── Security headers (defense in depth — NPM/upstream may add HSTS) ─────
    add_header X-Content-Type-Options    "nosniff"          always;
    add_header X-Frame-Options           "DENY"             always;
    add_header Referrer-Policy           "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy        "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()" always;
    add_header Cross-Origin-Opener-Policy "same-origin"     always;
    add_header Cross-Origin-Resource-Policy "same-site"     always;
    # CSP: SPA only fetches same-origin code; allow inline styles for dev-debug;
    # allow connect to api host. Tighten as the admin app stabilizes.
    add_header Content-Security-Policy   "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self' https://zeromail.com https://*.zeromail.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
    add_header Cache-Control             $cache_control     always;

    # ─── Compression ────────────────────────────────────────────────────────
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript application/xml+rss application/xml text/javascript image/svg+xml font/ttf font/otf application/wasm;

    # ─── Hashed assets — long cache, immutable. ─────────────────────────────
    location /assets/ {
        access_log off;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        try_files $uri =404;
    }

    # ─── Service worker / manifest / index.html — never cache. ──────────────
    location = /sw.js                    { add_header Cache-Control "no-store" always; try_files $uri =404; }
    location = /manifest.webmanifest     { add_header Cache-Control "no-cache" always; try_files $uri =404; }
    location = /index.html               { add_header Cache-Control "no-store" always; try_files $uri =404; }

    # ─── Healthcheck. ───────────────────────────────────────────────────────
    location = /healthz {
        access_log off;
        return 200 "ok\n";
        add_header Content-Type text/plain;
    }

    # ─── SPA fallback. ──────────────────────────────────────────────────────
    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINX_CONF

# nginx default writes pid + cache + log under /var. Allow our non-root user
# to write those without escalating.
RUN mkdir -p /var/cache/nginx /var/run /var/log/nginx /tmp/nginx-client-body && \
    chown -R 1654:1654 /var/cache/nginx /var/run /var/log/nginx /tmp/nginx-client-body /usr/share/nginx/html && \
    touch /var/run/nginx.pid && chown 1654:1654 /var/run/nginx.pid

# Make nginx listen on an unprivileged port (5174) and use our writable dirs.
RUN sed -i \
    -e 's|user  nginx;|user  appuser;|' \
    -e 's|pid        /var/run/nginx.pid;|pid /var/run/nginx.pid;|' \
    -e 's|client_body_temp_path|client_body_temp_path /tmp/nginx-client-body; # |' \
    /etc/nginx/nginx.conf || true

# Copy the built SPA from the builder stage.
COPY --from=builder --chown=1654:1654 /app/apps/admin/dist /usr/share/nginx/html

USER 1654:1654

EXPOSE 5174

LABEL org.opencontainers.image.title="ZeroMail Admin Console" \
      org.opencontainers.image.description="ZeroMail admin SPA — Vite 8 + React 19 + TanStack Router (WebAuthn admin auth)" \
      org.opencontainers.image.source="https://github.com/kl3inIT/zero-mail" \
      maintainer="dathip04@gmail.com"

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -fsS http://localhost:5174/healthz || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["nginx", "-g", "daemon off;"]
