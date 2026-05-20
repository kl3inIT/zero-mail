---
title: Zero Mail Local Dev Setup
status: active
last-updated: 2026-05-20
audience: 3-person engineering team
---

# Local Dev Setup

This is the onboarding runbook for engineers working on Zero Mail. The team uses
**local development** with a **shared dev database** hosted on the production
VPS — local code, shared data.

## Architecture

```
   Your laptop                              Hostinger VPS (72.62.193.33)
   ─────────────                            ─────────────────────────────
   IntelliJ ZeroMailApi          ┌────────► zeromail-postgres
   IntelliJ ZeroMailWorker  ─SSH─┤           ├─ zeromail        (PROD,  role zeromail)
   Local Postgres (offline)      │           └─ zeromail_dev    (DEV,   role zeromail_dev)
   Local Redis (always)          │
                                 └──────────► zeromail-redis    (PROD only — devs DO NOT share)
```

- **Shared dev DB** lives on the VPS Postgres container. The `zeromail_dev` role
  cannot read or write the prod `zeromail` database (verified by ArchUnit /
  Postgres role checks).
- **Local Redis** is per-developer to avoid session / idempotency races.
- **Local OAuth + LLM + Pub/Sub** — each dev uses their own dev-only credentials.
- **No dev slot on the VPS** (VPS has 8 GB RAM; we cannot sustain a second
  dev stack alongside prod). The shared DB is the only dev resource on the VPS.

## 1. Prerequisites

| Tool | Min version |
|------|-------------|
| JDK 25 (Temurin) | 25 |
| Docker / Docker Desktop | 27+ |
| pnpm | 11.0.8 (pinned in package.json) |
| Node | LTS (≥20.9) |
| `gh` CLI | 2.45+ (optional, for cloning + secret read access) |

```sh
# macOS
brew install temurin@25 docker pnpm node gh

# Ubuntu
sudo apt install -y docker.io docker-compose-plugin
curl -fsSL https://get.pnpm.io/install.sh | sh -
# install JDK 25 via sdkman or asdf
```

## 2. Get SSH access to the dev DB

Each dev needs their `id_ed25519.pub` added to `dat@72.62.193.33:~/.ssh/authorized_keys`.

**Operator (Dat)** — for each new team member:

```sh
ssh dat@72.62.193.33
# paste the dev's ed25519 pubkey into ~/.ssh/authorized_keys
echo "ssh-ed25519 AAAA... <dev-name>@<their-machine>" >> ~/.ssh/authorized_keys
```

**Each dev** — verify the connection works:

```sh
ssh -T dat@72.62.193.33
# Expected: PTY allocated and you land in the VPS shell.
```

## 3. Open the SSH tunnel to the shared dev DB

The dev DB Postgres container is **not exposed** to the public internet —
the only way in is through SSH on the VPS.

### Background tunnel (recommended)

```sh
ssh -fN -L 5555:zeromail-postgres:5432 dat@72.62.193.33
```

- `-f`: background after authentication.
- `-N`: don't open a remote shell — pure port-forward.
- `5555` on your laptop ⇄ `zeromail-postgres:5432` in the VPS docker network.

Verify:

```sh
psql -h localhost -p 5555 -U zeromail_dev -d zeromail_dev
# password prompt: paste the shared dev password from the operator
```

### Keep the tunnel up automatically

Add to `~/.ssh/config`:

```sshconfig
Host zeromail-db-tunnel
    HostName 72.62.193.33
    User dat
    LocalForward 5555 zeromail-postgres:5432
    ServerAliveInterval 30
    ExitOnForwardFailure yes
```

