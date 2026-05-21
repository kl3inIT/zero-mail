---
title: Zero Mail CI/CD + Ops Runbook
status: active
last-updated: 2026-05-20
---

# Zero Mail CI/CD + Ops Runbook

End-to-end runbook for the GitHub Actions → GHCR → VPS deploy pipeline that
serves the `prod` slot on the Hostinger VPS (`72.62.193.33`, 8 GB RAM).

## 1. Architecture

```
   Devs (×3) local                            VPS (72.62.193.33, 8 GB RAM)
   ────────────────                           ──────────────────────────────
   IntelliJ + local Redis                     zeromail-postgres
   SSH tunnel :5555 ──────────────────────► ├─ zeromail        (PROD)
                                              └─ zeromail_dev    (SHARED DEV — 3-team)
                                              zeromail-redis     (PROD only)
                                              zeromail-api
                                              zeromail-worker
                                              zeromail-frontend
                                              zeromail-nginx-proxy-manager
                                              zeromail-9router

   GitHub Actions                             Prod deploy via SSH
   ─────────────                              ──────────────────
   PR / push main  ─▶ CI + Security gates
   push main       ─▶ Build and Push (api, worker, frontend → GHCR)
   push v*.*.*-rc* ─▶ Release gates (golden-path + 50-tenant loadtest)
   push v*.*.*     ─▶ Build and Push (+ :prod tag)
                    └▶ Deploy Prod (manual approval via Environment "prod")
```

No dev slot on the VPS — 8 GB RAM is insufficient for two parallel stacks.
Dev work happens on local laptops against the shared `zeromail_dev` DB; see
`docs/ops/DEV-ENV.md`.

### NPM routing (admin SPA moved off the Next.js container)

Phase 8 originally routed `admin.zeromail.vn` → `zeromail-frontend:3000` because
the admin app was still a placeholder route inside the Next.js project. With the
admin SPA now living in its own container (`zeromail-admin:5174` serving Vite
static output via nginx), update NPM proxy hosts:

| Host | Forward host | Forward port | Notes |
|------|--------------|--------------|-------|
| `zeromail.vn`             | `zeromail-frontend` | `3000` | Unchanged (Next.js SSR). |
| `zeromail.vn/api`         | `zeromail-api`      | `8080` | Unchanged. |
| `admin.zeromail.vn`       | `zeromail-admin`    | `5174` | **Changed** — was `zeromail-frontend:3000`. |
| `admin.zeromail.vn/api/admin` | `zeromail-api`  | `8080` | Unchanged. |

Update once via the NPM UI (`http://127.0.0.1:81` via SSH tunnel — see
`v1.2-deploy.md` §2).

## 2. Branching strategy (3-person team)

- **Trunk-based, short-lived branches**: `feat/<topic>`, `fix/<topic>` →
  PR → `main`.
- **`main` is always deployable.** Merge implies green CI + security + 1 review.
- **Release**: cut `vX.Y.Z-rc.N` for release candidates (runs `release.yml`:
  golden-path E2E + 50-tenant loadtest), then promote to `vX.Y.Z` (no `-rc`)
  which becomes prod-eligible.
- **Prod deploy**: manual `workflow_dispatch` on `Deploy Prod`, choose tag,
  one reviewer approves the `prod` environment in the GitHub UI.

## 3. Why `ci.yml` only delegates to `gates.yml`

This is **the recommended 2026 GitHub Actions pattern** (DRY workflows):

- `gates.yml` uses `on: workflow_call` → reusable workflow library
- `ci.yml` = trigger entry (PR + push main, owns concurrency)
- `release.yml` calls the SAME `gates.yml` on tag push, no duplicated jobs
- One source of truth for "what CI runs" → no drift between PR and release.

## 4. Workflows

| File | Trigger | Purpose |
|------|---------|---------|
| `ci.yml` | PR + push main | thin entry → calls `gates.yml` |
| `gates.yml` | `workflow_call` only | backend (gradle check + JaCoCo), frontend (lint, typecheck, test+coverage, build), Playwright E2E |
| `security.yml` | PR + push main + weekly Mon 03:00 UTC | CodeQL (java + ts) + Gitleaks + Trivy fs + SBOM/OSV + Trivy image on main |
| `i18n-check.yml` | PR + push (touching i18n paths) | key coverage gate |
| `release.yml` | `v*.*.*-rc*` tag | gates + golden-path + 50-tenant loadtest |
| `build-and-push.yml` | push main + tag `v*.*.*` | builds + pushes api/worker/frontend to GHCR |
| `deploy-prod.yml` | `workflow_dispatch` with `version` input | manual prod deploy with required env approval |

## 5. Environments + secrets

