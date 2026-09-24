# Zero Mail

AI email assistant that helps you reach inbox zero in Gmail. Zero Mail
auto-triages incoming mail, categorizes and archives it, and drafts replies
based on rules you write in plain language.

[![CI](https://github.com/kl3inIT/zero-mail/actions/workflows/ci.yml/badge.svg)](https://github.com/kl3inIT/zero-mail/actions/workflows/ci.yml)
[![Security](https://github.com/kl3inIT/zero-mail/actions/workflows/security.yml/badge.svg)](https://github.com/kl3inIT/zero-mail/actions/workflows/security.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Features

- **Natural-language rules**: describe what should happen ("archive newsletters
  I never open", "label invoices and forward to accounting") and Zero Mail turns
  it into structured `When / Then` rules you can review and edit.
- **AI triage**: new Gmail messages arrive via Google Pub/Sub push and are
  classified and acted on in near real time.
- **Reply drafting**: drafts responses in your voice and leaves them in Gmail
  for you to send; nothing destructive happens without a rule you enabled.
- **Chat assistant**: ask questions about your inbox and manage rules from a
  streaming chat UI.
- **Bring your own key**: routes LLM calls through OpenRouter by default, with
  BYOK support for OpenAI-compatible providers.
- **Multi-tenant SaaS**: billing, admin console, observability, and
  per-tenant isolation built in.

## Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Java 25, Spring Boot 4, Spring AI 2.0, Spring Modulith, Gradle (Kotlin DSL) |
| Data | PostgreSQL, Redis, Liquibase |
| Web app | Next.js 16 (App Router), React 19, TanStack Query, shadcn/ui, Tailwind CSS 4 |
| Admin app | Vite, React |
| Mail | Gmail API + Google Cloud Pub/Sub |
| Ops | Docker Compose, OpenTelemetry, Grafana / Loki / Tempo / Prometheus |

## Repository layout

```
backend/
  core/     domain logic, persistence, AI orchestration (Spring Modulith modules)
  api/      HTTP API, auth, OAuth, webhooks
  worker/   background jobs: Gmail sync, triage, scheduled work
apps/
  web/      user-facing Next.js app
  admin/    internal admin console
docker/     compose files for infra, app stack, and observability
docs/       architecture notes and ops runbooks
```

## Getting started

### Prerequisites

- JDK 25 (Temurin)
- Node.js 24+ and pnpm 11 (`corepack enable`)
- Docker (for PostgreSQL and Redis)
- A Google Cloud project with an OAuth client and the Gmail API enabled
- An OpenRouter (or OpenAI-compatible) API key

### Run locally

```sh
git clone https://github.com/kl3inIT/zero-mail.git
cd zero-mail

# 1. Configure environment
cp .env.example .env.local   # fill in DB, Redis, Google OAuth, and LLM keys

# 2. Start PostgreSQL and Redis
docker compose -f docker/docker-compose.infra.yml up -d postgres redis

# 3. Start the backend (API and worker, in separate terminals)
./gradlew :backend:api:bootRun
./gradlew :backend:worker:bootRun

# 4. Start the frontend
pnpm install
pnpm web:dev                 # http://localhost:3000
```

`.env.example` documents every variable. See
[`docs/ops/DEV-ENV.md`](docs/ops/DEV-ENV.md) for the full team setup and
[`docs/ops/CICD-RUNBOOK.md`](docs/ops/CICD-RUNBOOK.md) for deployment.

### Tests and checks

```sh
./gradlew check     # backend tests, architecture tests, Spotless formatting
pnpm check          # frontend typecheck, lint, i18n checks
pnpm test           # frontend unit tests
pnpm e2e            # Playwright end-to-end tests
```

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md),
[CONVENTIONS.md](CONVENTIONS.md), and [TESTING.md](TESTING.md) before opening
a pull request, and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

Found a security issue? Please report it privately; see [SECURITY.md](SECURITY.md).

## Acknowledgements

Zero Mail's product direction is inspired by
[Inbox Zero](https://github.com/elie222/inbox-zero). Zero Mail is an
independent implementation on a Java / Spring stack.

## License

[MIT](LICENSE) © kl3inIT and Zero Mail contributors