Then start it with `ssh -fN zeromail-db-tunnel` (or use [autossh](https://www.harding.motd.ca/autossh/)
for crash-resilient tunnels: `autossh -M 0 -fN zeromail-db-tunnel`).

### Get the shared dev password

The operator stores the current shared dev DB password in the team's
out-of-band secret store (1Password / Bitwarden / pinned chat). Ask the
operator the first time — never commit it.

To rotate (operator only):

```sh
ssh dat@72.62.193.33
cd /apps/zero-mail
NEW_PW="$(openssl rand -base64 24 | tr -d '/+=' | head -c 28)"
docker exec -i -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d postgres -v ON_ERROR_STOP=1 \
  -v dev_password="$NEW_PW" \
  < ops/postgres/init/10-create-dev-db.sql > /dev/null
echo "$NEW_PW"   # share via the secret store; clear shell history after
```

## 4. Configure `.env.local`

```sh
cd zero-mail
cp .env.example .env.local
```

Then fill in:

| Field | How to get it |
|-------|---------------|
| `DB_PASSWORD` | Shared dev pw from operator (see §3). |
| `GOOGLE_OAUTH_CLIENT_ID` / `_SECRET` | Operator shares the team dev OAuth client. Add `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI if missing. |
| `REFRESH_TOKEN_KEY_BASE64` | `openssl rand -base64 32` — per-dev, never share. |
| `LOCAL_REFRESH_TOKEN_KEY_BASE64` | Mirror `REFRESH_TOKEN_KEY_BASE64`. |
| `ZEROMAIL_LLM_PLATFORM_API_KEY` | Your own OpenRouter key with $5–10 cap. |
| `PUBSUB_PUSH_AUDIENCE_URL` | Run `pnpm tailscale:funnel:api`, paste the `*.ts.net/internal/pubsub/gmail` URL. |
| `GOOGLE_PUBSUB_TOPIC_NAME` | Your own dev Pub/Sub subscription topic. |
| `ZEROMAIL_ADMIN_BOOTSTRAP_EMAILS` | Your gmail (you'll receive the enrollment URL on boot). |
| `ZEROMAIL_ADMIN_AUDIT_HMAC_KEK_BASE64` | `openssl rand -base64 32` — per-dev. |

The remaining fields have sane localhost defaults.

## 5. First boot

```sh
# 1. Bring up local Redis (and optional local Postgres if you skip the tunnel).
docker compose up -d redis
docker compose up -d postgres    # optional, only if you want a local DB instead of the tunnel

# 2. Start the SSH tunnel to the shared dev DB.
ssh -fN zeromail-db-tunnel        # uses the ~/.ssh/config entry from §3

# 3. Run the worker once — it runs all Liquibase migrations against zeromail_dev.
#    IntelliJ: run the "ZeroMailWorker" run configuration.
#    CLI:      ./gradlew :backend:worker:bootRun

# 4. Run the API.
#    IntelliJ: run "ZeroMailApi" with profile=dev.
#    CLI:      ./gradlew :backend:api:bootRun

# 5. Run the frontend.
pnpm install
pnpm web:dev
```

On first API boot you'll see in the console (because you set yourself as a
bootstrap admin):

```
[ZeroMail Admin Bootstrap] you@example.com enrollment URL:
   http://localhost:8080/api/admin/enrollment?token=<32hex> (valid 10 minutes)
```

Open that URL in the frontend (`http://localhost:5174/enroll?token=...`) to set
up your WebAuthn passkey for the admin console.

## 6. Working with the shared dev DB

Connect with `psql` for ad-hoc queries:

```sh
psql -h localhost -p 5555 -U zeromail_dev -d zeromail_dev
```

Or in IntelliJ Database tool:

- Type: PostgreSQL
- Host: `localhost`
- Port: `5555`
- Database: `zeromail_dev`
- User: `zeromail_dev`
- Password: shared dev pw

### Conventions

- **No DROP TABLE / TRUNCATE without coordination.** All three of us are
  reading + writing this DB. If you need a clean slate, drop your tenant rows
  by tenant ID rather than nuking tables.
- **Migrations:** every Liquibase changeset you author runs against this DB
  whenever any teammate boots the worker. Coordinate destructive changesets
  (column drop, NOT NULL backfill) in PR review before merging.
- **Seed data:** the dev DB is intentionally not auto-seeded. Use
  `loadtest/scripts/seed-tenants.sql` against `zeromail_dev` if you want
  representative load.

### Reset to a clean state (operator)

```sh
ssh dat@72.62.193.33
docker exec -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d postgres -c "DROP DATABASE zeromail_dev"
docker exec -e PGPASSWORD=zeromail zeromail-postgres \
  psql -U zeromail -d postgres -v dev_password="$NEW_PW" \
  -f /docker-entrypoint-initdb.d/10-create-dev-db.sql
# Now any teammate's worker boot re-runs Liquibase from a fresh DB.
```

## 7. Going fully local (offline mode)

If you're working offline or want to test destructive migrations:

```sh
# In .env.local, swap the tunnel pointers for the local compose stack:
DB_URL=jdbc:postgresql://localhost:15432/zeromail
DB_USER=zeromail
DB_PASSWORD=zeromail

# Then:
docker compose up -d postgres redis
./gradlew :backend:worker:bootRun    # applies migrations to your local DB
```

## 8. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Connection refused` on `localhost:5555` | tunnel down | `ssh -fN zeromail-db-tunnel` or check `pgrep -af 5555` |
| `password authentication failed for "zeromail_dev"` | password rotated | ask operator for the new pw |
| `relation … does not exist` | Liquibase changeset missing on the dev DB | someone needs to boot the worker against `zeromail_dev` to apply the changeset |
| `Permission denied (publickey)` to VPS | SSH pub key not added | operator: `echo "<your pubkey>" >> /home/dat/.ssh/authorized_keys` |
| `Could not lock — DatabaseChangeLogLock` | another worker is mid-migration on the same DB | wait 1 min, or have the worker holding the lock crash/exit cleanly |