```sh
gh api repos/kl3inIT/zero-mail/environments --jq '.environments[].name'
# prod  (5-min wait timer; add reviewers via Settings → Environments)

gh secret list --repo kl3inIT/zero-mail --env prod
```

| Secret | Source |
|--------|--------|
| `VPS_HOST` | `72.62.193.33` |
| `VPS_USER` | `dat` |
| `VPS_SSH_PORT` | `22` |
| `VPS_SSH_KEY` | ed25519 private key (`zeromail-deploy@github-actions`); pub in `dat@VPS:~/.ssh/authorized_keys` |

App env vars (DB password, OAuth client, LLM keys) live on the VPS in
`/apps/zero-mail/.env` — not in GitHub. Edit on the server when rotating.

## 6. Image tags published to GHCR

`ghcr.io/kl3init/zero-mail-{api,worker,frontend,admin}`:

- **api / worker** — Java 25 Spring Boot, built from `backend/{api,worker}/Dockerfile` (multi-stage Gradle bootJar → eclipse-temurin:25-jre-noble)
- **frontend** — Next.js 16 standalone, built from `apps/web/Dockerfile` (node:24-alpine + libc6-compat)
- **admin** — Vite 8 SPA → static, built from `apps/admin/Dockerfile` (node:24-alpine builder → nginx:1.27-alpine runner with hardened SPA config + OWASP security headers)

| Tag | Pushed on | Used by |
|-----|-----------|---------|
| `sha-<short>` | every push main + every tag | trivy image scan, full provenance |
| `dev` | push main | floating "latest main" for ad-hoc dev pulls |
| `vX.Y.Z` | release tag | `deploy-prod.yml` (immutable) |
| `prod` | release tag | manual rollback alias |

## 7. Deploy operations

### 7.1 Cut a release candidate

```sh
git checkout main && git pull
git tag -a v1.3.0-rc.1 -m "RC 1.3.0"
git push origin v1.3.0-rc.1
```

Triggers `release.yml`. Verify:

```sh
gh run list --workflow release.yml --branch v1.3.0-rc.1
```

### 7.2 Promote to prod

```sh
git tag -a v1.3.0 -m "Release 1.3.0"
git push origin v1.3.0
# wait for build-and-push to finish:
gh run watch --workflow "Build and Push Images"

gh workflow run deploy-prod.yml --ref main -f version=v1.3.0
```

One of the three team members approves the `prod` environment when GitHub
emails them. Deploy completes in ~3 minutes.

### 7.3 Manual prod rollback

```sh
gh workflow run deploy-prod.yml --ref main -f version=v1.2.9   # previous good tag
```

Approve, deploy completes. Workflow pulls `ghcr.io/kl3init/zero-mail-*:v1.2.9`
(immutable). For an instant on-VPS rollback before the workflow finishes:

```sh
ssh dat@72.62.193.33
cd /apps/zero-mail
cat .last-deploy.txt   # snapshot written by every Deploy Prod run
```

## 8. VPS operational tasks (manual)

### 8.1 Bootstrap dev DB on the VPS

Already done 2026-05-20 by `scripts/ops/postgres/init/10-create-dev-db.sql`. To rotate
the shared dev password:

```sh
ssh dat@72.62.193.33
NEW_PW="$(openssl rand -base64 24 | tr -d '/+=' | head -c 28)"
docker exec -i -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d postgres -v ON_ERROR_STOP=1 \
  -v dev_password="$NEW_PW" \
  < /apps/zero-mail/scripts/ops/postgres/init/10-create-dev-db.sql > /dev/null
echo "$NEW_PW"   # share via 1Password / pinned chat; then clear shell history
```

### 8.2 Postgres backup automation

Install once:

```sh
ssh dat@72.62.193.33
sudo mkdir -p /etc/zeromail /var/backups/zeromail
sudo bash -c 'openssl rand -base64 48 > /etc/zeromail/backup.passphrase'
sudo chmod 400 /etc/zeromail/backup.passphrase
sudo chown root:root /etc/zeromail/backup.passphrase

# Write the passphrase to your secret store (1Password) BEFORE proceeding.
sudo cat /etc/zeromail/backup.passphrase     # capture once
```

Cron entry (run as root so it can read the passphrase):

```sh
sudo crontab -e
# 5 3 * * *  /apps/zero-mail/scripts/ops/backup/pg-backup.sh >> /var/log/zeromail-backup.log 2>&1
```

Daily 03:05 UTC: dumps both `zeromail` + `zeromail_dev`, encrypts with GPG/AES-256,
keeps 7 daily + 4 weekly + 6 monthly snapshots in `/var/backups/zeromail/`.

Off-host upload (optional, recommended): install rclone, configure a remote
(`r2:zeromail-backups` or `b2:zeromail-backups`), then set the cron line:

