package com.zeromail.core.gmail.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.gmail.support.OldSingleAccountFixture;
import com.zeromail.core.support.PostgresContainerTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class Migration119Test extends PostgresContainerTest {

    private static final String CHANGESET_RESOURCE =
            "db/changelog/changes/119-gmail-connections-multi-mailbox.yaml";

    @Autowired DataSource dataSource;

    @Test
    void old_single_account_row_is_backfilled_primary_without_touching_ciphertext()
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String schemaName = "migration_119_" + UUID.randomUUID().toString().replace('-', '_');
            try (Statement statement = connection.createStatement()) {
                statement.execute("create schema " + schemaName);
                statement.execute("set search_path to " + schemaName);
                statement.execute(
                        "create table tenants(id uuid primary key, display_name varchar(255))");
                statement.execute(
                        """
                        create table gmail_connections(
                          id uuid primary key,
                          tenant_id uuid not null,
                          google_email varchar(320) not null,
                          status varchar(32) not null,
                          refresh_token_encrypted bytea,
                          scopes_granted text,
                          connected_at timestamptz,
                          disconnected_at timestamptz
                        )
                        """);
                statement.execute(
                        "alter table gmail_connections add constraint uq_gmail_connections_tenant_id unique (tenant_id)");
            }

            JdbcTemplate scratchJdbcTemplate =
                    new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            OldSingleAccountFixture.SeededSingleAccount seededSingleAccount =
                    OldSingleAccountFixture.seed(scratchJdbcTemplate);

            try (Statement statement = connection.createStatement()) {
                statement.execute(forwardSqlFromChangeset());
            }

            Boolean primary =
                    scratchJdbcTemplate.queryForObject(
                            "select is_primary from gmail_connections where id = ?",
                            Boolean.class,
                            seededSingleAccount.gmailConnectionId());
            byte[] encryptedRefreshTokenAfterMigration =
                    scratchJdbcTemplate.queryForObject(
                            "select refresh_token_encrypted from gmail_connections where id = ?",
                            byte[].class,
                            seededSingleAccount.gmailConnectionId());
            String scopesGranted =
                    scratchJdbcTemplate.queryForObject(
                            "select scopes_granted from gmail_connections where id = ?",
                            String.class,
                            seededSingleAccount.gmailConnectionId());
            String status =
                    scratchJdbcTemplate.queryForObject(
                            "select status from gmail_connections where id = ?",
                            String.class,
                            seededSingleAccount.gmailConnectionId());

            assertThat(primary).isTrue();
            assertThat(encryptedRefreshTokenAfterMigration)
                    .containsExactly(seededSingleAccount.encryptedRefreshToken());
            assertThat(scopesGranted).isEqualTo(OldSingleAccountFixture.SCOPES_GRANTED);
            assertThat(status).isEqualTo("CONNECTED");
        }
    }

    @Test
    void changeset_119_never_names_token_scope_or_watch_columns_in_forward_sql() throws Exception {
        String forwardSql = forwardSqlFromChangeset().toLowerCase(Locale.ROOT);

        assertThat(forwardSql)
                .contains("is_primary")
                .contains("uq_gmail_conn_active_email")
                .contains("uq_gmail_conn_primary")
                .doesNotContain("refresh_token_encrypted")
                .doesNotContain("scopes_granted")
                .doesNotContain("watch_");
    }

    private static String forwardSqlFromChangeset() throws IOException, URISyntaxException {
        var resource = Migration119Test.class.getClassLoader().getResource(CHANGESET_RESOURCE);
        assertThat(resource)
                .as("changeset 119 must be present on the test runtime classpath")
                .isNotNull();

        List<String> lines = Files.readAllLines(Path.of(resource.toURI()));
        StringBuilder forwardSql = new StringBuilder();
        boolean insideChanges = false;
        boolean insideForwardSql = false;
        int sqlIndent = -1;
        for (String line : lines) {
            if (line.trim().equals("changes:")) {
                insideChanges = true;
                continue;
            }
            if (insideForwardSql && line.trim().equals("rollback:")) {
                break;
            }
            if (insideChanges && !insideForwardSql && line.trim().equals("sql: |")) {
                insideForwardSql = true;
                sqlIndent = line.indexOf("sql: |");
                continue;
            }
            if (insideForwardSql) {
                if (line.isBlank()) {
                    forwardSql.append('\n');
                } else if (line.length() > sqlIndent) {
                    forwardSql
                            .append(line.substring(Math.min(line.length(), sqlIndent)))
                            .append('\n');
                }
            }
        }
        return forwardSql.toString();
    }
}
