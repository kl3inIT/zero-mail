#!/usr/bin/env bash
# MED-3: wait for the worker queue to drain before invariant verification.
# k6 Pub/Sub pushes return after api insertion; worker processing is async.
# The queue table is pubsub_delivery.
set -euo pipefail

DB_HOST="${LOADTEST_DB_HOST:-localhost}"
DB_PORT="${LOADTEST_DB_PORT:-15433}"
DB_NAME="${LOADTEST_DB_NAME:-zeromail}"
DB_USER="${LOADTEST_DB_USER:-zeromail}"
DB_PASSWORD="${LOADTEST_DB_PASSWORD:-zeromail}"
TIMEOUT_SECONDS="${LOADTEST_DRAIN_TIMEOUT:-120}"
POLL_INTERVAL_SECONDS="${LOADTEST_DRAIN_POLL_INTERVAL:-3}"

export PGPASSWORD="$DB_PASSWORD"

start_epoch=$(date +%s)
while true; do
    pending_count=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc \
        "SELECT COUNT(*) FROM pubsub_delivery WHERE status = 'PENDING' AND (locked_until IS NULL OR locked_until < NOW())")

    if [[ "$pending_count" -eq 0 ]]; then
        echo "event=worker_drain_complete pending=0 elapsed_s=$(( $(date +%s) - start_epoch ))"
        exit 0
    fi

    now_epoch=$(date +%s)
    if (( now_epoch - start_epoch >= TIMEOUT_SECONDS )); then
        echo "event=worker_drain_timeout pending=$pending_count elapsed_s=$(( now_epoch - start_epoch ))" >&2
        exit 1
    fi

    echo "event=worker_drain_polling pending=$pending_count"
    sleep "$POLL_INTERVAL_SECONDS"
done
