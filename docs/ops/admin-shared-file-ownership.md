---
title: Admin Shared File Ownership
phase: 08-admin-console-operator-tooling
plan: 8A
---

# Admin Shared File Ownership

Phase 8 plans share several files that cross admin-console, user-app, and
runtime boundaries. Before editing any row below, the executor must read the
owner note, keep the listed invariant intact, and run the verification command.

| File | Primary owner | Other Phase 8 plans | Invariant | Verification |
|---|---|---|---|---|
| `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` | 8A admin auth | 8B master keys, 8C tenant inspection | Admin chain stays `@Order(1)`, user chain stays `@Order(2)`, and `.oauth2Login()` never appears in admin chain. | `./gradlew :backend:api:test --tests "*Admin*"` |
| `backend/core/src/main/java/com/zeromail/core/llm/gateway/ChatModelCacheEvictionListener.java` | 8B master keys | 8D curated catalog, 9 settings | Cache eviction may react to admin changes, but it must not log prompt text, provider keys, or Gmail content. | `rg "prompt|completion|apiKey|bodyText" backend/core/src/main/java/com/zeromail/core/llm/gateway/ChatModelCacheEvictionListener.java` |
| `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase owner for the current plan | 8B, 8D, 8E | Changelog includes are append-only and keep numbering offsets: 8A=048-057, 8B=058-067, 8D=068-077, 8E=078+. | `rg "changes/0(48|49|50|58|68|78)" backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` |
| `apps/admin/src/routes/__root.tsx` | 8A admin SPA shell | 8B-8F admin pages | Root route owns global providers only; feature pages must stay in child routes and preserve the ADMIN MODE banner in authenticated layout. | `pnpm --filter @zeromail/admin build` |
| `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java` | 8A OpenAPI split | 8B master-key API, 8D catalog API, 8E queue API | `/v3/api-docs/public` excludes `/api/admin/**`; `/v3/api-docs/admin` includes only `/api/admin/**`. | `./gradlew :backend:api:test --tests "*Admin*"` |
| `backend/core/src/main/resources/application.yml` | Core shared defaults | 8B-8F admin services | Admin secrets are referenced by property name only; no default production key, token, or credential material belongs in this file. | `rg "sk-|AIza|password: .+|secret: .+" backend/core/src/main/resources/application.yml` |

If a future plan must change an invariant, it must update this table and record
the decision in that plan summary.

