-- Bootstrap the isolated dev environment on the production Postgres instance.
-- Runs against the postgres superuser DB (`postgres` or `zeromail`).
-- Re-runnable: rotates the password on every run; safe to invoke after the first time.
--
-- Usage on the VPS (one-time, with the dev password loaded into the shell env):
--   docker exec -i \
--     -e PGPASSWORD=<superuser-password> \
--     zeromail-postgres psql -U zeromail -d postgres -v ON_ERROR_STOP=1 \
--     -v dev_password="<generated-dev-password>" \
--     < ops/postgres/init/10-create-dev-db.sql
--
-- Isolation guarantees:
--   - Role `zeromail_dev` cannot read or write database `zeromail` (prod).
--   - Role `zeromail` (prod) is *not* granted to `zeromail_dev`, and vice versa.
--   - Liquibase locks are per-database, so the dev worker cannot stall prod migrations.

\set ON_ERROR_STOP on

-- Idempotent role create + password rotate. psql substitutes :'dev_password' outside
-- DO blocks, so we use \gexec instead of EXECUTE inside PL/pgSQL.
SELECT format(
    CASE WHEN EXISTS (SELECT FROM pg_roles WHERE rolname = 'zeromail_dev')
         THEN 'ALTER ROLE zeromail_dev WITH PASSWORD %L'
         ELSE 'CREATE ROLE zeromail_dev LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT'
    END,
    :'dev_password'
) AS sql_to_run
\gexec

SELECT 'CREATE DATABASE zeromail_dev OWNER zeromail_dev ENCODING ''UTF8'' TEMPLATE template0' AS sql_to_run
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'zeromail_dev')
\gexec

REVOKE ALL ON DATABASE zeromail_dev FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE zeromail_dev TO zeromail_dev;

REVOKE ALL ON DATABASE zeromail FROM zeromail_dev;

\c zeromail_dev

ALTER SCHEMA public OWNER TO zeromail_dev;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO zeromail_dev;

SELECT
    rolname,
    rolcanlogin,
    rolsuper,
    rolcreatedb,
    rolcreaterole
FROM pg_roles
WHERE rolname IN ('zeromail', 'zeromail_dev')
ORDER BY rolname;

SELECT datname, datdba::regrole AS owner, encoding, datcollate
FROM pg_database
WHERE datname IN ('zeromail', 'zeromail_dev')
ORDER BY datname;
