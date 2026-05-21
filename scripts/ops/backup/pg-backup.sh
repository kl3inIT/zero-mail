#!/usr/bin/env bash
# Postgres backup for Zero Mail.
#
# Daily cron entry: `5 3 * * *  /apps/zero-mail/ops/backup/pg-backup.sh >> /var/log/zeromail-backup.log 2>&1`
#
# What it does:
#   1. pg_dump (custom format, compressed) of both zeromail + zeromail_dev.
#   2. Encrypts each dump with GPG symmetric (passphrase from /etc/zeromail/backup.passphrase).
#   3. Writes to /var/backups/zeromail/<UTC-date>/.
#   4. Retains 7 daily + 4 weekly + 6 monthly snapshots.
#   5. Optional off-host upload if RCLONE_REMOTE env is set
#      (e.g. RCLONE_REMOTE=r2:zeromail-backups → uses preconfigured rclone target).
#
# Recovery:
#   gpg --decrypt zeromail-2026-05-20.dump.gpg > restore.dump
#   docker exec -i zeromail-postgres pg_restore -U zeromail -d zeromail_restore -c -j 4 < restore.dump

set -euo pipefail

BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/zeromail}"
PG_CONTAINER="${PG_CONTAINER:-zeromail-postgres}"
PG_SUPERUSER="${PG_SUPERUSER:-zeromail}"
PG_SUPERUSER_PW="${PG_SUPERUSER_PW:-zeromail}"
PASSPHRASE_FILE="${PASSPHRASE_FILE:-/etc/zeromail/backup.passphrase}"
RCLONE_REMOTE="${RCLONE_REMOTE:-}"
RCLONE_PATH="${RCLONE_PATH:-zeromail-backups}"

DATE_UTC="$(date -u +%Y-%m-%d)"
TS_UTC="$(date -u +%Y%m%dT%H%M%SZ)"
DAILY_DIR="${BACKUP_ROOT}/daily/${DATE_UTC}"
WEEKLY_DIR="${BACKUP_ROOT}/weekly"
MONTHLY_DIR="${BACKUP_ROOT}/monthly"

mkdir -p "$DAILY_DIR" "$WEEKLY_DIR" "$MONTHLY_DIR"

if [[ ! -f "$PASSPHRASE_FILE" ]]; then
    echo "[pg-backup ${TS_UTC}] ERROR: missing passphrase file $PASSPHRASE_FILE" >&2
    exit 1
fi
chmod 400 "$PASSPHRASE_FILE" 2>/dev/null || true

dump_one_db() {
    local db="$1"
    local out="${DAILY_DIR}/${db}-${TS_UTC}.dump.gpg"

    echo "[pg-backup ${TS_UTC}] dumping ${db} → ${out}"
    docker exec -e PGPASSWORD="$PG_SUPERUSER_PW" "$PG_CONTAINER" \
        pg_dump -U "$PG_SUPERUSER" -d "$db" -F c -Z 9 \
        | gpg --batch --yes --symmetric --cipher-algo AES256 \
              --passphrase-file "$PASSPHRASE_FILE" -o "$out"

    if [[ ! -s "$out" ]]; then
        echo "[pg-backup ${TS_UTC}] ERROR: dump for ${db} is empty" >&2
        exit 1
    fi
    echo "[pg-backup ${TS_UTC}] ${db} ok ($(du -h "$out" | cut -f1))"
}

dump_one_db zeromail
dump_one_db zeromail_dev

# Weekly snapshot every Sunday.
if [[ "$(date -u +%u)" == "7" ]]; then
    cp -a "$DAILY_DIR"/. "$WEEKLY_DIR/${DATE_UTC}/" 2>/dev/null || {
        mkdir -p "$WEEKLY_DIR/${DATE_UTC}"
        cp -a "$DAILY_DIR"/. "$WEEKLY_DIR/${DATE_UTC}/"
    }
fi

# Monthly snapshot on day-1.
if [[ "$(date -u +%d)" == "01" ]]; then
    mkdir -p "$MONTHLY_DIR/${DATE_UTC}"
    cp -a "$DAILY_DIR"/. "$MONTHLY_DIR/${DATE_UTC}/"
fi

# Retention.
find "$BACKUP_ROOT/daily"   -mindepth 1 -maxdepth 1 -type d -mtime +7   -exec rm -rf {} +
find "$BACKUP_ROOT/weekly"  -mindepth 1 -maxdepth 1 -type d -mtime +28  -exec rm -rf {} +
find "$BACKUP_ROOT/monthly" -mindepth 1 -maxdepth 1 -type d -mtime +180 -exec rm -rf {} +

# Optional off-host upload via rclone.
if [[ -n "$RCLONE_REMOTE" ]] && command -v rclone >/dev/null 2>&1; then
    echo "[pg-backup ${TS_UTC}] uploading to ${RCLONE_REMOTE}/${RCLONE_PATH}"
    rclone copy "$DAILY_DIR" "${RCLONE_REMOTE}/${RCLONE_PATH}/daily/${DATE_UTC}/" --quiet
fi

echo "[pg-backup ${TS_UTC}] done"
