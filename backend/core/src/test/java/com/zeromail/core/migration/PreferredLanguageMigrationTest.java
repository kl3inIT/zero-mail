package com.zeromail.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * [BLOCKING] Wave 0 schema-push proof for Phase 1.1: boots Spring against the shared Postgres 17
 * Testcontainer (so Liquibase auto-applies every changeset including 006-), then asserts via {@code
 * information_schema} that the new {@code users.preferred_language} column has the exact shape
 * locked by CONTEXT.md decision D-B2:
 *
 * <ul>
 *   <li>{@code character varying} type with maximum length 2
 *   <li>{@code NOT NULL}
 *   <li>default value {@code 'vi'}
 *   <li>CHECK constraint enforcing the {@code ('vi','en')} allow-list
 * </ul>
 *
 * Phase 1.1 plans 02-08 are gated on this test passing.
 */
class PreferredLanguageMigrationTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void preferred_language_column_exists_with_locked_shape() {
        Map<String, Object> col =
                jdbc.queryForMap(
                        "SELECT column_name, data_type, character_maximum_length, is_nullable, column_default "
                                + "FROM information_schema.columns "
                                + "WHERE table_name = 'users' AND column_name = 'preferred_language'");

        assertThat(col).containsEntry("data_type", "character varying");
        assertThat(col.get("character_maximum_length")).isEqualTo(2);
        assertThat(col).containsEntry("is_nullable", "NO");
        assertThat((String) col.get("column_default")).contains("'vi'");
    }

    @Test
    void preferred_language_check_constraint_is_present() {
        // Pull the CHECK clause text from pg_catalog and assert it references both allow-listed
        // locales. Using pg_get_constraintdef avoids brittle whitespace assertions on
        // information_schema.check_constraints.
        String def =
                jdbc.queryForObject(
                        "SELECT pg_get_constraintdef(c.oid) "
                                + "FROM pg_constraint c "
                                + "JOIN pg_class t ON t.oid = c.conrelid "
                                + "WHERE t.relname = 'users' AND c.conname = 'users_preferred_language_check'",
                        String.class);
        assertThat(def).isNotNull();
        assertThat(def).contains("preferred_language");
        assertThat(def).contains("'vi'");
        assertThat(def).contains("'en'");
    }
}