```
5 3 * * *  RCLONE_REMOTE=r2:zeromail-backups /apps/zero-mail/scripts/ops/backup/pg-backup.sh >> /var/log/zeromail-backup.log 2>&1
```

Recovery verification (do this monthly):

```sh
ssh dat@72.62.193.33
LATEST=$(ls -1t /var/backups/zeromail/daily/*/zeromail-*.dump.gpg | head -1)
gpg --batch --passphrase-file /etc/zeromail/backup.passphrase --decrypt "$LATEST" > /tmp/restore.dump
docker exec -i -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d postgres -c "CREATE DATABASE zeromail_restore_test;"
docker exec -i -e PGPASSWORD=zeromail zeromail-postgres \
  pg_restore -U zeromail -d zeromail_restore_test -c < /tmp/restore.dump
docker exec -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d zeromail_restore_test -c "\dt" | head -20
docker exec -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d postgres -c "DROP DATABASE zeromail_restore_test;"
shred -u /tmp/restore.dump
```

### 8.3 Activate pg_stat_statements

The extension was created in both DBs on 2026-05-20. Activate the stats
collector by restarting Postgres with `shared_preload_libraries` (configured
in `docker-compose.yml`):

```sh
ssh dat@72.62.193.33
cd /apps/zero-mail
docker compose up -d postgres            # re-creates with the new command flags
docker exec -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d zeromail -c "SELECT count(*) FROM pg_stat_statements;"
```

Now `Postgres MCP Pro` tools (`analyze_query_indexes`, `get_top_queries`,
`analyze_workload_indexes`) can return results.

`log_min_duration_statement=500` is now active too — any query slower than
500 ms goes to `docker logs zeromail-postgres`. Log rotation caps that
collection at 30 MB (json-file 10m × 3).

### 8.4 Docker log rotation (already applied)

All services use the `default-logging` YAML anchor in `docker-compose.yml`:
3 × 10 MB rotated json-files = ~30 MB per service maximum. To verify:

```sh
docker inspect zeromail-api --format '{{json .HostConfig.LogConfig}}'
# {"Type":"json-file","Config":{"max-file":"3","max-size":"10m","compress":"true"}}
```

## 9. Code quality artifacts

- **JaCoCo** (backend) — XML uploaded as `jacoco-reports` from `gates.yml`.
  Open HTML at `backend/<module>/build/reports/jacoco/test/html/index.html`.
- **Vitest --coverage** (frontend) — lcov + summary uploaded as `vitest-coverage`.
- **CycloneDX SBOM** — generated by `security.yml`, uploaded as `sbom`.

Thresholds are not enforced yet (baseline still establishing); add a
`jacocoTestCoverageVerification` rule once each module hits its target.

## 10. Security scanning summary

| Job | Severity gate | Runs on |
|-----|---------------|---------|
| CodeQL java-kotlin / javascript-typescript | high+ (default + extended + quality) | PR + push main + weekly |
| Gitleaks | any leak | PR + push main + weekly |
| Trivy filesystem | HIGH, CRITICAL | PR + push main + weekly |
| Trivy image (api/worker/frontend) | CRITICAL | push main only (after images publish) |
| CycloneDX + OSV-Scanner | any CVE | PR + push main + weekly |

GitHub native secret scanning + push protection should be enabled in repo
Settings → Code security and analysis (UI only, not in YAML).

## 11. Rotation policy

| Secret | Cadence |
|--------|---------|
| `REFRESH_TOKEN_KEY_BASE64` | **never** (rotation invalidates every stored Gmail refresh token) |
| `ZEROMAIL_ADMIN_AUDIT_HMAC_KEK_BASE64` | every 6 months |
| Shared dev DB password (`zeromail_dev`) | every 3 months, or immediately after team member offboarding |
| `VPS_SSH_KEY` (deploy bot) | every 12 months, or immediately after operator turnover |
| Backup GPG passphrase | every 12 months (rotate by re-encrypting last good backup with the new key) |
| `GOOGLE_OAUTH_CLIENT_SECRET` | when GCP recommends; minimum every 12 months |

## 12. Quick reference

| What | Where |
|------|-------|
| VPS shell | `ssh dat@72.62.193.33` |
| Prod compose | `/apps/zero-mail/docker-compose.yml` |
| Prod env file | `/apps/zero-mail/.env` |
| Backup dir | `/var/backups/zeromail/` |
| Postgres logs | `docker logs zeromail-postgres` |
| Backup log | `/var/log/zeromail-backup.log` |
| Dev DB tunnel | `ssh -fN -L 5555:zeromail-postgres:5432 dat@72.62.193.33` |
| GHCR images | https://github.com/kl3inIT?tab=packages |
| GitHub envs | https://github.com/kl3inIT/zero-mail/settings/environments |
