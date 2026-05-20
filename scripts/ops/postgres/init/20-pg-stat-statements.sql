-- Enable pg_stat_statements on the shared Postgres instance (prod + dev DBs).
-- Runs as a docker-entrypoint-initdb.d script on fresh volumes. For existing
-- volumes, run manually:
--   docker exec -e PGPASSWORD=zeromail zeromail-postgres \
--     psql -U zeromail -d zeromail -f /docker-entrypoint-initdb.d/20-pg-stat-statements.sql
--   docker exec -e PGPASSWORD=zeromail zeromail-postgres \
--     psql -U zeromail -d zeromail_dev -f /docker-entrypoint-initdb.d/20-pg-stat-statements.sql
--
-- The extension itself is loaded via `shared_preload_libraries` in
-- docker-compose.yml; this script only creates the per-database catalog view.

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
