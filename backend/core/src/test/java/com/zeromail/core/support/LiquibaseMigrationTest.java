package com.zeromail.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * [BLOCKING] schema-push proof: boots Spring against a real Postgres 17 Testcontainer,
 * Liquibase applies all changesets, then asserts every required table is present.
 */
class LiquibaseMigrationTest extends PostgresContainerTest {

    @Autowired
    DataSource dataSource;

    @Test
    void all_tables_exist() throws Exception {
        Set<String> seen = new HashSet<>();
        try (var c = dataSource.getConnection();
             var md = c.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (md.next()) {
                seen.add(md.getString("TABLE_NAME"));
            }
        }
        assertThat(seen).contains(
                "tenants",
                "users",
                "gmail_connections",
                "onboarding_selections",
                "event_publication");
    }
}
